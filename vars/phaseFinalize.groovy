import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

import org.ods.scheduler.LeVADocumentScheduler
import org.ods.service.OpenShiftService
import org.ods.service.ServiceRegistry
import org.ods.util.MROPipelineUtil
import org.ods.util.PipelineUtil

def call(Map project, List<Set<Map>> repos) {
    def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
    def os               = ServiceRegistry.instance.get(OpenShiftService)
    def util             = ServiceRegistry.instance.get(MROPipelineUtil)

    def phase = MROPipelineUtil.PipelinePhases.FINALIZE

    echo "Finalizing deployment of project '${project.id}' into environment '${env.MULTI_REPO_ENV}'"

    // Check if the target environment exists in OpenShift
    def environment = "${project.id}-${env.MULTI_REPO_ENV}".toLowerCase()
    if (!os.envExists(environment)) {
        throw new RuntimeException("Error: target environment '${environment}' does not exist in OpenShift.")
    }

    project.data.gitLocation = os.exportProject(env.MULTI_REPO_ENV, project.id.toLowerCase(), env.RELEASE_PARAM_CHANGE_ID)

    // Dump a simple representation of the project
    def simpleProject = new JsonSlurperClassic().parseText(JsonOutput.toJson(project)) // clone
    simpleProject.repositories.each { repo ->
        repo.data.documents = []
    }

    echo "Project ${simpleProject}"

    levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, project)

    // Fail the build in case of failing tests.
    if (project.data.build.hasFailingTests) {
        util.failBuild("Error: found failing tests.")
    }
}

return this

