@Grab(group="com.konghq", module="unirest-java", version="2.4.03", classifier="standalone")

import java.nio.file.Paths

import kong.unirest.Unirest

import org.ods.scheduler.LeVADocumentScheduler
import org.ods.service.DocGenService
import org.ods.service.JenkinsService
import org.ods.service.JiraService
import org.ods.service.JiraZephyrService
import org.ods.service.LeVADocumentChaptersFileService
import org.ods.service.NexusService
import org.ods.service.OpenShiftService
import org.ods.service.ServiceRegistry
import org.ods.usecase.JUnitTestReportsUseCase
import org.ods.usecase.JiraUseCase
import org.ods.usecase.JiraUseCaseSupport
import org.ods.usecase.JiraUseCaseZephyrSupport
import org.ods.usecase.LeVADocumentUseCase
import org.ods.usecase.SonarQubeUseCase
import org.ods.util.GitUtil
import org.ods.util.MROPipelineUtil
import org.ods.util.PDFUtil
import org.ods.util.PipelineSteps
import org.ods.util.PipelineUtil
import org.ods.util.Project

def call() {
    Unirest.config()
        .socketTimeout(1200000)
        .connectTimeout(120000)

    def steps = new PipelineSteps(this)
    def git = new GitUtil(steps)
    def project = new Project(steps, git).load()
    def repos = project.repositories
    def util = new MROPipelineUtil(project, steps)

    // Configure current build
    currentBuild.description = "Build #${BUILD_NUMBER} - Change: ${env.RELEASE_PARAM_CHANGE_ID}, Project: ${project.key}, Target Environment: ${project.key}-${env.MULTI_REPO_ENV}"

    // Register global services
    def registry = ServiceRegistry.instance
    registry.add(GitUtil, git)
    registry.add(PDFUtil, new PDFUtil())
    registry.add(PipelineSteps, steps)
    registry.add(MROPipelineUtil, util)
    registry.add(Project, project)

    registry.add(DocGenService,
        new DocGenService(env.DOCGEN_URL)
    )

    registry.add(LeVADocumentChaptersFileService,
        new LeVADocumentChaptersFileService(steps)
    )

    registry.add(JenkinsService,
        new JenkinsService(
            registry.get(PipelineSteps)
        )
    )

    if (project.services?.jira) {
        withCredentials([ usernamePassword(credentialsId: project.services.jira.credentials.id, usernameVariable: "JIRA_USERNAME", passwordVariable: "JIRA_PASSWORD") ]) {
            registry.add(JiraService,
                new JiraService(
                    env.JIRA_URL,
                    env.JIRA_USERNAME,
                    env.JIRA_PASSWORD
                )
            )

            if (hasCapability(project.capabilities, "Zephyr")) {
                registry.add(JiraZephyrService,
                    new JiraZephyrService(
                        env.JIRA_URL,
                        env.JIRA_USERNAME,
                        env.JIRA_PASSWORD
                    )
                )
            }
        }
    }

    registry.add(NexusService,
        new NexusService(
            env.NEXUS_URL,
            env.NEXUS_USERNAME,
            env.NEXUS_PASSWORD
        )
    )

    withCredentials([ usernamePassword(credentialsId: project.services.bitbucket.credentials.id, usernameVariable: "BITBUCKET_USER", passwordVariable: "BITBUCKET_PW") ]) {
        registry.add(OpenShiftService,
            new OpenShiftService(
                registry.get(PipelineSteps),
                env.OPENSHIFT_API_URL,
                env.BITBUCKET_HOST,
                env.BITBUCKET_USER,
                env.BITBUCKET_PW
            )
        )
    }

    def jiraUseCase = new JiraUseCase(
        registry.get(Project),
        registry.get(PipelineSteps),
        registry.get(MROPipelineUtil),
        registry.get(JiraService)
    )

    jiraUseCase.setSupport(
        hasCapability(project.capabilities, "Zephyr")
            ? new JiraUseCaseZephyrSupport(project, steps, jiraUseCase, registry.get(JiraZephyrService), registry.get(MROPipelineUtil))
            : new JiraUseCaseSupport(project, steps, jiraUseCase)
    )

    registry.add(JiraUseCase, jiraUseCase)

    registry.add(JUnitTestReportsUseCase,
        new JUnitTestReportsUseCase(
            registry.get(Project),
            registry.get(PipelineSteps),
            registry.get(MROPipelineUtil)
        )
    )

    registry.add(SonarQubeUseCase,
        new SonarQubeUseCase(
            registry.get(Project),
            registry.get(PipelineSteps),
            registry.get(NexusService)
        )
    )

    registry.add(LeVADocumentUseCase,
        new LeVADocumentUseCase(
            registry.get(Project),
            registry.get(PipelineSteps),
            registry.get(MROPipelineUtil),
            registry.get(DocGenService),
            registry.get(JenkinsService),
            registry.get(JiraUseCase),
            registry.get(LeVADocumentChaptersFileService),
            registry.get(NexusService),
            registry.get(OpenShiftService),
            registry.get(PDFUtil),
            registry.get(SonarQubeUseCase)
        )
    )

    registry.add(LeVADocumentScheduler,
        new LeVADocumentScheduler(
            registry.get(Project),
            registry.get(PipelineSteps),
            registry.get(MROPipelineUtil),
            registry.get(LeVADocumentUseCase)
        )
    )

    def phase = MROPipelineUtil.PipelinePhases.INIT

    // Clean workspace from previous runs
    [PipelineUtil.ARTIFACTS_BASE_DIR, PipelineUtil.SONARQUBE_BASE_DIR, PipelineUtil.XUNIT_DOCUMENTS_BASE_DIR, MROPipelineUtil.REPOS_BASE_DIR].each { name ->
       echo "Cleaning workspace directory '${name}' from previous runs"
       Paths.get(env.WORKSPACE, name).toFile().deleteDir()
    }

    // Checkout repositories into the workspace
    parallel(util.prepareCheckoutReposNamedJob(repos) { steps_, repo ->
        echo "Repository: ${repo}"
        echo "Environment configuration: ${env.getEnvironment()}"
    })

    // Load configs from each repo's release-manager.yml
    util.loadPipelineConfigs(repos)

    // Compute groups of repository configs for convenient parallelization
    repos = util.computeRepoGroups(repos)

    registry.get(LeVADocumentScheduler).run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)

    return [ project: project, repos: repos ]
}

private boolean hasCapability(List capabilities, String name) {
    def collector = {
        return (it instanceof Map) ? it.keySet().first().toLowerCase() : it.toLowerCase()
    }

    return capabilities.collect(collector).contains(name.toLowerCase())
}

return this
