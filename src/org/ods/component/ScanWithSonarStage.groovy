package org.ods.component

import org.ods.services.BitbucketService
import org.ods.services.SonarQubeService
import org.ods.util.ILogger

@SuppressWarnings('ParameterCount')
class ScanWithSonarStage extends Stage {

    public final String STAGE_NAME = 'SonarQube Analysis'
    private final BitbucketService bitbucket
    private final SonarQubeService sonarQube

    ScanWithSonarStage(
        def script,
        IContext context,
        Map config,
        BitbucketService bitbucket,
        SonarQubeService sonarQube,
        ILogger logger) {
        super(script, context, config, logger)
        if (config.branch) {
            config.eligibleBranches = config.branch.split(',')
        } else if (context.sonarQubeBranch) {
            config.eligibleBranches = context.sonarQubeBranch.split(',')
        } else if (context.sonarQubeEdition != 'community') {
            config.eligibleBranches = ['*']
        } else {
            config.eligibleBranches = ['master']
        }
        if (!config.containsKey('longLivedBranches')) {
            config.longLivedBranches = context.branchToEnvironmentMapping
                .keySet()
                .findAll { it != '*' && !it.endsWith('/') }
                .toList()
        }
        if (!config.containsKey('analyzePullRequests')) {
            config.analyzePullRequests = true
        }
        if (!config.containsKey('requireQualityGatePass')) {
            config.requireQualityGatePass = false
        }
        this.bitbucket = bitbucket
        this.sonarQube = sonarQube
    }

    protected run() {
        if (config.eligibleBranches) {
            logger.info "Scanned branches: ${config.eligibleBranches.join(', ')}"
        } else {
            logger.info 'No branches to scan configured.'
            return
        }

        if (!isEligibleBranch(config.eligibleBranches, context.gitBranch)) {
            logger.info "Skipping as branch '${context.gitBranch}' is not covered by the 'branch' option."
            return
        }

        if (config.longLivedBranches) {
            logger.info "Long-lived branches: ${config.longLivedBranches.join(', ')}"
        } else {
            logger.info 'No long-lived branches configured.'
        }

        def sonarProperties = sonarQube.readProperties()

        def sonarProjectKey = "${context.projectId}-${context.componentId}"
        sonarProperties['sonar.projectKey'] = sonarProjectKey
        sonarProperties['sonar.projectName'] = sonarProjectKey
        sonarProperties['sonar.branch.name'] = context.gitBranch

        logger.startClocked("${sonarProjectKey}-sq-scan")
        scan(sonarProperties)
        logger.debugClocked("${sonarProjectKey}-sq-scan")
        retryComputeEngineStatusCheck()

        generateAndArchiveReports(
            sonarProjectKey,
            context.buildTag,
            sonarProperties['sonar.branch.name'],
            context.sonarQubeEdition,
            context.localCheckoutEnabled
        )

        if (config.requireQualityGatePass) {
            def qualityGateResult = getQualityGateResult(sonarProjectKey)
            if (qualityGateResult == 'ERROR') {
                script.error 'Quality gate failed!'
            } else if (qualityGateResult == 'UNKNOWN') {
                script.error 'Quality gate unknown!'
            } else {
                script.echo 'Quality gate passed.'
            }
        }
    }

    private void scan(Map sonarProperties) {
        def pullRequestInfo = assemblePullRequestInfo()
        def doScan = { Map prInfo ->
            sonarQube.scan(sonarProperties, context.gitCommit, prInfo, context.sonarQubeEdition, context.debug)
        }
        if (pullRequestInfo) {
            bitbucket.withTokenCredentials { username, token ->
                doScan(pullRequestInfo + [bitbucketToken: token])
            }
        } else {
            doScan([:])
        }
    }

    private assemblePullRequestInfo() {
        if (!config.analyzePullRequests) {
            logger.info 'PR analysis is disabled.'
            return [:]
        }
        if (config.longLivedBranches.contains(context.gitBranch)) {
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

        def longLivedList = config.longLivedBranches.join(', ')
        logger.info "No open PR found for ${context.gitBranch} " +
            "even though it is not one of the long-lived branches (${longLivedList})."
        return [:]
    }

    private generateAndArchiveReports(String projectKey, String author, String sonarBranch, String sonarQubeEdition,
                                      boolean archive) {
        def targetReport = "SCRR-${projectKey}.docx"
        def targetReportMd = "SCRR-${projectKey}.md"
        sonarQube.generateCNESReport(projectKey, author, sonarBranch, sonarQubeEdition)
        script.sh(
            label: 'Create artifacts dir',
            script: 'mkdir -p artifacts'
        )
        script.sh(
            label: 'Move report to artifacts dir',
            script: 'mv *-analysis-report.docx* artifacts/; mv *-analysis-report.md* artifacts/'
        )
        script.sh(
            label: 'Rename report to SCRR',
            script: """
            mv artifacts/*-analysis-report.docx* artifacts/${targetReport};
            mv artifacts/*-analysis-report.md* artifacts/${targetReportMd}
            """
        )
        if (archive) {
            script.archiveArtifacts(artifacts: 'artifacts/SCRR*')
        }

        def sonarqubeStashPath = "scrr-report-${context.componentId}-${context.buildNumber}"
        context.addArtifactURI('sonarqubeScanStashPath', sonarqubeStashPath)

        script.stash(
            name: "${sonarqubeStashPath}",
            includes: 'artifacts/SCRR*',
            allowEmpty: true
        )
        context.addArtifactURI('SCRR', targetReport)
        context.addArtifactURI('SCRR-MD', targetReportMd)
    }

    private String getQualityGateResult(String sonarProjectKey) {
        def qualityGateJSON = sonarQube.getQualityGateJSON(sonarProjectKey)
        try {
            def qualityGateResult = script.readJSON(text: qualityGateJSON)
            def status = qualityGateResult?.projectStatus?.projectStatus ?: 'UNKNOWN'
            return status.toUpperCase()
        } catch (Exception ex) {
            script.error 'Quality gate status could not be retrieved. ' +
                "Status was: '${qualityGateJSON}'. Error was: ${ex}"
        }
    }

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

}
