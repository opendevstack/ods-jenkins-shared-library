import org.ods.service.DocGenService
import org.ods.service.JiraService
import org.ods.service.NexusService
import org.ods.service.ServiceRegistry
import org.ods.usecase.JUnitTestReportsUseCase
import org.ods.usecase.JiraUseCase
import org.ods.usecase.LeVaDocumentUseCase
import org.ods.util.PipelineUtil
import org.ods.util.MultiRepoOrchestrationPipelineUtil

def call() {
    // Gather metadata
    def util = new MultiRepoOrchestrationPipelineUtil(this)
    def project = util.readProjectMetadata()
    def repos = project.repositories

    // Register services
    def registry = ServiceRegistry.instance
    registry.add(PipelineUtil.class.name, util)

    registry.add(DocGenService.class.name,
        new DocGenService(env.DOCGEN_URL)
    )

    withCredentials([ usernamePassword(credentialsId: project.services.jira.credentials.id, usernameVariable: "JIRA_USERNAME", passwordVariable: "JIRA_PASSWORD") ]) {
        registry.add(JiraService.class.name,
            new JiraService(env.JIRA_URL, env.JIRA_USERNAME, env.JIRA_PASSWORD)
        )
    }

    registry.add(JiraUseCase.class.name,
        new JiraUseCase(this, registry.get(JiraService.class.name))
    )

    registry.add(JUnitTestReportsUseCase.class.name,
        new JUnitTestReportsUseCase(this)
    )

    registry.add(NexusService.class.name,
        new NexusService(env.NEXUS_URL, env.NEXUS_USERNAME, env.NEXUS_PASSWORD)
    )

    registry.add(LeVaDocumentUseCase.class.name,
        new LeVaDocumentUseCase(this,
            registry.get(DocGenService.class.name),
            registry.get(JiraService.class.name),
            registry.get(NexusService.class.name),
            registry.get(PipelineUtil.class.name)
        )
    )

    // Checkout repositories into the workspace
    parallel(util.prepareCheckoutReposNamedJobs(repos))

    // Load pipeline configs from each repo's .pipeline-config.yml
    util.readPipelineConfigs(repos)

    // Compute groups of repository configs for convenient parallelization
    repos = util.computeRepoGroups(repos)

    return [ project: project, repos: repos ]
}

return this
