import org.ods.service.ServiceRegistry
import org.ods.usecase.LeVaDocumentUseCase
import org.ods.util.MROPipelineUtil
import org.ods.util.PipelineUtil

def call(Map project, List<Set<Map>> repos) {
    def levaDoc = ServiceRegistry.instance.get(LeVaDocumentUseCase.class.name)
    def util    = ServiceRegistry.instance.get(PipelineUtil.class.name)

    echo "Creating and archiving a Technical Installation Plan for project '${project.id}'"
    levaDoc.createTIP(project)

    def groups = util.prepareExecutePhaseForReposNamedJob(MROPipelineUtil.PipelinePhases.DEPLOY, repos, null) { steps, repo ->
        echo "Creating and archiving a Technical Installation Report for repo '${repo.id}'"
        levaDoc.createTIR(project, repo)
    }

    // Execute phase for groups of independent repos
    groups.each { group ->
        parallel(group)
    }
}

return this
