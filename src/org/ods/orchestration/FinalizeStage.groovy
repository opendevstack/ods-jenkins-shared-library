package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.services.GitService
import org.ods.services.OpenShiftService
import org.ods.orchestration.scheduler.*
import org.ods.orchestration.service.*
import org.ods.orchestration.usecase.*
import org.ods.orchestration.util.*
import org.ods.services.BitbucketService
import org.ods.util.PipelineSteps

class FinalizeStage extends Stage {

    public final String STAGE_NAME = 'Finalize'

    FinalizeStage(def script, Project project, List<Set<Map>> repos) {
        super(script, project, repos)
    }

    @SuppressWarnings(['ParameterName', 'AbcMetric'])
    def run() {
        def steps = ServiceRegistry.instance.get(PipelineSteps)
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
        def os = ServiceRegistry.instance.get(OpenShiftService)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)
        def bitbucket = ServiceRegistry.instance.get(BitbucketService)

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
            if (!os.envExists()) {
                throw new RuntimeException(
                    "Error: target environment '${targetProject}' does not exist in OpenShift."
                )
            }
        }

        def agentCondition = project.isAssembleMode && repos.size() > 0
        runOnAgentPod(project, agentCondition) {
            // Execute phase for each repository
            Map repoGroups = util.prepareExecutePhaseForReposNamedJob(
                phase, repos, preExecuteRepo, postExecuteRepo).flatten()

            repoGroups.failFast = true
            script.parallel(repoGroups)

//                .each { group ->
//                    group.failFast = true
//                    script.parallel(group)
//                }

            // record release manager repo state
            if (project.isAssembleMode && !project.isWorkInProgress) {
                util.tagAndPushBranch(project.gitReleaseBranch, project.targetTag)
                // add the tag commit that was created for traceability ..
                GitService gitUtl = ServiceRegistry.instance.get(GitService)
                project.gitData.createdExecutionCommit = gitUtl.commitSha
            }
        }

        if (project.isAssembleMode && !project.isWorkInProgress) {
            steps.echo("!!! CAUTION: Any future changes that should affect version '${project.buildParams.version}' " +
                "need to be committed into branch '${project.gitReleaseBranch}'.")
        }

        // Dump a representation of the project
        steps.echo(" ---- ODS Project (${project.key}) data ----\r${project.toString()}\r -----")

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

            message += "."

            bitbucket.setBuildStatus (steps.env.BUILD_URL, project.gitData.commit,
                "FAILURE", "Release Manager for commit: ${project.gitData.commit}")

            util.failBuild(message)
            throw new IllegalStateException(message)
        } else {
            project.reportPipelineStatus()
            bitbucket.setBuildStatus (steps.env.BUILD_URL, project.gitData.commit,
                "SUCCESSFUL", "Release Manager for commit: ${project.gitData.commit}")
        }
    }

}
