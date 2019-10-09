@Grab(group="com.konghq", module="unirest-java", version="2.3.08", classifier="standalone")

import kong.unirest.Unirest

import org.ods.service.DocGenService
import org.ods.service.JenkinsService
import org.ods.service.JiraService
import org.ods.service.LeVaDocumentChaptersFileService
import org.ods.service.NexusService
import org.ods.service.OpenShiftService
import org.ods.service.ServiceRegistry
import org.ods.usecase.JUnitTestReportsUseCase
import org.ods.usecase.JiraUseCase
import org.ods.usecase.LeVaDocumentUseCase
import org.ods.usecase.SonarQubeUseCase
import org.ods.util.GitUtil
import org.ods.util.MROPipelineUtil
import org.ods.util.PDFUtil
import org.ods.util.PipelineSteps
import org.ods.util.PipelineUtil

def call() {
    Unirest.config()
        .socketTimeout(600000)
        .connectTimeout(60000)

    def steps = new PipelineSteps(this)
    def git = new GitUtil(steps)
    def util = new MROPipelineUtil(steps, git)

    // Gather metadata
    def project = util.loadProjectMetadata()
    def repos = project.repositories

    // Configure current build
    currentBuild.description = "Build #${BUILD_NUMBER} - Change: ${env.RELEASE_PARAM_CHANGE_ID}, Project: ${project.id}, Target Environment: ${project.id}-${env.MULTI_REPO_ENV}"

    // Register global services
    def registry = ServiceRegistry.instance
    registry.add(GitUtil.class.name, git)
    registry.add(PDFUtil.class.name, new PDFUtil())
    registry.add(PipelineSteps.class.name, steps)
    registry.add(PipelineUtil.class.name, util)

    registry.add(DocGenService.class.name,
        new DocGenService(env.DOCGEN_URL)
    )

    registry.add(LeVaDocumentChaptersFileService.class.name,
        new LeVaDocumentChaptersFileService(steps)
    )

    registry.add(JenkinsService.class.name,
        new JenkinsService(
            registry.get(PipelineSteps.class.name)
        )
    )

    if (project.services.jira) {
        withCredentials([ usernamePassword(credentialsId: project.services.jira.credentials.id, usernameVariable: "JIRA_USERNAME", passwordVariable: "JIRA_PASSWORD") ]) {
            registry.add(JiraService.class.name,
                new JiraService(
                    env.JIRA_URL,
                    env.JIRA_USERNAME,
                    env.JIRA_PASSWORD
                )
            )
        }
    }

    registry.add(NexusService.class.name,
        new NexusService(
            env.NEXUS_URL,
            env.NEXUS_USERNAME,
            env.NEXUS_PASSWORD
        )
    )

    withCredentials([ usernamePassword(credentialsId: project.services.bitbucket.credentials.id, usernameVariable: "BITBUCKET_USER", passwordVariable: "BITBUCKET_PW") ]) {
        registry.add(OpenShiftService.class.name,
            new OpenShiftService(
                registry.get(PipelineSteps.class.name),
                env.OPENSHIFT_API_URL,
                env.BITBUCKET_HOST,
                env.BITBUCKET_USER,
                env.BITBUCKET_PW
            )
        )
    }

    registry.add(JiraUseCase.class.name,
        new JiraUseCase(
            registry.get(PipelineSteps.class.name),
            registry.get(JiraService.class.name)
        )
    )

    registry.add(JUnitTestReportsUseCase.class.name,
        new JUnitTestReportsUseCase(
            registry.get(PipelineSteps.class.name)
        )
    )

    registry.add(LeVaDocumentUseCase.class.name,
        new LeVaDocumentUseCase(
            registry.get(PipelineSteps.class.name),
            registry.get(PipelineUtil.class.name),
            registry.get(DocGenService.class.name),
            registry.get(JenkinsService.class.name),
            registry.get(JiraUseCase.class.name),
            registry.get(LeVaDocumentChaptersFileService.class.name),
            registry.get(NexusService.class.name),
            registry.get(OpenShiftService.class.name),
            registry.get(PDFUtil.class.name)
        )
    )

    registry.add(SonarQubeUseCase.class.name,
        new SonarQubeUseCase(
            registry.get(PipelineSteps.class.name),
            registry.get(NexusService.class.name)
        )
    )

    // Checkout repositories into the workspace
    parallel(util.prepareCheckoutReposNamedJob(repos) { steps_, repo ->
        echo "Repository: ${repo}"
        echo "Environment configuration: ${env.getEnvironment()}"
    })

    // Load pipeline configs from each repo's .pipeline-config.yml
    util.loadPipelineConfigs(repos)

    // Compute groups of repository configs for convenient parallelization
    repos = util.computeRepoGroups(repos)

    return [ project: project, repos: repos ]
}

return this
