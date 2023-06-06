package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.TrivyService
// import org.ods.services.BitbucketService
// import org.ods.services.NexusService
import org.ods.util.ILogger

@TypeChecked
class ScanWithTrivyStage extends Stage {

    static final String STAGE_NAME = 'Trivy Scan'
    static final String BITBUCKET_AQUA_REPORT_KEY = "org.opendevstack.trivy"
    private final TrivyService trivy
    // private final BitbucketService bitbucket
    // private final NexusService nexus
    // private final ScanWithTrivyOptions options

    @SuppressWarnings('ParameterCount')
    @TypeChecked(TypeCheckingMode.SKIP)
    // ScanWithTrivyStage(def script, IContext context, TrivyService trivy, BitbucketService bitbucket,
    //                   NexusService nexusService, ILogger logger) {
    ScanWithTrivyStage(def script, IContext context, TrivyService trivy, ILogger logger) {
        super(script, context, logger)
//        this.options = new ScanWithTrivyOptions(config)
        this.trivy = trivy
        // this.bitbucket = bitbucket
        // this.nexus = nexusService
    }

    protected run() {
        String errorMessages = ''

        // Name in Nexus of the repository where to store the reports.
        //To set proper param
        // String nexusRepository = configurationAquaCluster['nexusRepository']
        // if (!nexusRepository) {
        //     logger.info "Please provide the name of the repository in Nexus to store the reports!"
        //     errorMessages += "<li>Provide the name of the repository in Nexus to use with Aqua</li>"
        // }

        String jsonFile = "trivy-sbom.json"
        String format = "cyclonedx"
        String scanners = "vuln,config,secret,license"
        String vulType = "os,library"
        logger.info "1º check"
        int returnCode = scanViaCli(scanners, vulType, format, jsonFile)
        logger.info "2º check"
        if (![TrivyService.TRIVY_SUCCESS, TrivyService.TRIVY_POLICIES_ERROR].contains(returnCode)) {
            errorMessages += "<li>Error executing Trivy CLI</li>"
        }
        logger.info "3º check"
        // If report exists
        // if ([TrivyService.TRIVY_SUCCESS, TrivyService.TRIVY_POLICIES_ERROR].contains(returnCode)) {
        //     try {
        //         def resultInfo = steps.readJSON(text: steps.readFile(file: jsonFile) as String) as Map
        //         Map vulnerabilities = resultInfo.vulnerability_summary as Map
        //         // returnCode is 0 --> Success or 4 --> Error policies
        //         // with sum of errorCodes > 0 BitbucketCodeInsight is FAIL
        //         def errorCodes = [returnCode,
        //                           vulnerabilities.critical ?: 0,
        //                           vulnerabilities.malware ?: 0]

        //         URI reportUriNexus = archiveReportInNexus(reportFile, nexusRepository)
        //         createBitbucketCodeInsightReport(url, nexusRepository ? reportUriNexus.toString() : null,
        //             registry, imageRef, errorCodes.sum() as int, errorMessages)
        //         archiveReportInJenkins(!context.triggeredByOrchestrationPipeline, reportFile)
        //     } catch (err) {
        //         logger.warn("Error archiving the Aqua reports due to: ${err}")
        //         errorMessages += "<li>Error archiving Aqua reports</li>"
        //     }
        // } else {
        //     createBitbucketCodeInsightReport(errorMessages)
        // }

        // notifyAquaProblem(alertEmails, errorMessages)
        return
    }

    @SuppressWarnings('ParameterCount')
    private int scanViaCli(String scanners,String vulType, String format, String jsonFile) {
        int returnCode = trivy.scanViaCli(scanners, vulType, format, jsonFile)
        //Check return code for Trivy cli and adjust bellow
        logger.info "1.1º check"
        switch (returnCode) {
            case TrivyService.TRIVY_SUCCESS:
                logger.info "Finished scan via Trivy CLI successfully!"
                break
            case TrivyService.TRIVY_OPERATIONAL_ERROR:
                logger.info "An error occurred in processing the scan request " +
                    "(e.g. invalid command line options, image not pulled, operational error)."
                break
            case TrivyService.TRIVY_POLICIES_ERROR:
                logger.info "The scan failed at least one of the Policies specified."
                break
            default:
                logger.info "An unknown return code was returned: ${returnCode}"
        }
        logger.info "1.2º check"
        logger.infoClocked("","Trivy scan (via CLI)")
        return returnCode
    }

    // @SuppressWarnings('ParameterCount')
    // private createBitbucketCodeInsightReport(String aquaUrl, String nexusUrlReport,
    //                                          String registry, String imageRef, int returnCode, String messages) {
    //     String aquaScanUrl = aquaUrl + "/#/images/" + registry + "/" + imageRef.replace("/", "%2F") + "/vulns"
    //     String title = "Aqua Security"
    //     String details = "Please visit the following links to review the Aqua Security scan report:"

    //     String result = returnCode == 0 ? "PASS" : "FAIL"

    //     def data = [
    //         key: BITBUCKET_AQUA_REPORT_KEY,
    //         title: title,
    //         link: nexusUrlReport,
    //         otherLinks: [
    //             [
    //                 title: "Report",
    //                 text: "Result in Aqua",
    //                 link: aquaScanUrl
    //             ]
    //         ],
    //         details: details,
    //         result: result,
    //     ]
    //     if (nexusUrlReport) {
    //         ((List)data.otherLinks).add([
    //             title: "Report",
    //             text: "Result in Nexus",
    //             link: nexusUrlReport,
    //         ])
    //     }
    //     if (messages) {
    //         data.put("messages",[
    //             [ title: "Messages", value: prepareMessageToBitbucket(messages), ]
    //         ])
    //     }

    //     bitbucket.createCodeInsightReport(data, context.repoName, context.gitCommit)
    // }

    // private createBitbucketCodeInsightReport(String messages) {
    //     String title = "Aqua Security"
    //     String details = "There was some problems with Aqua:"

    //     String result = "FAIL"

    //     def data = [
    //         key: BITBUCKET_AQUA_REPORT_KEY,
    //         title: title,
    //         messages: [
    //             [
    //                 title: "Messages",
    //                 value: prepareMessageToBitbucket(messages)
    //             ]
    //         ],
    //         details: details,
    //         result: result,
    //     ]

    //     bitbucket.createCodeInsightReport(data, context.repoName, context.gitCommit)
    // }

    // private String prepareMessageToBitbucket(String message = "") {
    //     return message?.replaceAll("<li>", "")?.replaceAll("</li>", ". ")
    // }

    // @SuppressWarnings('ReturnNullFromCatchBlock')
    // private URI archiveReportInNexus(String reportFile, nexusRepository) {
    //     try {
    //         URI report = nexus.storeArtifact(
    //             "${nexusRepository}",
    //             "${context.projectId}/${this.options.resourceName}/" +
    //                 "${new Date().format('yyyy-MM-dd')}-${context.buildNumber}/aqua",
    //             "report.html",
    //             (steps.readFile(file: reportFile) as String).bytes, "text/html")

    //         logger.info "Report stored in: ${report}"

    //         return report
    //     } catch (err) {
    //         logger.warn("Error archiving the Aqua reports in Nexus due to: ${err}")
    //         return null
    //     }
    // }

    // private archiveReportInJenkins(boolean archive, String reportFile) {
    //     String targetReport = "SCSR-${context.projectId}-${context.componentId}-${reportFile}"
    //     steps.sh(
    //         label: 'Create artifacts dir',
    //         script: 'mkdir -p artifacts'
    //     )
    //     steps.sh(
    //         label: 'Rename report to SCSR',
    //         script: "mv ${reportFile} artifacts/${targetReport}"
    //     )
    //     if (archive) {
    //         steps.archiveArtifacts(artifacts: 'artifacts/SCSR*')
    //     }
    //     String aquaScanStashPath = "scsr-report-${context.componentId}-${context.buildNumber}"
    //     context.addArtifactURI('aquaScanStashPath', aquaScanStashPath)

    //     steps.stash(
    //         name: "${aquaScanStashPath}",
    //         includes: 'artifacts/SCSR*',
    //         allowEmpty: true
    //     )
    //     context.addArtifactURI('SCSR', targetReport)
    // }

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
