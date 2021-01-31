package org.ods.orchestration

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import org.ods.services.ServiceRegistry
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.orchestration.phases.FinalizeOdsComponent
import org.ods.services.BitbucketService
import org.ods.services.OpenShiftService
import org.ods.services.GitService

import groovy.json.JsonOutput

@TypeChecked
class FinalizeStage extends Stage {

    public final String STAGE_NAME = 'Finalize'

    FinalizeStage(def script, Project project, List<Set<Map>> repos) {
        super(script, project, repos)
    }

    @SuppressWarnings(['AbcMetric', 'MethodSize', 'CyclomaticComplexity'])
    def run() {
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
        def os = ServiceRegistry.instance.get(OpenShiftService)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)
        def bitbucket = ServiceRegistry.instance.get(BitbucketService)
        def git = ServiceRegistry.instance.get(GitService)

        def phase = MROPipelineUtil.PipelinePhases.FINALIZE

        Closure preExecuteRepo = { Map repo ->
            levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
        }

        Closure executeRepo = { Map repo ->
            switch (util.repoType(repo)) {
                case MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE:
                case MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SERVICE:
                    if (this.project.isAssembleMode) {
                        new FinalizeOdsComponent(this.project, this.steps, git, this.logger).run(repo)
                    } else {
                        util.logRepoSkip(phase, repo)
                    }
                    break
                case MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST:
                    util.logRepoSkip(phase, repo)
                    break
                default:
                    util.runCustomInstructionsForPhaseOrSkip(phase, repo)
                    break
            }
        }

        Closure postExecuteRepo = { Map repo ->
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
            util.executeRepoGroups(repos, executeRepo, preExecuteRepo, postExecuteRepo)

            if (project.isAssembleMode && !project.isWorkInProgress) {
                integrateIntoMainBranch(git)
            }

            gatherCreatedExecutionCommits(git)

            if (!project.buildParams.rePromote) {
                pushRepos(git)
                recordAndPushEnvState(git)
            }

            // add the tag commit that was created for traceability ..
            this.logger.debug "Current release manager commit: ${project.gitData.commit}"
            project.gitData.createdExecutionCommit = git.commitSha
        }

        if (project.isAssembleMode && !project.isWorkInProgress) {
            this.logger.warn('!!! CAUTION: Any future changes that should affect version ' +
                "'${project.buildParams.version}' " +
                "need to be committed into branch '${project.gitReleaseBranch}'.")
        }

        // Dump a representation of the project
        this.logger.debug("---- ODS Project (${project.key}) data ----\r${project.toString()}\r -----")

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
                bitbucket.setBuildStatus(
                    this.steps.env.BUILD_URL as String,
                    project.gitData.commit as String,
                    'FAILED', "Release Manager for commit: ${project.gitData.commit}",
                )
            }

            util.failBuild(message)
            throw new IllegalStateException(message)
        } else {
            project.reportPipelineStatus()
            if (!project.isWorkInProgress) {
                bitbucket.setBuildStatus(
                    this.steps.env.BUILD_URL as String,
                    project.gitData.commit as String,
                    "SUCCESSFUL", "Release Manager for commit: ${project.gitData.commit}",
                )
            }
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private void pushRepos(GitService git) {
        def flattenedRepos = repos.flatten()
        def repoPushTasks = flattenedRepos.collectEntries { repo ->
            [
                (repo.id): {
                    this.steps.dir("${this.steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/${repo.id}") {
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
        this.steps.parallel(repoPushTasks)
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private void gatherCreatedExecutionCommits(GitService git) {
        def flattenedRepos = repos.flatten()
        def gatherCommitTasks = flattenedRepos.collectEntries { repo ->
            [
                (repo.id): {
                    this.steps.dir("${this.steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/${repo.id}") {
                        if (!repo.data.git) {
                            repo.data.git = [:]
                        }
                        repo.data.git.createdExecutionCommit = git.commitSha
                    }
                }
            ]
        }
        gatherCommitTasks.failFast = true
        this.steps.parallel(gatherCommitTasks)
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private void integrateIntoMainBranch(GitService git) {
        def flattenedRepos = repos.flatten()
        def repoIntegrateTasks = flattenedRepos
            .findAll { it.type?.toLowerCase() != MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST }
            .collectEntries { repo ->
                [
                    (repo.id): {
                        this.steps.dir("${this.steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/${repo.id}") {
                            List<String> filesToCheckout = []
                            if (this.steps.fileExists('openshift')) {
                                filesToCheckout = ['openshift/ods-deployments.json']
                            } else {
                                filesToCheckout = [
                                    'openshift-exported/ods-deployments.json',
                                    'openshift-exported/template.yml'
                                ]
                            }
                            git.mergeIntoMainBranch(
                                project.gitReleaseBranch,
                                repo.branch as String,
                                filesToCheckout,
                            )
                        }
                    }
                ]
        }
        repoIntegrateTasks.failFast = true
        this.steps.parallel(repoIntegrateTasks)
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private void recordAndPushEnvState(GitService git) {
        // record release manager repo state
        this.logger.debug "Finalize: Recording HEAD commits from repos ..."
        this.logger.debug "On release manager commit ${git.commitSha}"
        def gitHeads = repos.flatten().collectEntries { repo ->
            this.logger.debug "HEAD of repo '${repo.id}': ${repo.data.git?.createdExecutionCommit}"
            [(repo.id): (repo.data.git?.createdExecutionCommit ?: '')]
        }
        def envState = [
            version: project.buildParams.version,
            changeId: project.buildParams.changeId,
            repositories: gitHeads,
        ]
        this.steps.writeFile(
            file: project.envStateFileName,
            text: JsonOutput.prettyPrint(JsonOutput.toJson(envState))
        )
        git.commit(
            [project.envStateFileName],
            "ODS: Record commits deployed into ${project.buildParams.targetEnvironmentToken}"
        )

        if (project.isWorkInProgress) {
            git.pushRef('master')
        } else {
            // We don't need to merge, we simply commit the env file. That
            // avoids unnecessary merge conflicts.
            git.switchToOriginTrackingBranch('master')
            git.checkoutAndCommitFiles(
                project.gitReleaseBranch,
                [project.envStateFileName],
                "ODS: Update ${project.buildParams.targetEnvironmentToken} env state"
            )
            git.pushRef('master')
            git.switchToExistingBranch(project.gitReleaseBranch)

            git.createTag(project.targetTag)
            git.pushBranchWithTags(project.gitReleaseBranch)
        }
    }

}
