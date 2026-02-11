package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.services.SonarQubeService
import org.ods.util.ILogger

@SuppressWarnings('ParameterCount')
@TypeChecked
class ScanWithSonarStage extends Stage {

    static final String STAGE_NAME = 'SonarQube Analysis'
    static final String BITBUCKET_SONARQUBE_REPORT_KEY = "ods.sonarqube"
    static final String DEFAULT_NEXUS_REPOSITORY = "leva-documentation"
    static final String SONAR_CONFIG_MAP_NAME = "sonarqube-scan"
    private final BitbucketService bitbucket
    private final SonarQubeService sonarQube
    private final NexusService nexus
    private final ScanWithSonarOptions options
    private final Map configurationSonarCluster
    private final Map configurationSonarProject
    private final String exclusions
    private final String sonarQubeAccount
    private final Boolean sonarQubeProjectsPrivate

    @TypeChecked(TypeCheckingMode.SKIP)
    ScanWithSonarStage(
        def script,
        IContext context,
        Map<String, Object> config,
        BitbucketService bitbucket,
        SonarQubeService sonarQube,
        NexusService nexus,
        ILogger logger,
        Map configurationSonarCluster = [:],
        Map configurationSonarProject = [:]
    ) {
        super(script, context, logger)
        if (!config.resourceName) {
            config.resourceName = context.componentId
        }
        // If user did not explicitly define which branches to scan,
        // infer that information from elsewhere.
        if (!config.containsKey('branches') && !config.containsKey('branch')) {
            if (context.sonarQubeBranch) {
                config.branches = context.sonarQubeBranch.split(',')
            } else if (context.sonarQubeEdition != 'community') {
                config.branches = ['*']
            } else {
                // Community edition can only scan one branch in a meaningful way
                config.branches = ['master']
            }
        }

        if (!config.containsKey('longLivedBranches')) {
            config.longLivedBranches = context.branchToEnvironmentMapping
                .keySet()
                .findAll { it != '*' && !it.endsWith('/') }
                .toList()
        }
        if (!config.containsKey('analyzePullRequests')) {
            config.analyzePullRequests = false
        }
        if (!config.containsKey('requireQualityGatePass')) {
            config.requireQualityGatePass = false
        }
        if (configurationSonarCluster['nexusRepository']) {
            config.sonarQubeNexusRepository = configurationSonarCluster['nexusRepository']
        }

        this.options = new ScanWithSonarOptions(config)
        this.bitbucket = bitbucket
        this.sonarQube = sonarQube
        this.nexus = nexus
        this.configurationSonarCluster = configurationSonarCluster
        this.configurationSonarProject = configurationSonarProject
        this.exclusions = configurationSonarCluster['exclusions'] ?: ""
        this.sonarQubeAccount = configurationSonarCluster['sonarQubeAccount'] ?: "cd-user-with-password"
        def privateProjectsValue = configurationSonarCluster['sonarQubeProjectsPrivate']
        this.sonarQubeProjectsPrivate = privateProjectsValue?.toString()?.toLowerCase() == 'true' ?: false
    }

    // This is called from Stage#execute if the branch being built is eligible.
    protected run() {
        if (options.longLivedBranches) {
            logger.info "Long-lived branches: ${options.longLivedBranches.join(', ')}"
        } else {
            logger.info 'No long-lived branches configured.'
        }
        if (exclusions) {
            logger.info("SonarQube scan will run for the entire repository source code." +
            " The following exclusions will be applied: ${exclusions}")
        } else {
            logger.info("SonarQube scan will run for the entire repository source code." +
            " No exclusions configured.")
        }

        def sonarProperties = sonarQube.readProperties() ?: [:]
        def sonarProjectKey = "${context.projectId}-${context.componentId}".toString()
        sonarProperties['sonar.projectKey'] = sonarProjectKey
        sonarProperties['sonar.projectName'] = sonarProjectKey
        sonarProperties['sonar.branch.name'] = context.gitBranch

        def pullRequestInfo = assemblePullRequestInfo()
        def ocSecretName = "sonarqube-private-token"
        def jenkinsCredID = "${context.cdProject}-${sonarQubeAccount}"
        def jenkinsSonarCred = "${context.cdProject}-${ocSecretName}"

        logger.info "Sonarqube private projects value is: ${sonarQubeProjectsPrivate}"
        if (sonarQubeProjectsPrivate) {
            sonarQube.generateAndStoreSonarQubeToken("${jenkinsCredID}", "${context.cdProject}", "${ocSecretName}")
            steps.withCredentials([
                steps.usernamePassword(
                    credentialsId: jenkinsSonarCred,
                    usernameVariable: 'sonarQubeUser',
                    passwordVariable: 'privateToken'
                )
            ]) {
                logger.info("SonarQube private projects enabled, using private token.")
                runSonarQubeScanAndReport(
                    sonarProjectKey,
                    sonarProperties,
                    pullRequestInfo,
                    steps.env.privateToken as String
                )
            }
        } else {
            logger.info("SonarQube private projects disabled, using public token.")
            runSonarQubeScanAndReport(
                sonarProjectKey,
                sonarProperties,
                pullRequestInfo,
                ""
            )
        }
    }

    private void runSonarQubeScanAndReport(
        String sonarProjectKey,
        Map sonarProperties,
        Map pullRequestInfo,
        String privateToken
    ) {
        logger.startClocked("${sonarProjectKey}-sq-scan")
        scan(sonarProperties, pullRequestInfo, privateToken)
        logger.debugClocked("${sonarProjectKey}-sq-scan", (null as String))
        retryComputeEngineStatusCheck(privateToken)

        def targetReport = "sonarqube-report-${sonarProjectKey}.pdf"

        generateAndArchiveReports(
            sonarProjectKey,
            privateToken,
            targetReport
        )

        // Upload report to Nexus and create Bitbucket code insight
        URI nexusUri = generateAndArchiveReportInNexus(targetReport,
            context.sonarQubeNexusRepository ? context.sonarQubeNexusRepository : DEFAULT_NEXUS_REPOSITORY)

        // We need always the QG to put in insight report in Bitbucket
        def qualityGateResult = getQualityGateResult(
            sonarProjectKey,
            context.sonarQubeEdition,
            context.gitBranch,
            pullRequestInfo ? pullRequestInfo.bitbucketPullRequestKey.toString() : null,
            privateToken
        ).toUpperCase()

        createBitbucketCodeInsightReport(qualityGateResult, nexusUri.toString(), sonarProjectKey,
            context.sonarQubeEdition, sonarProperties['sonar.branch.name'].toString())

        // Possible values for qualityGateResult are 'OK', 'WARN', 'ERROR', and 'NONE'.
        // The NONE status is returned when there is no quality gate associated with the analysis.
        switch (qualityGateResult) {
            case 'OK':
                logger.info "Quality gate passed with value ${qualityGateResult}."
                break
            case 'WARN':
                logger.info(
                    "Quality gate passed with value ${qualityGateResult}, " +
                    "but there are conditions that triggered a warning."
                )
                break
            case 'ERROR':
                if (options.requireQualityGatePass) {
                    steps.error "Quality gate failed with value '${qualityGateResult}'! Pipeline will be stopped."
                } else {
                    logger.info(
                        "Quality gate failed with value ${qualityGateResult}, " +
                        "but continuing due to not requiring quality gate pass."
                    )
                }
                break
            case 'NONE':
                if (options.requireQualityGatePass) {
                    steps.error "No quality gate was applied for this analysis! Pipeline will be stopped."
                }
                 else {
                    logger.info(
                        "No quality gate was applied for this analysis, " +
                        "but continuing due to not requiring quality gate pass."
                    )
                }
                break
            default:
                if (options.requireQualityGatePass) {
                    steps.error "Quality gate is unknown! Value: '${qualityGateResult}'. Pipeline will be stopped."
                } else {
                    logger.info(
                        "Quality gate is unknown, its value ${qualityGateResult}, " +
                        "but continuing due to not requiring quality gate pass."
                    )
                }
                break
        }
    }
    private void scan(Map sonarProperties, Map<String, Object> pullRequestInfo, String privateToken) {
        def doScan = { Map<String, Object> prInfo ->
            sonarQube.scan([
                properties: sonarProperties,
                gitCommit: context.gitCommit,
                pullRequestInfo: prInfo,
                sonarQubeEdition: context.sonarQubeEdition,
                exclusions: exclusions,
                privateToken: privateToken,
            ])
        }
        if (pullRequestInfo) {
            bitbucket.withTokenCredentials { username, token ->
                doScan(pullRequestInfo + [bitbucketToken: token])
            }
        } else {
            doScan([:])
        }
    }

    private Map<String, Object> assemblePullRequestInfo() {
        if (!options.analyzePullRequests) {
            logger.info 'PR analysis is disabled.'
            return [:]
        }
        if (options.longLivedBranches.contains(context.gitBranch)) {
            logger.info "Branch '${context.gitBranch}' is considered to be long-lived. " +
                'PR analysis will not be performed.'
            return [:]
        }

        def pullRequest = bitbucket.findPullRequest(context.repoName, context.gitBranch)

        if (pullRequest) {
            return [
                bitbucketUrl: context.bitbucketUrl,
                bitbucketProject: context.projectId,
                bitbucketRepository: context.repoName,
                bitbucketPullRequestKey: pullRequest.key,
                branch: context.gitBranch,
                baseBranch: pullRequest.base,
            ]
        }

        def longLivedList = options.longLivedBranches.join(', ')
        logger.info "No open PR found for ${context.gitBranch} " +
            "even though it is not one of the long-lived branches (${longLivedList})."
        return [:]
    }

    private generateAndArchiveReports(
        String projectKey,
        String privateToken,
        String targetReport) {
        sonarQube.generateReport(projectKey, privateToken)
        steps.sh(
            label: 'Create artifacts dir',
            script: "mkdir -p ${steps.env.WORKSPACE}/artifacts"
        )
        steps.sh(
            label: 'Move and rename report to artifacts dir',
            script: "mv sonarqube-report_force_fail.pdf ${steps.env.WORKSPACE}/artifacts/${targetReport} 2>&1 || " +
                    "echo 'Failed to move report'"
        )
        
        try {
            steps.archiveArtifacts(artifacts: "artifacts/sonarqube-report-*")

            def sonarqubeStashPath = "sonarqube-report-${context.componentId}-${context.buildNumber}"
            context.addArtifactURI('sonarqubeScanStashPath', sonarqubeStashPath)

            steps.stash(
                name: "${sonarqubeStashPath}",
                includes: "artifacts/sonarqube-report-*"
            )

            context.addArtifactURI('sonarqube-report', targetReport)
        } catch (Exception e) {
            logger.warn "Failed to archive or stash artifacts: ${e.message}"
        }
        
        
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private String getQualityGateResult(
        String sonarProjectKey,
        String sonarQubeEdition,
        String gitBranch,
        String bitbucketPullRequestKey,
        String privateToken
    ) {
        def qualityGateJSON = sonarQube.getQualityGateJSON(
            sonarProjectKey,
            sonarQubeEdition,
            gitBranch,
            bitbucketPullRequestKey,
            privateToken
        )
        try {
            def qualityGateResult = steps.readJSON(text: qualityGateJSON)
            def status = qualityGateResult?.projectStatus?.status ?: 'UNKNOWN'
            return status.toUpperCase()
        } catch (Exception ex) {
            steps.error 'Quality gate status could not be retrieved. ' +
                "Status was: '${qualityGateJSON}'. Error was: ${ex}"
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private String retryComputeEngineStatusCheck(String token) {
        def waitTime = 5 // seconds
        def retries = 5
        def taskProperties = sonarQube.readTask()
        for (def i = 0; i < retries; i++) {
            def computeEngineTaskResult
            try {
                computeEngineTaskResult = sonarQube.getComputeEngineTaskResult(taskProperties['ceTaskId'], token)
            } catch (Exception ex) {
                script.error 'Compute engine status could not be retrieved. ' +
                "Status was: '${computeEngineTaskResult}'. Error was: ${ex}"
            }
            if (computeEngineTaskResult == 'IN_PROGRESS' || computeEngineTaskResult == 'PENDING') {
                logger.info "SonarQube background task has not finished yet."
                script.sleep(waitTime)
            } else if (computeEngineTaskResult == 'SUCCESS') {
                logger.info "SonarQube background task has finished successfully."
                break
            } else if (computeEngineTaskResult == 'FAILED') {
                logger.info "SonarQube background task has failed!"
                steps.error 'SonarQube Scanner stage has ended with errors'
            } else {
                logger.info "Unknown status for the background task"
                steps.error 'SonarQube Scanner stage has ended with errors'
            }
        }
    }

    private createBitbucketCodeInsightReport(
        String qualityGateResult, String nexusUrlReport, String sonarProjectKey, String edition, String branch) {
        String sorQubeScanUrl = sonarQube.getSonarQubeHostUrl() + "/dashboard?id=${sonarProjectKey}"
        String title = "SonarQube"
        String details = "Please visit the following links to review the SonarQube report:"
        String result = qualityGateResult == "OK" ? "PASS" : "FAIL"

        if (edition != 'community') {
            sorQubeScanUrl +=  "&branch=${branch}"
        }

        def data = [
            key: BITBUCKET_SONARQUBE_REPORT_KEY,
            title: title,
            link: nexusUrlReport,
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in SonarQube",
                    link: sorQubeScanUrl
                ],
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: nexusUrlReport
                ]
            ],
            details: details,
            result: result,
        ]

        bitbucket.createCodeInsightReport(data, context.repoName, context.gitCommit)
    }

    private URI generateAndArchiveReportInNexus(String targetReport, nexusRepository) {
        def reportPath = "${steps.env.WORKSPACE}/artifacts/${targetReport}"
        def reportBytes = steps.readFile(file: reportPath, encoding: "ISO-8859-1") as String
        byte[] pdfBytes = reportBytes.getBytes("ISO-8859-1")

        URI report = nexus.storeArtifact(
            "${nexusRepository}",
            "${context.projectId}/${this.options.resourceName}/" +
                "${context.buildTime.format('yyyy-MM-dd_HH-mm-ss')}_${context.buildNumber}/sonarQube",
            "report.pdf",
            pdfBytes,
            "application/pdf")

        logger.info "Report stored in: ${report}"

        return report
    }

}
