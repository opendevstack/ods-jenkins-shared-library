package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.apache.commons.lang3.StringUtils
import org.ods.orchestration.util.GitUtil
import org.ods.services.AquaRemoteCriticalVulnerabilityWithSolutionException
import org.ods.services.AquaService
import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger

@TypeChecked
class ScanWithAquaStage extends Stage {

    static final String REMOTE_EXPLOIT_TYPE = 'remote'
    static final String CRITICAL_AQUA_SEVERITY = 'critical'
    static final String STAGE_NAME = 'Aqua Security Scan'
    static final String AQUA_CONFIG_MAP_NAME = "aqua"
    static final String BITBUCKET_AQUA_REPORT_KEY = "ods.sec"
    static final Integer AQUA_DEFAULT_TIMEOUT = 300
    private final AquaService aqua
    private final BitbucketService bitbucket
    private final OpenShiftService openShift
    private final NexusService nexus
    private final ScanWithAquaOptions options
    private final Map configurationAquaCluster
    private final Map configurationAquaProject

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

        def reportImageRefName = createImageRefNameForReport(imageRef)
        String reportFile = "aqua-report-${reportImageRefName}.html"
        String jsonFile = "aqua-report-${reportImageRefName}.json"
        int returnCode = scanViaCli(url, registry, imageRef, credentialsId, reportFile, jsonFile)
        if (![AquaService.AQUA_SUCCESS, AquaService.AQUA_POLICIES_ERROR].contains(returnCode)) {
            errorMessages += "<li>Error executing Aqua CLI</li>"
        }
        List actionableVulnerabilities = null
        String nexusReportLink = null
        // If report exists
        if ([AquaService.AQUA_SUCCESS, AquaService.AQUA_POLICIES_ERROR].contains(returnCode)) {
            try {
                def resultInfo = steps.readJSON(text: steps.readFile(file: jsonFile) as String) as Map

                Set whitelistedRECVs = []
                actionableVulnerabilities = filterRemoteCriticalWithSolutionVulnerabilities(resultInfo,
                    whitelistedRECVs)
                if (whitelistedRECVs.size() > 0) {
                    logger.warn(buildWhiteListedRECVsMessage(whitelistedRECVs))
                }

                Map vulnerabilities = resultInfo.vulnerability_summary as Map
                // returnCode is 0 --> Success or 4 --> Error policies
                // with sum of errorCodes > 0 BitbucketCodeInsight is FAIL
                def errorCodes = [returnCode,
                                  vulnerabilities.critical ?: 0,
                                  vulnerabilities.malware ?: 0]

                URI reportUriNexus = archiveReportInNexus(reportFile, nexusRepository)
                nexusReportLink = nexusRepository ? reportUriNexus.toString() : null
                createBitbucketCodeInsightReport(url, nexusReportLink,
                    registry, imageRef, errorCodes.sum() as int, errorMessages, actionableVulnerabilities)
                archiveReportInJenkins(reportFile)
            } catch (err) {
                logger.warn("Error archiving the Aqua reports due to: ${err}")
                errorMessages += "<li>Error archiving Aqua reports</li>"
            }
        } else {
            createBitbucketCodeInsightReport(errorMessages)
        }

        notifyAquaProblem(alertEmails, errorMessages)

        if (actionableVulnerabilities?.size() > 0) { // We need to mark the pipeline and delete the image
            performActionsForRECVs(actionableVulnerabilities, nexusReportLink)
        }

        return
    }

    static String createImageRefNameForReport(String imageRefName) {
        if (!imageRefName) {
            throw new IllegalArgumentException ("imageRefName must not be null")
        }

        // Sample image refs:
        // registryHostName:port/namespace/imageName:tag -> imageName:tag -> imageName
        // registryHostName:port/namespace/imageName@sha256:hexDigest ->
        //     -> imageName@sha256:hexDigest -> imageName@sha256 -> imageName
        return imageRefName.split('/').last().split(':').first().split('@').first()
    }

    private void performActionsForRECVs(List actionableVulnerabilities, String nexusReportLink) {
        def scannedBranch = computeScannedBranch()
        logger.info("Aqua scanned branch: ${scannedBranch}")
        addAquaVulnerabilityObjectsToContext(actionableVulnerabilities, nexusReportLink, scannedBranch)
        String response = openShift.deleteImage(context.getComponentId() + ":" + context.getShortGitCommit())
        logger.info("Delete image response: " + response)
        throw new AquaRemoteCriticalVulnerabilityWithSolutionException(
            buildActionableMessageForAquaVulnerabilities(actionableVulnerabilities: actionableVulnerabilities,
                nexusReportLink: nexusReportLink, gitUrl: context.getGitUrl(), gitBranch: scannedBranch,
                gitCommit: context.getGitCommit(), repoName: context.getRepoName()))
    }

    private void addAquaVulnerabilityObjectsToContext(List actionableVulnerabilities, String nexusReportLink,
            String scannedBranch) {
        context.addArtifactURI('aquaCriticalVulnerability', actionableVulnerabilities)
        context.addArtifactURI('jiraComponentId', context.getComponentId())
        context.addArtifactURI('gitUrl', context.getGitUrl())
        context.addArtifactURI('gitBranch', scannedBranch)
        context.addArtifactURI('repoName', context.getRepoName())
        context.addArtifactURI('nexusReportLink', nexusReportLink)
    }

    private String buildWhiteListedRECVsMessage(Set whiteListedRECVs) {
        StringBuilder message = new StringBuilder("The Aqua scan detected the following remotely " +
            "exploitable critical vulnerabilities which were whitelisted in Aqua: ")
        message.append(whiteListedRECVs.join(", "))
        return message.toString()
    }

    private String buildActionableMessageForAquaVulnerabilities(Map args) {
        StringBuilder message = new StringBuilder()
        String gitBranchUrl = GitUtil.buildGitBranchUrl(args.gitUrl as String, context.getProjectId(),
            args.repoName as String, args.gitBranch as String)
        message.append("We detected remotely exploitable critical vulnerabilities in repository ${gitBranchUrl}. " +
            "Due to their high severity, we must stop the delivery " +
            "process until all vulnerabilities have been addressed. ")

        message.append("\n\nThe following vulnerabilities were found:")
        def count = 1
        for (def vulnerability : args.actionableVulnerabilities) {
            message.append("\n\n${count}.    Vulnerability name: " + (vulnerability as Map).name as String)
            message.append("\n\n${count}.1.  Description: " + (vulnerability as Map).description as String)
            message.append("\n\n${count}.2.  Solution: " + (vulnerability as Map).solution as String)
            message.append("\n")
            count++
        }
        def openPRs = getOpenPRsForCommit(args.gitCommit as String, args.repoName as String)
        if (openPRs.size() > 0) {
            message.append("\nThis commit exists in the following open pull requests: ")
            def cnt = 1
            for (def pr : openPRs) {
                message.append("\n\n${cnt}.    Pull request: " + (pr as Map).title as String)
                message.append("\n\n${cnt}.1.  Link: " + (pr as Map).link as String)
                message.append("\n")
                cnt++
            }
        }
        if (args.nexusReportLink != null) {
            message.append("\nYou can find the complete security scan report here: ${args.nexusReportLink}.\n")
        }
        return message.toString()
    }

    private List getOpenPRsForCommit(String gitCommit, String repoName) {
        def apiResponse = bitbucket.getPullRequestsForCommit(repoName, gitCommit)
        def prs = []
        try {
            def js = steps.readJSON(text: apiResponse) as Map
            prs = js['values']
            if (prs == null) {
                throw new RuntimeException('Field "values" of JSON response must not be empty!')
            }
        } catch (Exception ex) {
            logger.warn "Could not understand API response. Error was: ${ex}"
            return []
        }
        def response = []
        for (def i = 0; i < (prs as List).size(); i++) {
            Map pr = (prs as List)[i] as Map
            if (!(pr.open as Boolean)) { // We only consider Open PRs
                continue
            }
            response.add([
                title: pr.title,
                link: (((pr.links as Map).self as List)[0] as Map).href,
            ])
        }
        response
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
                                             String registry, String imageRef, int returnCode, String messages,
                                                List actionableVulnerabilities) {
        String aquaScanUrl = aquaUrl + "/#/images/" + registry + "/" + imageRef.replace("/", "%2F") + "/vulns"
        String title = "Aqua Security (Image: ${createImageRefNameForReport(imageRef)})"
        String details = "Please visit the following links to review the Aqua Security scan report:"

        String result = returnCode == 0 ? "PASS" : "FAIL"

        def data = [
            key: BITBUCKET_AQUA_REPORT_KEY + "_${createImageRefNameForReport(imageRef)}",
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
        if (actionableVulnerabilities?.size() > 0) {
            if (!data.messages) {
                data.put("messages", [])
            }
            ((List) data.messages).add([
                title: "Blocking",
                value: "Yes",
            ])
        }

        bitbucket.createCodeInsightReport(data, context.repoName, context.gitCommit)
    }

    private createBitbucketCodeInsightReport(String messages) {
        String title = "Aqua Security"
        String details = "There were some problems with Aqua:"

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
                    "${context.buildTime.format('YYYY-MM-dd_HH-mm-ss')}_${context.buildNumber}/aqua",
                reportFile,
                (steps.readFile(file: reportFile) as String).bytes, "text/html")

            logger.info "Report stored in: ${report}"
            return report
        } catch (err) {
            logger.warn("Error archiving the Aqua reports in Nexus due to: ${err}")
            return null
        }
    }

    private archiveReportInJenkins(String reportFile) {
        String targetReport = "SCSR-${context.projectId}-${context.componentId}-${reportFile}"
        steps.sh(
            label: 'Create artifacts dir',
            script: 'mkdir -p artifacts'
        )
        steps.sh(
            label: 'Rename report to SCSR',
            script: "mv ${reportFile} artifacts/${targetReport}"
        )
        steps.archiveArtifacts(artifacts: 'artifacts/SCSR*')

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

    private List filterRemoteCriticalWithSolutionVulnerabilities(Map aquaJsonMap, Set whitelistedRECVs) {
        List result = []
        aquaJsonMap.resources.each { it ->
            (it as Map).vulnerabilities.each { vul ->
                Map vulnerability = vul as Map
                if ((vulnerability?.exploit_type as String)?.split(',')*.trim()
                    .any { it.equalsIgnoreCase(REMOTE_EXPLOIT_TYPE) }
                    && (vulnerability?.aqua_severity as String)?.equalsIgnoreCase(CRITICAL_AQUA_SEVERITY)
                    && !StringUtils.isEmpty((vulnerability?.solution as String).trim())) {
                    if (Boolean.parseBoolean(vulnerability?.already_acknowledged as String)) {
                        whitelistedRECVs.add(vulnerability.name)
                    } else {
                        result.push(vulnerability)
                    }
                }
            }
        }
        return result
    }

    private String computeScannedBranch() {
        def scannedBranch = context.getGitBranch()
        if (scannedBranch.toLowerCase().startsWith("release/")) {
            // We need to check that the branch was created in BitBucket otherwise we scanned the default branch
            Map branchesResponse = bitbucket.findRepoBranches(context.getRepoName(), scannedBranch)
            List matchedBranches = branchesResponse['values'] as List
            if (matchedBranches?.size() > 0) {
                for (def i = 0; i < matchedBranches.size(); i++) {
                    Map branch = matchedBranches[i]
                    if (branch.displayId == scannedBranch) {
                        return scannedBranch
                    }
                }
            }
            scannedBranch = bitbucket.getDefaultBranch(context.getRepoName())
        }
        return scannedBranch
    }

}
