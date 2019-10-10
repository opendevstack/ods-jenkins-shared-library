import org.ods.service.ServiceRegistry
import org.ods.util.MROPipelineUtil
import org.ods.util.PipelineUtil

def call(Map project, List<Set<Map>> repos) {
    def util = ServiceRegistry.instance.get(PipelineUtil.class.name)
    
    util.prepareExecutePhaseForReposNamedJob(MROPipelineUtil.PipelinePhases.TEST, repos)
        .each { group ->
            parallel(group)
        }
}

return this
