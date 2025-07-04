package org.ods.orchestration

import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.services.BitbucketService
import org.ods.services.GitService
import org.ods.services.OpenShiftService
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
    BitbucketService bitbucketService

    def setup() {
        script = new PipelineSteps()
        levaDocScheduler = Mock(LeVADocumentScheduler)
        openshiftService = Mock(OpenShiftService)
        util = Mock(MROPipelineUtil)
        logger = new Logger(script, true)
        project = Spy(createProject())
        gitService = Mock(GitService)
        bitbucketService = Mock(BitbucketService)

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
        registry.add(BitbucketService, bitbucketService)

        return registry
    }

    def "deploy in Q with 3 installable repos (all included)"() {
        given:
        project.buildParams.targetEnvironment = 'qa'
        project.buildParams.targetEnvironmentToken = 'Q'
        project.targetProject = "net-test"

        when:
        deployStage.run()

        then:
        1 * bitbucketService.getUrl() >> "https://bitbucket"
        1 * util.getInstallableRepos()
        0 * util.verifyEnvLoginAndExistence(*_)
    }

    def "deploy in Q with 3 installable repos (none included)"() {
        given:
        project.buildParams.targetEnvironment = 'qa'
        project.buildParams.targetEnvironmentToken = 'Q'
        project.targetProject = "net-test"
        for (repo in project.data.metadata.repositories) {
            repo.include = false
        }

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

        when:
        deployStage.run()

        then:
        1 * bitbucketService.getUrl() >> "https://bitbucket"
        0 * project.targetClusterExternal >> false
        0 * openshiftService.envExists(project.targetProject) >> true
    }
}
