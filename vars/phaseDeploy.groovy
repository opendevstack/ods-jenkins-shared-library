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

    def groups = util.prepareExecutePhaseForReposNamedJob(PipelinePhases.DEPLOY_PHASE, repos, null) { script, repo ->
        // Create and store a Technical Installation Report document
        echo "Creating and archiving a Technical Installation Report for ${repo.id}"
        levaDoc.createTIR(script.env.version, project, repo)
    }

    // Execute phase for groups of independent repos
    groups.each { group ->
        parallel(group)
    }
}

return this
