package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.orchestration.util.PDFUtil
import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.services.SonarQubeService
import org.ods.util.ILogger

@SuppressWarnings('ParameterCount')
@TypeChecked
class ScanWithSonarStage extends Stage {

    static final String STAGE_NAME = 'SonarQube Analysis'
    static final String BITBUCKET_SONARQUBE_REPORT_KEY = "org.opendevstack.sonarqube"
    static final String DEFAULT_NEXUS_REPOSITORY = "leva-documentation"
    private final BitbucketService bitbucket
    private final SonarQubeService sonarQube
    private final NexusService nexus
    private final ScanWithSonarOptions options

    @TypeChecked(TypeCheckingMode.SKIP)
    ScanWithSonarStage(
        def script,
        IContext context,
        Map<String, Object> config,
        BitbucketService bitbucket,
        SonarQubeService sonarQube,
        NexusService nexus,
        ILogger logger) {
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

        this.options = new ScanWithSonarOptions(config)
        this.bitbucket = bitbucket
        this.sonarQube = sonarQube
        this.nexus = nexus
    }

    // This is called from Stage#execute if the branch being built is eligible.
    protected run() {
        if (options.longLivedBranches) {
            logger.info "Long-lived branches: ${options.longLivedBranches.join(', ')}"
        } else {
            logger.info 'No long-lived branches configured.'
        }

        def sonarProperties = sonarQube.readProperties()

        def sonarProjectKey = "${context.projectId}-${context.componentId}".toString()
        sonarProperties['sonar.projectKey'] = sonarProjectKey
        sonarProperties['sonar.projectName'] = sonarProjectKey
        sonarProperties['sonar.branch.name'] = context.gitBranch

        logger.startClocked("${sonarProjectKey}-sq-scan")
        scan(sonarProperties)
        logger.debugClocked("${sonarProjectKey}-sq-scan", (null as String))
        retryComputeEngineStatusCheck()

        generateAndArchiveReports(
            sonarProjectKey,
            context.buildTag,
            sonarProperties['sonar.branch.name'].toString(),
            context.sonarQubeEdition,
            !context.triggeredByOrchestrationPipeline
        )

        // We need always the QG to put in insight report in Bitbucket
        def qualityGateResult = getQualityGateResult(sonarProjectKey)
        logger.info "SonarQube Quality Gate value: ${qualityGateResult}"
        if (options.requireQualityGatePass) {
            if (qualityGateResult == 'ERROR') {
                steps.error 'Quality gate failed!'
            } else if (qualityGateResult == 'UNKNOWN') {
                steps.error 'Quality gate unknown!'
            } else {
                steps.echo 'Quality gate passed.'
            }
        }
        def report = generateTempFileFromReport("artifacts/" + context.getBuildArtifactURIs().get('SCRR-MD'))
        URI reportUriNexus = generateAndArchiveReportInNexus(report,
            context.sonarQubeNexusRepository ? context.sonarQubeNexusRepository : DEFAULT_NEXUS_REPOSITORY)
        createBitbucketCodeInsightReport(qualityGateResult, reportUriNexus.toString(), sonarProjectKey)
    }

    private void scan(Map sonarProperties) {
        def pullRequestInfo = assemblePullRequestInfo()
        def doScan = { Map<String, Object> prInfo ->
            sonarQube.scan(sonarProperties, context.gitCommit, prInfo, context.sonarQubeEdition)
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
        String author,
        String sonarBranch,
        String sonarQubeEdition,
        boolean archive) {
        def targetReport = "SCRR-${projectKey}.docx"
        def targetReportMd = "SCRR-${projectKey}.md"
        sonarQube.generateCNESReport(projectKey, author, sonarBranch, sonarQubeEdition)
        steps.sh(
            label: 'Create artifacts dir',
            script: 'mkdir -p artifacts'
        )
        steps.sh(
            label: 'Move report to artifacts dir',
            script: 'mv *-analysis-report.docx* artifacts/; mv *-analysis-report.md* artifacts/'
        )
        steps.sh(
            label: 'Rename report to SCRR',
            script: """
            mv artifacts/*-analysis-report.docx* artifacts/${targetReport};
            mv artifacts/*-analysis-report.md* artifacts/${targetReportMd}
            """
        )
        if (archive) {
            steps.archiveArtifacts(artifacts: 'artifacts/SCRR*')
        }

        def sonarqubeStashPath = "scrr-report-${context.componentId}-${context.buildNumber}"
        context.addArtifactURI('sonarqubeScanStashPath', sonarqubeStashPath)

        steps.stash(
            name: "${sonarqubeStashPath}",
            includes: 'artifacts/SCRR*',
            allowEmpty: true
        )

        context.addArtifactURI('SCRR', targetReport)
        context.addArtifactURI('SCRR-MD', targetReportMd)
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private String getQualityGateResult(String sonarProjectKey) {
        def qualityGateJSON = sonarQube.getQualityGateJSON(sonarProjectKey)
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
    private String retryComputeEngineStatusCheck() {
        def waitTime = 5 // seconds
        def retries = 5
        def taskProperties = sonarQube.readTask()

        for (def i = 0; i < retries; i++) {
            def computeEngineTaskResult
            try {
                computeEngineTaskResult = sonarQube.getComputeEngineTaskResult(taskProperties['ceTaskId'])
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

    private createBitbucketCodeInsightReport(String qualityGateResult, String nexusUrlReport, String sonarProjectKey) {
        String sorQubeScanUrl = sonarQube.getSonarQubeHostUrl() + "/dashboard?id=${sonarProjectKey}"
        String title = "SonarQube"
        String details = "Please visit the following links to review the SonarQube report:"
        String result = qualityGateResult == "OK" ? "PASS" : "FAIL"

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

    @SuppressWarnings(['JavaIoPackageAccess', 'FileCreateTempFile'])
    private File generateTempFileFromReport(String report) {
        new File(this.steps.env.WORKSPACE.toString()).mkdirs()
        File file = new File("${this.steps.env.WORKSPACE}/sonarReport.md")
        file.write(steps.readFile(file: report) as String)
        return file
    }

    private URI generateAndArchiveReportInNexus(File reportMd, nexusRepository) {
        // Generate the PDF from temp markdown file
        def pdfReport = new PDFUtil().convertFromMarkdown(reportMd, true)

        URI report = nexus.storeArtifact(
            "${nexusRepository}",
            "${context.projectId}/${this.options.resourceName}/" +
                "${new Date().format('yyyy-MM-dd')}-${context.buildNumber}/sonarQube",
            "report.pdf",
            pdfReport, "application/pdf")

        logger.info "Report stored in: ${report}"

        return report
    }

}
