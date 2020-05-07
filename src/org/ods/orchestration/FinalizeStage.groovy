package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.orchestration.scheduler.*
import org.ods.orchestration.service.*
import org.ods.orchestration.usecase.*
import org.ods.orchestration.util.*

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
        runOnAgentPod(project, agentCondition) {
            // Execute phase for each repository
            util.prepareExecutePhaseForReposNamedJob(phase, repos, preExecuteRepo, postExecuteRepo)
                .each { group ->
                    group.failFast = true
                    script.parallel(group)
                }

            // record release manager repo state
            if (project.isAssembleMode && !project.isWorkInProgress) {
                util.tagAndPushBranch(project.gitReleaseBranch, project.targetTag)
                // add the tag commit that was created for traceability ..
                GitUtil gitUtl = ServiceRegistry.instance.get(GitUtil)
                project.getGitData.createdExecutionCommit = gitUtl.commit
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

            message += '.'
            util.failBuild(message)
            throw new IllegalStateException(message)
        } else {
            project.reportPipelineStatus()
        }
    }

}
