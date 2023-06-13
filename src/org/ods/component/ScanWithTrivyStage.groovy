package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.TrivyService
import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger

@TypeChecked
class ScanWithTrivyStage extends Stage {

    static final String STAGE_NAME = 'Trivy Scan'
    static final String BITBUCKET_TRIVY_REPORT_KEY = "org.opendevstack.trivy"
    private final TrivyService trivy
    private final BitbucketService bitbucket
    private final NexusService nexus
    private final OpenShiftService openShift
    private final ScanWithTrivyOptions options

    @SuppressWarnings('ParameterCount')
    @TypeChecked(TypeCheckingMode.SKIP)
    ScanWithTrivyStage(def script, IContext context, Map config, TrivyService trivy, BitbucketService bitbucket,
                      NexusService nexusService, OpenShiftService openShift, ILogger logger) {
        super(script, context, logger)
        if (!config.resourceName) {
            config.resourceName = context.componentId
        }
        // check format
        if (!config.format) {
            config.format = 'cyclonedx'
        }
        if (!config.scanners) {
            config.scanners = 'vuln,config,secret,license'
        }
        if (!config.vulType) {
            config.vulType = 'os,library'
        }
        if (!config.additionalFlags) {
            config.additionalFlags = []
        }
        if (!config.nexusReportRepository) {
            config.nexusReportRepository = 'leva-documentation'
        }
        if (!config.nexusDataBaseRepository) {
            config.nexusDataBaseRepository = 'docker-group-ods'
        }
        this.options = new ScanWithTrivyOptions(config)
        this.trivy = trivy
        this.bitbucket = bitbucket
        this.nexus = nexusService
        this.openShift = openShift
    }

    protected run() {
        String errorMessages = ''
        String reportFile = "trivy-sbom.json"
        int returnCode = scanViaCli(options.scanners, options.vulType, options.format,
            options.additionalFlags, reportFile, options.nexusDataBaseRepository)
        if ([TrivyService.TRIVY_SUCCESS].contains(returnCode)) {
            try {
                URI reportUriNexus = archiveReportInNexus(reportFile, options.nexusReportRepository)
                createBitbucketCodeInsightReport(options.nexusReportRepository ? reportUriNexus.toString() : null,
                    returnCode, errorMessages)
                archiveReportInJenkins(!context.triggeredByOrchestrationPipeline, reportFile)
            } catch (err) {
                logger.warn("Error archiving the Trivy reports due to: ${err}")
            }
        } else {
            errorMessages += "<li>Error executing Trivy CLI</li>"
            createBitbucketCodeInsightReport(errorMessages)
        }
        return
    }

    @SuppressWarnings('ParameterCount')
    private int scanViaCli(String scanners, String vulType, String format,
        List<String> additionalFlags, String reportFile, String nexusDataBaseRepository) {
        logger.startClocked(options.resourceName)
        String flags = ""
        additionalFlags.each { flag ->
            flags += " " + flag
        }
        int returnCode = trivy.scanViaCli(scanners, vulType, format, flags, reportFile,
        openShift.getApplicationDomain(context.cdProject), nexusDataBaseRepository)
        switch (returnCode) {
            case TrivyService.TRIVY_SUCCESS:
                logger.info "Finished scan via Trivy CLI successfully!"
                break
            case TrivyService.TRIVY_OPERATIONAL_ERROR:
                logger.info "An error occurred in processing the scan request " +
                    "(e.g. invalid command line options, image not pulled, operational error)."
                break
            default:
                logger.info "An unknown return code was returned: ${returnCode}"
        }
        logger.infoClocked(options.resourceName,"Trivy scan (via CLI)")
        return returnCode
    }

    @SuppressWarnings('ParameterCount')
    private createBitbucketCodeInsightReport(String nexusUrlReport, int returnCode, String messages) {
        String title = "Trivy Security"
        String details = "Please visit the following link to review the Trivy Security scan report:"
        String result = returnCode == 0 ? "PASS" : "FAIL"
        def data = [
            key: BITBUCKET_TRIVY_REPORT_KEY,
            title: title,
            link: nexusUrlReport,
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: nexusUrlReport
                ]
            ],
            details: details,
            result: result,
        ]

        if (messages) {
            data.put("messages",[
                [ title: "Messages", value: prepareMessageToBitbucket(messages), ]
            ])
        }

        bitbucket.createCodeInsightReport(data, context.repoName, context.gitCommit)
    }

    private createBitbucketCodeInsightReport(String messages) {
        String title = "Trivy Security"
        String details = "There was some problems with Trivy:"

        String result = "FAIL"

        def data = [
            key: BITBUCKET_TRIVY_REPORT_KEY,
            title: title,
            messages: [
                [
                    title: "Messages",
                    value: prepareMessageToBitbucket(messages)
                ]
            ],
            details: details,
            result: result,
        ]

        bitbucket.createCodeInsightReport(data, context.repoName, context.gitCommit)
    }

    private String prepareMessageToBitbucket(String message = "") {
        return message?.replaceAll("<li>", "")?.replaceAll("</li>", ". ")
    }

    @SuppressWarnings('ReturnNullFromCatchBlock')
    private URI archiveReportInNexus(String reportFile, nexusReportRepository) {
        try {
            URI report = nexus.storeArtifact(
                "${nexusReportRepository}",
                "${context.projectId}/${this.options.resourceName}/" +
                    "${new Date().format('yyyy-MM-dd')}-${context.buildNumber}/trivy",
                "${reportFile}",
                (steps.readFile(file: reportFile) as String).bytes, "json")

            logger.info "Report stored in: ${report}"

            return report
        } catch (err) {
            logger.warn("Error archiving the Trivy reports in Nexus due to: ${err}")
            return null
        }
    }

    private archiveReportInJenkins(boolean archive, String reportFile) {
        String targetReport = "SCSR-${context.projectId}-${context.componentId}-${reportFile}"
        steps.sh(
            label: 'Create artifacts dir',
            script: 'mkdir -p artifacts'
        )
        steps.sh(
            label: 'Rename report to SCSR',
            script: "mv ${reportFile} artifacts/${targetReport}"
        )
        if (archive) {
            steps.archiveArtifacts(artifacts: 'artifacts/SCSR*')
        }
        String trivyScanStashPath = "scsr-report-${context.componentId}-${context.buildNumber}"
        context.addArtifactURI('trivyScanStashPath', trivyScanStashPath)

        steps.stash(
            name: "${trivyScanStashPath}",
            includes: 'artifacts/SCSR*',
            allowEmpty: true
        )
        context.addArtifactURI('SCSR', targetReport)
    }

}
