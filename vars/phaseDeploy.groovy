import org.ods.service.ServiceRegistry
import org.ods.usecase.LeVaDocumentUseCase
import org.ods.util.MROPipelineUtil
import org.ods.util.PipelineUtil

def call(Map project, List<Set<Map>> repos) {
    def levaDoc = ServiceRegistry.instance.get(LeVaDocumentUseCase.class.name)
    def util    = ServiceRegistry.instance.get(PipelineUtil.class.name)

    def groups = util.prepareExecutePhaseForReposNamedJob(MROPipelineUtil.PipelinePhases.DEPLOY, repos, null) { steps, repo ->
        // TODO: ODS error handling

        // Create and store a Technical Installation Report document
        echo "Creating and archiving a Technical Installation Report for repo '${repo.id}'"
        repo.TIR = levaDoc.createTIR(steps.env.RELEASE_PARAM_VERSION, project, repo)
    }

    // Execute phase for groups of independent repos
    groups.each { group ->
        parallel(group)
    }
}

return this
