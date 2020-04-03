import hudson.Functions

import org.ods.scheduler.*
import org.ods.service.*
import org.ods.util.*

def call(Project project, List<Set<Map>> repos) {
    try {
        def steps = ServiceRegistry.instance.get(PipelineSteps)
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
        def os = ServiceRegistry.instance.get(OpenShiftService)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)
        def git = ServiceRegistry.instance.get(GitUtil)

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
                throw new RuntimeException("Error: target environment '${targetProject}' does not exist in OpenShift.")
            }
        }

        def agentCondition = project.isAssembleMode && repos.size() > 0
        runOnAgentPod(project, agentCondition) {
            // Execute phase for each repository
            util.prepareExecutePhaseForReposNamedJob(phase, repos, preExecuteRepo, postExecuteRepo)
                .each { group ->
                    group.failFast = true
                    parallel(group)
                }

            // record release manager repo state
            if (project.isAssembleMode && !project.isWorkInProgress) {
                util.tagAndPushBranch(project.gitReleaseBranch, project.targetTag)
            }
        }

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)

        // Dump a representation of the project
        steps.echo(" ---- ODS Project (${project.key}) data: \r${project.toString()}\r -----")

        if (project.isAssembleMode && !project.isWorkInProgress) {
            steps.echo("!!! CAUTION: Any future changes that should affect version '${project.buildParams.version}' need to be committed into branch '${project.gitReleaseBranch}'.")
        }

        // Fail the build in case of failing tests.
        if (project.hasFailingTests() || project.hasUnexecutedJiraTests()) {
            def message = "Error: "

            if (project.hasFailingTests()) {
                message += "found failing tests"
            }

            if (project.hasFailingTests() && project.hasUnexecutedJiraTests()) {
                message += " and "
            }

            if (project.hasUnexecutedJiraTests()) {
                message += "found unexecuted Jira tests"
            }

            message += "."
            util.failBuild(message)
            throw new IllegalStateException(message)
        } else {
            project.reportPipelineStatus()
        }
    } catch (e) {
        // Check for random null references which occur after a Jenkins restart
        if (ServiceRegistry.instance == null || ServiceRegistry.instance.get(PipelineSteps) == null) {
            e = new IllegalStateException("Error: invalid references have been detected for critical pipeline services. Most likely, your Jenkins instance has been recycled. Please re-run the pipeline!").initCause(e)
        }

        echo(e.message)

        try {
            project.reportPipelineStatus(e.message, true)
        } catch (reportError) {
            echo("Error: unable to report pipeline status because of: ${reportError.message}.")
            reportError.initCause(e)
            throw reportError
        }

        throw e
    }
}

return this

