package org.ods.component

import com.cloudbees.groovy.cps.NonCPS

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
        } else {
            config.eligibleBranches = context.sonarQubeBranch.split(',')
        }
        if (!config.containsKey('analyzePullRequests')) {
            config.analyzePullRequests = true
        }
        if (!config.longLivedBranches) {
            config.longLivedBranches = extractLongLivedBranches(context.branchToEnvironmentMapping)
        }
        if (!config.containsKey('requireQualityGatePass')) {
            config.requireQualityGatePass = false
        }
        this.bitbucket = bitbucket
        this.sonarQube = sonarQube
    }

    protected run() {
        if (!isEligibleBranch(config.eligibleBranches, context.gitBranch)) {
            logger.info "Skipping as branch '${context.gitBranch}' is not covered by the 'branch' option."
            return
        }

        script.echo "Long-lived branches: ${config.longLivedBranches.join(', ')}."

        def sonarProperties = sonarQube.readProperties()

        def sonarProjectKey = "${context.projectId}-${context.componentId}"
        sonarProperties['sonar.projectKey'] = sonarProjectKey
        sonarProperties['sonar.projectName'] = sonarProjectKey

        logger.startClocked("${sonarProjectKey}-sq-scan")
        scan(sonarProperties)
        logger.debugClocked("${sonarProjectKey}-sq-scan")

        generateAndArchiveReports(sonarProjectKey, context.buildTag, context.localCheckoutEnabled)

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

    @NonCPS
    private List<String> extractLongLivedBranches(Map branchMapping) {
        def branches = branchMapping.keySet()
        branches.removeAll { it.toLowerCase().endsWith('/') }
        branches.removeAll { it == '*' }
        branches.toList()
    }

    private void scan(Map sonarProperties) {
        def pullRequestInfo = assemblePullRequestInfo()
        def doScan = { Map prInfo ->
            sonarQube.scan(sonarProperties, context.gitCommit, prInfo, context.debug)
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

    private generateAndArchiveReports(String projectKey, String author, boolean archive) {
        def targetReport = "SCRR-${projectKey}.docx"
        def targetReportMd = "SCRR-${projectKey}.md"
        sonarQube.generateCNESReport(projectKey, author)
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

}
