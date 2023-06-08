package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.TrivyService
import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.util.ILogger

@TypeChecked
class ScanWithTrivyStage extends Stage {

    static final String STAGE_NAME = 'Trivy Scan'
    static final String BITBUCKET_TRIVY_REPORT_KEY = "org.opendevstack.trivy"
    private final TrivyService trivy
    private final BitbucketService bitbucket
    private final NexusService nexus
    private final ScanWithTrivyOptions options

    @SuppressWarnings('ParameterCount')
    @TypeChecked(TypeCheckingMode.SKIP)
    ScanWithTrivyStage(def script, IContext context, Map config, TrivyService trivy, BitbucketService bitbucket,
                      NexusService nexusService, ILogger logger) {
        super(script, context, logger)
        this.options = new ScanWithTrivyOptions(config)
        this.trivy = trivy
        this.bitbucket = bitbucket
        this.nexus = nexusService
    }

    protected run() {
        String errorMessages = ''
        String reportFile = "trivy-sbom.json"
        // remove checks
        logger.info "format: ${options.format}, scanners: ${options.scanners}, vulType: ${options.vulType}, resourceName: ${options.resourceName}, nexusRepository: ${options.nexusRepository}"
        logger.info "1º check"
        int returnCode = scanViaCli(options.scanners, options.vulType, options.format, reportFile)
        // remove check
        logger.info "2º check"
        if (![TrivyService.TRIVY_SUCCESS].contains(returnCode)) {
            errorMessages += "<li>Error executing Trivy CLI</li>"
        }
        // remove check
        logger.info "3º check"

        //If report exists
        if ([TrivyService.TRIVY_SUCCESS].contains(returnCode)) {
            try {
        //         def resultInfo = steps.readJSON(text: steps.readFile(file: jsonFile) as String) as Map
        //         Map vulnerabilities = resultInfo.vulnerability_summary as Map
        //         // returnCode is 0 --> Success or 4 --> Error policies
        //         // with sum of errorCodes > 0 BitbucketCodeInsight is FAIL
        //         def errorCodes = [returnCode,
        //                           vulnerabilities.critical ?: 0,
        //                           vulnerabilities.malware ?: 0]

                URI reportUriNexus = archiveReportInNexus(reportFile, options.nexusRepository)
                createBitbucketCodeInsightReport(options.nexusRepository ? reportUriNexus.toString() : null,
                    returnCode, errorMessages)
                archiveReportInJenkins(!context.triggeredByOrchestrationPipeline, reportFile)
            } catch (err) {
                logger.warn("Error archiving the Trivy reports due to: ${err}")
                errorMessages += "<li>Error archiving Trivy reports</li>"
            }
        } else {
            createBitbucketCodeInsightReport(errorMessages)
        }

        // notifyAquaProblem(alertEmails, errorMessages)
        return
    }

    @SuppressWarnings('ParameterCount')
    private int scanViaCli(String scanners, String vulType, String format, String reportFile) {
        logger.startClocked(options.resourceName)
        // remove check
        logger.info "1.1º check"
        int returnCode = trivy.scanViaCli(scanners, vulType, format, reportFile)
        //Check return code for Trivy cli and adjust bellow
        // remove check
        logger.info "1.2º check"
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
        // remove check
        logger.info "1.3º check"
        logger.infoClocked(options.resourceName,"Trivy scan (via CLI)")
        // remove check
        logger.info "1.4º check"
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
    private URI archiveReportInNexus(String reportFile, nexusRepository) {
        try {
            URI report = nexus.storeArtifact(
                "${nexusRepository}",
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
        //why arhive all artifacts and not only the specific file ?
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

    // private void notifyAquaProblem(String recipients = '', String message = '') {
    //     String subject = "Build $context.componentId on project $context.projectId had some problems with Aqua!"
    //     String body = "<p>$subject</p> " +
    //         "<p>URL : <a href=\"$context.buildUrl\">$context.buildUrl</a></p> <ul>$message</ul>"

    //     if (message) {
    //         steps.emailext(
    //             body: body, mimeType: 'text/html',
    //             replyTo: '$script.DEFAULT_REPLYTO', subject: subject,
    //             to: recipients
    //         )
    //     }
    // }

}
