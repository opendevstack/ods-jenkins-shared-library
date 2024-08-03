package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.apache.commons.lang3.StringUtils
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.services.AquaService
import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.ILogger

@TypeChecked
class ScanWithAquaStage extends Stage {

    static final String STAGE_NAME = 'Aqua Security Scan'
    static final String AQUA_CONFIG_MAP_NAME = "aqua"
    static final String BITBUCKET_AQUA_REPORT_KEY = "org.opendevstack.aquasec"
    static final Integer AQUA_DEFAULT_TIMEOUT = 300
    private final AquaService aqua
    private final BitbucketService bitbucket
    private final OpenShiftService openShift
    private final NexusService nexus
    private final ScanWithAquaOptions options
    private Map configurationAquaCluster
    private Map configurationAquaProject

    @SuppressWarnings('ParameterCount')
    @TypeChecked(TypeCheckingMode.SKIP)
    ScanWithAquaStage(def script, IContext context, Map config, AquaService aqua, BitbucketService bitbucket,
                      OpenShiftService openShift, NexusService nexusService, ILogger logger,
                      Map configurationAquaCluster = [:], Map configurationAquaProject = [:]) {
        super(script, context, logger)
        if (!config.resourceName) {
            config.resourceName = context.componentId
        }
        if (!config.scanTimeoutSeconds) {
            config.scanTimeoutSeconds = AQUA_DEFAULT_TIMEOUT
        }
        this.options = new ScanWithAquaOptions(config)
        this.aqua = aqua
        this.bitbucket = bitbucket
        this.openShift = openShift
        this.nexus = nexusService
        this.configurationAquaCluster = configurationAquaCluster
        this.configurationAquaProject = configurationAquaProject
    }

    protected run() {
        String errorMessages = ''
        def util = ServiceRegistry.instance.get(MROPipelineUtil)

        // Addresses form Aqua advises mails.
        String alertEmails = configurationAquaCluster['alertEmails']
        if (!alertEmails) {
            logger.info "Please provide the alert emails of the Aqua platform!"
            errorMessages = '<li>Provide the alert emails of the Aqua platform</li>'
        }
        // Base URL of Aqua server.
        String url = configurationAquaCluster['url']
        if (!url) {
            logger.info "Please provide the URL of the Aqua platform!"
            errorMessages += "<li>Provide the Aqua url of platform</li>"
        }
        // Name in Aqua of the registry that contains the image we want to scan.
        String registry = configurationAquaCluster['registry']
        if (!registry) {
            logger.info "Please provide the name of the registry that contains the image of interest!"
            errorMessages += "<li>Provide the name of the registry to use in Aqua</li>"
        }
        // Name in Nexus of the repository where to store the reports.
        String nexusRepository = configurationAquaCluster['nexusRepository']
        if (!nexusRepository) {
            logger.info "Please provide the name of the repository in Nexus to store the reports!"
            errorMessages += "<li>Provide the name of the repository in Nexus to use with Aqua</li>"
        }

        // Name of the credentials that stores the username/password of a user with access
        // to the Aqua server identified by "aquaUrl", defaults to the cd-user
        String secretName = configurationAquaCluster['secretName']
        String credentialsId = context.cdProject + "-"
        if (secretName) {
            credentialsId += secretName
        } else {
            credentialsId = context.credentialsId
            logger.info("No custom secretName was specified in the aqua ConfigMap, continuing with default " +
                "credentialsId '" + credentialsId + "'...")
        }
        // reference of the image that was build in this pipeline run
        String imageRef = getImageRef()
        if (!imageRef) {
            logger.info "Skipping as imageRef could not be retrieved. Possible reasons are:\n" +
                "-> The aqua stage runs before the image build stage and hence no new image was created yet.\n" +
                "-> The image build stage was not executed because the image was imported.\n" +
                "-> The aqua stage and the image build stage have different values for 'resourceName' set."
            errorMessages += "<li>Skipping as imageRef could not be retrieved</li>"
            notifyAquaProblem(alertEmails, errorMessages)
            return
        }

        String reportFile = "aqua-report.html"
        String jsonFile = "aqua-report.json"
        int returnCode = scanViaCli(url, registry, imageRef, credentialsId, reportFile, jsonFile)
        if (![AquaService.AQUA_SUCCESS, AquaService.AQUA_POLICIES_ERROR].contains(returnCode)) {
            errorMessages += "<li>Error executing Aqua CLI</li>"
        }
        List actionableVulnerabilities = null
        // If report exists
        if ([AquaService.AQUA_SUCCESS, AquaService.AQUA_POLICIES_ERROR].contains(returnCode)) {
            try {
                def resultInfo = steps.readJSON(text: steps.readFile(file: jsonFile) as String) as Map
                logger.info("AQUA JSON result: " + steps.readFile(file: jsonFile) as String)

                actionableVulnerabilities = filterRemoteCriticalWithSolutionVulnerabilities(resultInfo);
                logger.info("AQUA actionableVulnerabilities: " + actionableVulnerabilities)

                Map vulnerabilities = resultInfo.vulnerability_summary as Map
                // returnCode is 0 --> Success or 4 --> Error policies
                // with sum of errorCodes > 0 BitbucketCodeInsight is FAIL
                def errorCodes = [returnCode,
                                  vulnerabilities.critical ?: 0,
                                  vulnerabilities.malware ?: 0]

                URI reportUriNexus = archiveReportInNexus(reportFile, nexusRepository)
                createBitbucketCodeInsightReport(url, nexusRepository ? reportUriNexus.toString() : null,
                    registry, imageRef, errorCodes.sum() as int, errorMessages)
                archiveReportInJenkins(!context.triggeredByOrchestrationPipeline, reportFile)
            } catch (err) {
                logger.warn("Error archiving the Aqua reports due to: ${err}")
                errorMessages += "<li>Error archiving Aqua reports</li>"
            }
        } else {
            logger.info("PROBLEMS WITH AQUA")
            createBitbucketCodeInsightReport(errorMessages)
        }

        if (actionableVulnerabilities?.size() > 0) { // We need to fail the pipeline
            util.failBuild("Remote critical vulnerability found: " + actionableVulnerabilities)
            throw new Error("Remote critical vulnerability found: " + actionableVulnerabilities)
        }

        notifyAquaProblem(alertEmails, errorMessages)
        return
    }

    private String getImageRef() {
        // take the image ref of the image that is being build in the image build stage
        Map<String, String> buildInfo =
            context.getBuildArtifactURIs().builds[options.resourceName] as Map<String, String>
        if (buildInfo) {
            String imageRef = buildInfo.image
            return imageRef[(imageRef.indexOf("/") + 1)..-1]
        }
        return null
    }

    @SuppressWarnings('ParameterCount')
    private int scanViaCli(String aquaUrl, String registry, String imageRef,
                           String credentialsId, String reportFile, String jsonFile) {
        logger.startClocked(options.resourceName)
        int returnCode = aqua.scanViaCli(aquaUrl, registry, imageRef, credentialsId, reportFile, jsonFile,
            options.scanTimeoutSeconds)
        // see possible return codes at https://docs.aquasec.com/docs/scanner-cmd-scan#section-return-codes
        switch (returnCode) {
            case AquaService.AQUA_SUCCESS:
                logger.info "Finished scan via Aqua CLI successfully!"
                break
            case AquaService.AQUA_OPERATIONAL_ERROR:
                logger.info "An error occurred in processing the scan request " +
                    "(e.g. invalid command line options, image not pulled, operational error)."
                break
            case AquaService.AQUA_POLICIES_ERROR:
                logger.info "The image scanned failed at least one of the Image Assurance Policies specified."
                break
            default:
                logger.info "An unknown return code was returned: ${returnCode}"
        }
        logger.infoClocked(options.resourceName, "Aqua scan (via CLI)")
        return returnCode
    }

    @SuppressWarnings('ParameterCount')
    private createBitbucketCodeInsightReport(String aquaUrl, String nexusUrlReport,
                                             String registry, String imageRef, int returnCode, String messages) {
        String aquaScanUrl = aquaUrl + "/#/images/" + registry + "/" + imageRef.replace("/", "%2F") + "/vulns"
        String title = "Aqua Security"
        String details = "Please visit the following links to review the Aqua Security scan report:"

        String result = returnCode == 0 ? "PASS" : "FAIL"

        def data = [
            key: BITBUCKET_AQUA_REPORT_KEY,
            title: title,
            link: nexusUrlReport,
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Aqua",
                    link: aquaScanUrl
                ]
            ],
            details: details,
            result: result,
        ]
        if (nexusUrlReport) {
            ((List)data.otherLinks).add([
                title: "Report",
                text: "Result in Nexus",
                link: nexusUrlReport,
            ])
        }
        if (messages) {
            data.put("messages", [
                [ title: "Messages", value: prepareMessageToBitbucket(messages), ]
            ])
        }

        bitbucket.createCodeInsightReport(data, context.repoName, context.gitCommit)
    }

    private createBitbucketCodeInsightReport(String messages) {
        String title = "Aqua Security"
        String details = "There was some problems with Aqua:"

        String result = "FAIL"

        def data = [
            key: BITBUCKET_AQUA_REPORT_KEY,
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
                    "${new Date().format('yyyy-MM-dd')}-${context.buildNumber}/aqua",
                "report.html",
                (steps.readFile(file: reportFile) as String).bytes, "text/html")

            logger.info "Report stored in: ${report}"

            return report
        } catch (err) {
            logger.warn("Error archiving the Aqua reports in Nexus due to: ${err}")
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
        String aquaScanStashPath = "scsr-report-${context.componentId}-${context.buildNumber}"
        context.addArtifactURI('aquaScanStashPath', aquaScanStashPath)

        steps.stash(
            name: "${aquaScanStashPath}",
            includes: 'artifacts/SCSR*',
            allowEmpty: true
        )
        context.addArtifactURI('SCSR', targetReport)
    }

    private void notifyAquaProblem(String recipients = '', String message = '') {
        String subject = "Build $context.componentId on project $context.projectId had some problems with Aqua!"
        String body = "<p>$subject</p> " +
            "<p>URL : <a href=\"$context.buildUrl\">$context.buildUrl</a></p> <ul>$message</ul>"

        if (message) {
            steps.emailext(
                body: body, mimeType: 'text/html',
                replyTo: '$script.DEFAULT_REPLYTO', subject: subject,
                to: recipients
            )
        }
    }

    private List filterRemoteCriticalWithSolutionVulnerabilities(Map aquaJsonMap) {
        List result = []
        aquaJsonMap.resources.each { it ->
            (it as Map).vulnerabilities.each { vul ->
                Map vulnerability = vul as Map
                if ((vulnerability?.exploit_type as String)?.equalsIgnoreCase("remote")
                    && (vulnerability?.aqua_severity as String)?.equalsIgnoreCase("critical")
                    && !StringUtils.isEmpty(vulnerability?.solution as String)) {
                    result.push(vulnerability)
                }
            }
        }
        return result
    }
}
