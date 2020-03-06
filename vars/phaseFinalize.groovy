import groovy.json.JsonOutput

import org.ods.scheduler.LeVADocumentScheduler
import org.ods.service.OpenShiftService
import org.ods.service.ServiceRegistry
import org.ods.util.MROPipelineUtil
import org.ods.util.PipelineUtil
import org.ods.util.Project

def call(Project project, List<Set<Map>> repos) {
    def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
    def os               = ServiceRegistry.instance.get(OpenShiftService)
    def util             = ServiceRegistry.instance.get(MROPipelineUtil)

    def phase = MROPipelineUtil.PipelinePhases.FINALIZE

    echo "Finalizing deployment of project '${project.key}' into environment '${env.MULTI_REPO_ENV}'"

    // Check if the target environment exists in OpenShift
    def environment = "${project.key}-${env.MULTI_REPO_ENV}".toLowerCase()
    if (!os.envExists(environment)) {
        throw new RuntimeException("Error: target environment '${environment}' does not exist in OpenShift.")
    }

    project.gitData.location = os.exportProject(env.MULTI_REPO_ENV, project.key.toLowerCase(), env.RELEASE_PARAM_CHANGE_ID)

    // Dump a representation of the project
    echo "Project ${JsonOutput.toJson(project)}"

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
    }
}

return this

