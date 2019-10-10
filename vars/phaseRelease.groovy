import org.ods.service.ServiceRegistry
import org.ods.util.MROPipelineUtil
import org.ods.util.PipelineUtil

def call(Map project, List<Set<Map>> repos) {
    def util = ServiceRegistry.instance.get(PipelineUtil.class.name)

    // Execute phase for each repository
    util.prepareExecutePhaseForReposNamedJob(MROPipelineUtil.PipelinePhases.RELEASE, repos)
        .each { group ->
            parallel(group)
        }
}

return this
