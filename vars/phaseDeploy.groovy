import org.ods.service.ServiceRegistry
import org.ods.usecase.LeVaDocumentUseCase
import org.ods.util.MROPipelineUtil
import org.ods.util.PipelineUtil

def call(Map project, List<Set<Map>> repos) {
    def levaDoc = ServiceRegistry.instance.get(LeVaDocumentUseCase.class.name)
    def util    = ServiceRegistry.instance.get(PipelineUtil.class.name)

    def phase = MROPipelineUtil.PipelinePhases.DEPLOY

    if (LeVaDocumentUseCase.appliesToProject(project, LeVaDocumentUseCase.DocumentTypes.TIP, phase)) {
        echo "Creating and archiving a Technical Installation Plan for project '${project.id}'"
        levaDoc.createTIP(project)
    }

    def postExecute = { steps, repo ->
        if (LeVaDocumentUseCase.appliesToRepo(repo, LeVaDocumentUseCase.DocumentTypes.TIR, phase)) {
            echo "Creating and archiving a Technical Installation Report for repo '${repo.id}'"
            levaDoc.createTIR(project, repo)
        }
    }

    // Execute phase for each repository
    util.prepareExecutePhaseForReposNamedJob(phase, repos, null, postExecute)
        .each { group ->
            parallel(group)
        }
}

return this
