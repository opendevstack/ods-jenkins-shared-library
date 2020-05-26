package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.services.GitService
import org.ods.services.OpenShiftService
import org.ods.orchestration.scheduler.*
import org.ods.orchestration.util.*
import org.ods.services.BitbucketService
import org.ods.util.PipelineSteps

class FinalizeStage extends Stage {

    public final String STAGE_NAME = 'Finalize'

    FinalizeStage(def script, Context context, List<Set<Map>> repos) {
        super(script, context, repos)
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

        if (context.isAssembleMode) {
            // Check if the target environment exists in OpenShift
            def targetProject = context.targetProject
            if (!os.envExists()) {
                throw new RuntimeException(
                    "Error: target environment '${targetProject}' does not exist in OpenShift."
                )
            }
        }

        def agentCondition = context.isAssembleMode && repos.size() > 0
        runOnAgentPod(context, agentCondition) {
            // Execute phase for each repository
            util.prepareExecutePhaseForReposNamedJob(phase, repos, preExecuteRepo, postExecuteRepo)
                .each { group ->
                    group.failFast = true
                    script.parallel(group)
                }

            // record release manager repo state
            if (context.isAssembleMode && !context.isWorkInProgress) {
                util.tagAndPushBranch(context.gitReleaseBranch, context.targetTag)
                // add the tag commit that was created for traceability ..
                GitService gitUtl = ServiceRegistry.instance.get(GitService)
                context.gitData.createdExecutionCommit = gitUtl.commitSha
            }
        }

        if (context.isAssembleMode && !context.isWorkInProgress) {
            steps.echo("!!! CAUTION: Any future changes that should affect version '${context.buildParams.version}' " +
                "need to be committed into branch '${context.gitReleaseBranch}'.")
        }

        // Dump a representation of the project
        steps.echo(" ---- ODS Project (${context.key}) data ----\r${context.toString()}\r -----")

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)

        // Fail the build in case of failing tests.
        if (context.hasFailingTests() || context.hasUnexecutedJiraTests()) {
            def message = 'Error: '

            if (context.hasFailingTests()) {
                message += 'found failing tests'
            }

            if (context.hasFailingTests() && context.hasUnexecutedJiraTests()) {
                message += ' and '
            }

            if (context.hasUnexecutedJiraTests()) {
                message += 'found unexecuted Jira tests'
            }

            message += "."

            bitbucket.setBuildStatus (steps.env.BUILD_URL, context.gitData.commit,
                "FAILURE", "Release Manager for commit: ${context.gitData.commit}")

            util.failBuild(message)
            throw new IllegalStateException(message)
        } else {
            context.reportPipelineStatus()
            bitbucket.setBuildStatus (steps.env.BUILD_URL, context.gitData.commit,
                "SUCCESSFUL", "Release Manager for commit: ${context.gitData.commit}")
        }
    }

}
