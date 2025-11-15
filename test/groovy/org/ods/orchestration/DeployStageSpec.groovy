package org.ods.orchestration

import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.services.GitService
import org.ods.services.IScmService
import org.ods.services.OpenShiftService
import org.ods.services.ScmBitbucketService
import org.ods.services.ServiceRegistry
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import util.PipelineSteps
import util.SpecHelper

import static util.FixtureHelper.createProject

class DeployStageSpec extends SpecHelper {
    Project project
    DeployStage deployStage
    IPipelineSteps script
    LeVADocumentScheduler levaDocScheduler
    OpenShiftService openshiftService
    MROPipelineUtil util
    ILogger logger
    GitService gitService
    ScmBitbucketService bitbucketService

    def setup() {
        script = Spy(new PipelineSteps() {
            def EXTERNAL_OCP_API_TOKEN = "test"
        })
        levaDocScheduler = Mock(LeVADocumentScheduler)
        openshiftService = Mock(OpenShiftService)
        logger = new Logger(script, true)
        project = Spy(createProject())
        gitService = Mock(GitService)
        bitbucketService = Mock(ScmBitbucketService)
        util = Spy(new MROPipelineUtil(project, script, gitService, logger))

        createService()

        deployStage = Spy(new DeployStage(script, project, project.data.metadata.repositories, "fake"))
    }

    ServiceRegistry createService() {
        def registry = ServiceRegistry.instance

        registry.add(IPipelineSteps, script)
        registry.add(LeVADocumentScheduler, levaDocScheduler)
        registry.add(OpenShiftService, openshiftService)
        registry.add(MROPipelineUtil, util)
        registry.add(Logger, logger)
        registry.add(GitService, gitService)
        registry.add(IScmService, bitbucketService)

        return registry
    }

    def "deploy in Q with 3 installable repos (all included)"() {
        given:
        project.buildParams.targetEnvironment = 'qa'
        project.buildParams.targetEnvironmentToken = 'Q'
        project.targetProject = "net-test"
        openshiftService.envExists(*_) >> true
        util.prepareExecutePhaseForReposNamedJob(*_) >> []
        project.setOpenShiftData("test.api.url")

        when:
        deployStage.run()

        then:
        1 * bitbucketService.getUrl() >> "https://bitbucket"
        1 * util.getInstallableRepos()
        1 * util.verifyEnvLoginAndExistence(*_)
    }

    def "deploy in Q with 3 installable repos (none included)"() {
        given:
        project.buildParams.targetEnvironment = 'qa'
        project.buildParams.targetEnvironmentToken = 'Q'
        project.targetProject = "net-test"
        for (repo in project.data.metadata.repositories) {
            repo.include = false
        }
        openshiftService.envExists(*_) >> true
        util.prepareExecutePhaseForReposNamedJob(*_) >> []
        project.setOpenShiftData("test.api.url")

        when:
        deployStage.run()

        then:
        1 * bitbucketService.getUrl() >> "https://bitbucket"
        0 * project.targetClusterExternal >> false
        0 * openshiftService.envExists(project.targetProject) >> true
    }

    def "deploy in Q with 2 installable repos (none included) and one infra"() {
        given:
        project.buildParams.targetEnvironment = 'qa'
        project.buildParams.targetEnvironmentToken = 'Q'
        project.targetProject = "net-test"
        for (repo in project.data.metadata.repositories) {
            if (repo.id == "demo-app-catalogue") {
                repo.type = "ods-infra"
            } else {
                repo.include = false
            }
        }
        openshiftService.envExists(*_) >> true
        util.prepareExecutePhaseForReposNamedJob(*_) >> []
        project.setOpenShiftData("test.api.url")

        when:
        deployStage.run()

        then:
        1 * bitbucketService.getUrl() >> "https://bitbucket"
        0 * project.targetClusterExternal >> false
        0 * openshiftService.envExists(project.targetProject) >> true
    }

    def "deploy in Q with 3 installable repos (all included) and empty prod config"() {
        given:
        project.buildParams.targetEnvironment = 'prod'
        project.buildParams.targetEnvironmentToken = 'P'
        project.targetProject = "net-test"
        openshiftService.envExists(project.targetProject) >> true
        util.prepareExecutePhaseForReposNamedJob(*_) >> []
        project.data.metadata.environments.prod = [
            'apiUrl': '',
            'credentialsId': ''
        ]
        project.setOpenShiftData("test.api.url")

        when:
        deployStage.run()

        then:
        1 * bitbucketService.getUrl() >> "https://bitbucket"
        1 * util.getInstallableRepos()
        1 * util.verifyEnvLoginAndExistence(*_)
        0 * script.usernamePassword(['credentialsId':'',
                                     'usernameVariable':'EXTERNAL_OCP_API_SA',
                                     'passwordVariable':'EXTERNAL_OCP_API_TOKEN'])
    }

    def "deploy in Q with 3 installable repos (all included), no env config and non existant env project"() {
        given:
        project.buildParams.targetEnvironment = 'prod'
        project.buildParams.targetEnvironmentToken = 'P'
        project.targetProject = "net-test"
        util.prepareExecutePhaseForReposNamedJob(*_) >> []
        project.setOpenShiftData("test.api.url")

        when:
        deployStage.run()

        then:
        thrown(RuntimeException)
    }

    def "deploy in Q with 3 installable repos (all included) and existing wrong prod config"() {
        given:
        project.buildParams.targetEnvironment = 'prod'
        project.buildParams.targetEnvironmentToken = 'P'
        project.targetProject = "net-test"
        openshiftService.envExists(project.targetProject) >> true
        util.prepareExecutePhaseForReposNamedJob(*_) >> []
        project.data.metadata.environments.prod = [
            'openshiftClusterApiUrl': 'external.api.com',
            'openshiftClusterCredentialsId': 'testCredentials'
        ]
        project.setOpenShiftData("test.api.url")

        when:
        deployStage.run()

        then:
        1 * bitbucketService.getUrl() >> "https://bitbucket"
        1 * util.getInstallableRepos()
        1 * util.verifyEnvLoginAndExistence(*_)
        1 * script.usernamePassword(['credentialsId':'testCredentials',
                                     'usernameVariable':'EXTERNAL_OCP_API_SA',
                                     'passwordVariable':'EXTERNAL_OCP_API_TOKEN'])
    }
}
