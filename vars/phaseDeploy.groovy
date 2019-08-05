import org.ods.phase.PipelinePhases
import org.ods.util.MultiRepoOrchestrationPipelineUtil
import org.ods.service.DocGenService
import org.ods.service.JiraService
import org.ods.service.NexusService
import org.ods.usecase.JiraUseCase
import org.ods.usecase.LeVaDocumentUseCase

def call(Map project, List<Set<Map>> repos) {
    def pipelineUtil = new MultiRepoOrchestrationPipelineUtil(this)

    def docGen = new DocGenService(env.DOCGEN_URL)
    def nexus = new NexusService(env.NEXUS_URL, env.NEXUS_USERNAME, env.NEXUS_PASSWORD)

    def jira
    def jiraService
    withCredentials([ usernamePassword(credentialsId: project.services.jira.credentials.id, usernameVariable: "JIRA_USERNAME", passwordVariable: "JIRA_PASSWORD") ]) {
        jiraService = new JiraService(env.JIRA_URL, env.JIRA_USERNAME, env.JIRA_PASSWORD)
        jira = new JiraUseCase(this, jiraService)
    }

    def levaDocs = new LeVaDocumentUseCase(this, docGen, jiraService, nexus, pipelineUtil)

    // Execute phase for each repository
    pipelineUtil.prepareExecutePhaseForReposNamedJob(PipelinePhases.DEPLOY_PHASE, repos)
        .each { group ->
            parallel(group)

            group.each { repoId, _ ->
                def repo = project.repositories.find { it.id == repoId }

                // Create and store a Technical Installation Report document
                levaDocs.createTIR("0.1", project, repo)
            }
        }
}

return this
