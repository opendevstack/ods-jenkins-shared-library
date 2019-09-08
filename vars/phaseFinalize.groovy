import org.ods.service.ServiceRegistry
import org.ods.util.PipelineUtil

def call(Map project, List<Set<Map>> repos) {
    def util = ServiceRegistry.instance.get(PipelineUtil.class.name)

    echo "Finalizing deployment of project '${project.id}'"
}

return this
