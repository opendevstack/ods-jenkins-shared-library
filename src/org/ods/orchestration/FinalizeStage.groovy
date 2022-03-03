package org.ods.orchestration

import org.ods.orchestration.util.ProjectMessagesUtil
import org.ods.services.ServiceRegistry
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.services.BitbucketService
import org.ods.services.OpenShiftService
import org.ods.services.GitService
import org.ods.util.PipelineSteps
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import org.ods.util.ILogger

import groovy.json.JsonOutput

class FinalizeStage extends Stage {

    public final String STAGE_NAME = 'Finalize'

    FinalizeStage(def script, Project project, List<Set<Map>> repos) {
        super(script, project, repos)
    }

    @SuppressWarnings(['ParameterName', 'AbcMetric', 'MethodSize'])
    def run() {
        def steps = ServiceRegistry.instance.get(PipelineSteps)
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
        def os = ServiceRegistry.instance.get(OpenShiftService)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)
        def bitbucket = ServiceRegistry.instance.get(BitbucketService)
        def git = ServiceRegistry.instance.get(GitService)
        ILogger logger = ServiceRegistry.instance.get(Logger)

        def phase = MROPipelineUtil.PipelinePhases.FINALIZE

        def preExecuteRepo = { steps_, repo ->
            levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
        }

        def postExecuteRepo = { steps_, repo ->
            levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, repo)
        }

        if (project.isAssembleMode) {
            // Check if the target environment exists in OpenShift
            def targetProject = project.targetProject
            if (!os.envExists(targetProject)) {
                throw new RuntimeException(
                    "Error: target environment '${targetProject}' does not exist in OpenShift."
                )
            }
        }

        def agentCondition = project.isAssembleMode && repos.size() > 0
        runOnAgentPod(agentCondition) {
            // Execute phase for each repository - here in parallel, all repos
            Map repoFinalizeTasks = [ : ]
            util.prepareExecutePhaseForReposNamedJob(
                phase, repos, preExecuteRepo, postExecuteRepo).each { group ->
                repoFinalizeTasks << group
            }
            repoFinalizeTasks.failFast = true
            script.parallel(repoFinalizeTasks)
            logger.debug("Integrate into main branch")
            if (project.isAssembleMode && !project.isWorkInProgress) {
                integrateIntoMainBranchRepos(steps, git)
            }

            logger.debug("Gathering commits")
            gatherCreatedExecutionCommits(steps, git)

            if (!project.buildParams.rePromote) {
                pushRepos(steps, git)
                recordAndPushEnvStateForReleaseManager(steps, logger, git)
            }

            // add the tag commit that was created for traceability ..
            logger.debug "Current release manager commit: ${project.gitData.commit}"
            project.gitData.createdExecutionCommit = git.commitSha
        }

        if (project.isAssembleMode && !project.isWorkInProgress) {
            logger.warn('!!! CAUTION: Any future changes that should affect version ' +
                "'${project.buildParams.version}' " +
                "need to be committed into branch '${project.gitReleaseBranch}'.")
        }

        if (project.hasWipJiraIssues()) {
            util.warnBuild(ProjectMessagesUtil.generateWIPIssuesMessage(project))
        }

        // Dump a representation of the project
        logger.debug("---- ODS Project (${project.key}) data ----\r${project}\r -----")

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)

        // Fail the build in case of failing tests.
        if (project.hasFailingTests() || project.hasUnexecutedJiraTests()) {
            def message = 'Error: '

            if (project.hasFailingTests()) {
                message += 'found failing tests'
            }

            if (project.hasFailingTests() && project.hasUnexecutedJiraTests()) {
                message += ' and '
            }

            if (project.hasUnexecutedJiraTests()) {
                message += 'found unexecuted Jira tests'
            }

            message += '.'

            if (!project.isWorkInProgress) {
                bitbucket.setBuildStatus (steps.env.BUILD_URL, project.gitData.commit,
                    'FAILED', "Release Manager for commit: ${project.gitData.commit}")
            }

            util.failBuild(message)
            throw new IllegalStateException(message)
        } else {
            project.reportPipelineStatus()
            if (!project.isWorkInProgress) {
                bitbucket.setBuildStatus (steps.env.BUILD_URL, project.gitData.commit,
                    "SUCCESSFUL", "Release Manager for commit: ${project.gitData.commit}")
            }
        }
    }

    private void pushRepos(IPipelineSteps steps, GitService git) {
        def flattenedRepos = repos.flatten()
        def repoPushTasks = [ : ]
        def repoSize = flattenedRepos.size()
        for (def i = 0; i < repoSize; i++) {
            def repo = flattenedRepos[i]
            repoPushTasks << [ (repo.id): {
                steps.dir("${steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/${repo.id}") {
                    if (project.isWorkInProgress) {
                        git.pushRef(repo.branch)
                    } else if (project.isAssembleMode) {
                        git.createTag(project.targetTag)
                        git.pushBranchWithTags(project.gitReleaseBranch)
                    } else {
                        git.createTag(project.targetTag)
                        git.pushRef(project.targetTag)
                    }
                }
            }
            ]
        }
        repoPushTasks.failFast = true
        script.parallel(repoPushTasks)
    }

    private void gatherCreatedExecutionCommits(IPipelineSteps steps, GitService git) {
        def flattenedRepos = repos.flatten()
        def gatherCommitTasks = [ : ]
        def repoSize = flattenedRepos.size()
        for (def i = 0; i < repoSize; i++) {
            def repo = flattenedRepos[i]
            gatherCommitTasks << [ (repo.id): {
                steps.dir("${steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/${repo.id}") {
                    repo.data.git.createdExecutionCommit = git.commitSha
                    steps.echo "repo.id: ${repo.id}: ${repo.data.git.createdExecutionCommit}"
                }
            }
            ]
        }

        gatherCommitTasks.failFast = true
        script.parallel(gatherCommitTasks)
    }

    private void integrateIntoMainBranchRepos(IPipelineSteps steps, GitService git) {
        def flattenedRepos = repos.flatten()
        def repoIntegrateTasks = [ : ]
        def repoSize = flattenedRepos.size()
        for (def i = 0; i < repoSize; i++) {
            def repo = flattenedRepos[i]
            if (repo.type?.toLowerCase() != MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST &&
                repo.type?.toLowerCase() != MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_INFRA &&
                repo.type?.toLowerCase() != MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SAAS_SERVICE ) {
                repoIntegrateTasks << [ (repo.id): {
                    steps.dir("${steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/${repo.id}") {
                        def filesToCheckout = []
                        if (steps.fileExists('openshift')) {
                            filesToCheckout = ['openshift/ods-deployments.json']
                        } else {
                            filesToCheckout = [
                                'openshift-exported/ods-deployments.json',
                                'openshift-exported/template.yml'
                            ]
                        }
                        git.mergeIntoMainBranch(project.gitReleaseBranch, repo.branch, filesToCheckout)
                    }
                }
                ]
            }
        }
        repoIntegrateTasks.failFast = true
        script.parallel(repoIntegrateTasks)
    }

    private void recordAndPushEnvStateForReleaseManager(IPipelineSteps steps, ILogger logger, GitService git) {
        // record release manager repo state
        logger.debug "Finalize: Recording HEAD commits from repos ..."
        logger.debug "On release manager commit ${git.commitSha}"
        def flattenedRepos = repos.flatten()
        def gitHeads = [ : ]
        def repoSize = flattenedRepos.size()
        for (def i = 0; i < repoSize; i++) {
            def repo = flattenedRepos[i]
            logger.debug "HEAD of repo '${repo.id}': ${repo.data.git.createdExecutionCommit}"
            gitHeads << [ (repo.id): (repo.data.git.createdExecutionCommit ?: '')]
        }

        def envState = [
            version: project.buildParams.version,
            changeId: project.buildParams.changeId,
            repositories: gitHeads,
        ]
        steps.writeFile(
            file: project.envStateFileName,
            text: JsonOutput.prettyPrint(JsonOutput.toJson(envState))
        )

        def filesToCommit = [project.envStateFileName]
        def messageToCommit = "ODS: Record commits deployed into ${project.buildParams.targetEnvironmentToken}"
        if (! project.isWorkInProgress) {
            def projectDataFileNames =  project.saveProjectData()
            filesToCommit.addAll(projectDataFileNames)
            messageToCommit = messageToCommit + " and data of version ${project.getVersionName()}"
        }

        git.commit(
            filesToCommit,
            messageToCommit
        )

        if (project.isWorkInProgress) {
            git.pushRef('master')
        } else {
            // We don't need to merge, we simply commit the env file. That
            // avoids unnecessary merge conflicts.
            git.switchToOriginTrackingBranch('master')
            git.checkoutAndCommitFiles(
                project.gitReleaseBranch,
                filesToCommit,
                "ODS: Update ${project.buildParams.targetEnvironmentToken} env state"
            )
            git.pushRef('master')
            git.switchToExistingBranch(project.gitReleaseBranch)

            git.createTag(project.targetTag)
            git.pushBranchWithTags(project.gitReleaseBranch)
        }
    }

}
