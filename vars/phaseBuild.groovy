import org.ods.phase.PipelinePhases
import org.ods.service.ServiceRegistry
import org.ods.util.PipelineUtil

def call(Map project, List<Set<Map>> repos) {
    def util = ServiceRegistry.instance.get(PipelineUtil.class.name)

    // Execute phase for each repository
    util.prepareExecutePhaseForReposNamedJob(PipelinePhases.BUILD_PHASE, repos)
        .each { group ->
            parallel(group)
        }
}

return this
