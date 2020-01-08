import org.ods.scheduler.LeVADocumentScheduler
import org.ods.service.ServiceRegistry
import org.ods.util.MROPipelineUtil
import org.ods.util.PipelineUtil

def call(Map project, List<Set<Map>> repos) {
    def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler.class.name)
    def util             = ServiceRegistry.instance.get(PipelineUtil.class.name)

    def phase = MROPipelineUtil.PipelinePhases.DEPLOY

    def preExecuteRepo = { steps, repo ->
        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, project, repo)
    }

    def postExecuteRepo = { steps, repo ->
        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, project, repo)
    }

    levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START, project)

    // Execute phase for each repository
    util.prepareExecutePhaseForReposNamedJob(phase, repos, preExecuteRepo, postExecuteRepo)
        .each { group ->
            parallel(group)
        }

    levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, project)
}

return this
