import org.ods.scheduler.LeVADocumentScheduler
import org.ods.service.OpenShiftService
import org.ods.service.ServiceRegistry
import org.ods.util.MROPipelineUtil

def call(Map project, List<Set<Map>> repos) {
    def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler.class.name)
    def os               = ServiceRegistry.instance.get(OpenShiftService.class.name)

    def phase = MROPipelineUtil.PipelinePhases.FINALIZE

    echo "Finalizing deployment of project '${project.id}' into environment '${env.MULTI_REPO_ENV}'"

    // Check if the target environment exists in OpenShift
    def environment = "${project.id}-${env.MULTI_REPO_ENV}".toLowerCase()
    if (!os.envExists(environment)) {
        throw new RuntimeException("Error: target environment '${environment}' does not exist in OpenShift.")
    }

    project.data.gitLocation = os.exportProject(env.MULTI_REPO_ENV, project.id.toLowerCase(), env.RELEASE_PARAM_CHANGE_ID)

    echo "Project ${project}"

    levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, project)
}

return this

