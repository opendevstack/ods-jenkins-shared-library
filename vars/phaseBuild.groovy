import org.ods.phase.PipelinePhases
import org.ods.util.MultiRepoOrchestrationPipelineUtil

def call(Map metadata, List<Set<Map>> repos) {
    // Execute phase for each repository
    def util = new MultiRepoOrchestrationPipelineUtil(this)
    util.prepareExecutePhaseForReposNamedJob(PipelinePhases.BUILD_PHASE, repos)
        .each { group ->
            parallel(group)
        }
}

return this
