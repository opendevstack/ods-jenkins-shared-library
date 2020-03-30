import org.ods.scheduler.LeVADocumentScheduler
import org.ods.service.ServiceRegistry
import org.ods.util.MROPipelineUtil
import org.ods.util.Project

def call(Project project, List<Set<Map>> repos) {
    def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
    def util             = ServiceRegistry.instance.get(MROPipelineUtil)

    def phase = MROPipelineUtil.PipelinePhases.RELEASE

    def preExecuteRepo = { steps, repo ->
        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
    }

    def postExecuteRepo = { steps, repo ->
        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, repo)
    }

    try {
        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START)

        util.prepareExecutePhaseForReposNamedJob(phase, repos, preExecuteRepo, postExecuteRepo)
            .each { group ->
                parallel(group)
            }

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)
    } catch (e) {
        this.steps.echo(e.message)
        try {
            project.reportPipelineStatus(e)
        } catch (reportError) {
            this.steps.echo("Error: Found a second error while trying to report the pipeline status with ${reportError.message}")
        }
        throw e
    }
}

return this
