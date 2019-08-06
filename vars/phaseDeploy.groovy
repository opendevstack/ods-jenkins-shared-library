import org.ods.phase.PipelinePhases
import org.ods.service.DocGenService
import org.ods.service.JiraService
import org.ods.service.NexusService
import org.ods.service.ServiceRegistry
import org.ods.usecase.JiraUseCase
import org.ods.usecase.LeVaDocumentUseCase
import org.ods.util.PipelineUtil

def call(Map project, List<Set<Map>> repos) {
    def docGen  = ServiceRegistry.instance.get(DocGenService.class.name)
    def jira    = ServiceRegistry.instance.get(JiraUseCase.class.name)
    def levaDoc = ServiceRegistry.instance.get(LeVaDocumentUseCase.class.name)
    def nexus   = ServiceRegistry.instance.get(NexusService.class.name)
    def util    = ServiceRegistry.instance.get(PipelineUtil.class.name)

    // Execute phase for each repository
    util.prepareExecutePhaseForReposNamedJob(PipelinePhases.DEPLOY_PHASE, repos)
        .each { group ->
            parallel(group)

            group.each { repoId, _ ->
                def repo = project.repositories.find { it.id == repoId }

                // Create and store a Technical Installation Report document
                levaDoc.createTIR("0.1", project, repo)
            }
        }
}

return this
