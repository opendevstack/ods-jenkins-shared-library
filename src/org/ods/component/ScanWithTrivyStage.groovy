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
        if (!config.format) {
            config.format = 'cyclonedx'
        }
        if (!config.scanners) {
            config.scanners = 'vuln,misconfig,secret,license'
        }
        if (!config.pkgType) {
            config.pkgType = 'os,library'
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
        if (!config.reportFile) {
            config.reportFile = 'trivy-sbom.json'
        }
        this.options = new ScanWithTrivyOptions(config)
        this.trivy = trivy
        this.bitbucket = bitbucket
        this.nexus = nexusService
        this.openShift = openShift
    }

    protected run() {
        String flags = options.additionalFlags.collect { " $it" }.join("")
        int returnCode = trivy.scan(
            options.resourceName,
            options.scanners,
            options.pkgType,
            options.format,
            flags,
            options.reportFile,
            options.nexusDataBaseRepository,
            openShift.getApplicationDomain()
        )
        try {
            URI reportUriNexus = archiveReportInNexus()
            createBitbucketCodeInsightReport(reportUriNexus?.toString(), returnCode)
            archiveReportInJenkins(!context.triggeredByOrchestrationPipeline, options.reportFile)
        } catch (Exception err) {
            logger.warn("Error archiving the Trivy reports due to: ${err}")
            createBitbucketCodeInsightErrorReport("<li>Error archiving Trivy report: ${err.message}</li>")
        }
    }

    private void createBitbucketCodeInsightReport(String nexusUrlReport, int returnCode) {
        def data = [
            key: BITBUCKET_TRIVY_REPORT_KEY,
            title: "Trivy Security",
            link: nexusUrlReport,
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: nexusUrlReport
                ]
            ],
            details: "Please visit the following link to review the Trivy Security scan report:",
            result: returnCode == TrivyService.TRIVY_SUCCESS ? "PASS" : "FAIL",
        ]
        bitbucket.createCodeInsightReport(data, context.repoName, context.gitCommit)
    }

    private void createBitbucketCodeInsightErrorReport(String errorMessages) {
        def data = [
            key: BITBUCKET_TRIVY_REPORT_KEY,
            title: "Trivy Security",
            messages: [
                [
                    title: "Messages",
                    value: prepareMessageToBitbucket(errorMessages)
                ]
            ],
            details: "There was some problems with Trivy:",
            result: "FAIL",
        ]
        bitbucket.createCodeInsightReport(data, context.repoName, context.gitCommit)
    }

    private static String prepareMessageToBitbucket(String message = "") {
        return message?.replaceAll("<li>", "")?.replaceAll("</li>", ". ")
    }

    private URI archiveReportInNexus() {
        URI report = nexus.storeArtifact(
            options.nexusReportRepository,
            "${context.projectId}/${options.resourceName}/" +
                "${new Date().format('yyyy-MM-dd')}-${context.buildNumber}/trivy",
            options.reportFile,
            (steps.readFile(file: options.reportFile) as String).bytes, "json")
        logger.info "Report stored in: ${report}"
        return report
    }

    private void archiveReportInJenkins(boolean archive, String reportFile) {
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
