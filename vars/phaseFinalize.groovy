import groovy.json.JsonOutput

import org.ods.scheduler.LeVADocumentScheduler
import org.ods.service.OpenShiftService
import org.ods.service.ServiceRegistry
import org.ods.util.MROPipelineUtil
import org.ods.util.PipelineUtil
import org.ods.util.GitUtil
import org.ods.util.Project

def call(Project project, List<Set<Map>> repos) {
    def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
    def os               = ServiceRegistry.instance.get(OpenShiftService)
    def util             = ServiceRegistry.instance.get(MROPipelineUtil)
    def git              = ServiceRegistry.instance.get(GitUtil)

    def phase = MROPipelineUtil.PipelinePhases.FINALIZE

    def preExecuteRepo = { steps, repo ->
        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
    }

    def postExecuteRepo = { steps, repo ->
        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, repo)
    }

    try {
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
                    parallel(group)
                }
        }

        // record release manager repo state
        if (project.isAssembleMode && !project.isWorkInProgress) {
            util.tagAndPushBranch(project.gitReleaseBranch, project.targetTag)
        }

        // Dump a representation of the project
        echo "Project ${project.toString()}"

        if (project.isAssembleMode && !project.isWorkInProgress) {
            echo "CAUTION: Any future changes that should affect version '${project.buildParams.version}' need to be committed into branch '${project.gitReleaseBranch}'."
        }

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)

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
        this.steps.echo(e.message)
        project.reportPipelineStatus(e)
        throw e
    }
}

return this

