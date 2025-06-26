package org.ods.orchestration

import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.services.BitbucketService
import org.ods.services.GitService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import util.PipelineSteps
import util.SpecHelper

import static util.FixtureHelper.createProject

class InitStageSpec extends SpecHelper {
    GitService gitService
    BitbucketService bitbucketService
    OpenShiftService openShiftService
    NexusService nexusService
    IPipelineSteps script
    Project project
    InitStage initStage
    MROPipelineUtil util
    JiraUseCase jira
    ILogger logger

    def setup() {
        script = new PipelineSteps()
        gitService = Mock(GitService)
        bitbucketService = Mock(BitbucketService)
        openShiftService = Mock(OpenShiftService)
        nexusService = Mock(NexusService)
        jira = Mock(JiraUseCase)
        logger = new Logger(script, true)
        project = Spy(createProject())
        util = Mock(MROPipelineUtil)
        createService()
        initStage = Spy(new InitStage(script, project, project.repositories, null))
    }

    ServiceRegistry createService() {
        def registry = ServiceRegistry.instance

        registry.add(IPipelineSteps, script)
        registry.add(MROPipelineUtil, util)
        registry.add(JiraUseCase, jira)
        registry.add(Logger, logger)
        registry.add(GitService, gitService)
        registry.add(BitbucketService, bitbucketService)
        registry.add(OpenShiftService, openShiftService)
        registry.add(NexusService, nexusService)

        return registry
    }


    ServiceRegistry createService(GitService git) {
        def registry = createService()
        registry.add(GitService, git)
        return registry
    }

    Map buildLoadClousure(boolean rePromote, String targetEnvironmentToken)
    {
        Map buildParams =  [
            changeDescription            : "The change I've wanted.",
            changeId                     : "0815",
            configItem                   : "myItem",
            targetEnvironment            : "prod",
            targetEnvironmentToken       : targetEnvironmentToken,
            version                      : "1.0.0",
            rePromote                    : rePromote
        ]

        project = createProject(buildParams)
        createService(project.git)
        initStage = new InitStage(script, project, project.repositories, null)
        script.env >> [BUILD_URL: 'http://dummy', BUILD_NUMBER: '01']
        openShiftService.apiUrl >> ''
        buildParams
    }

    def "checkOutReleaseManagerRepo_WhenBranchExists"() {
        given:
        Map buildParams = [ : ]
        buildParams.version = "WIP"
        buildParams.changeId = "1.0.0"
        buildParams.targetEnvironmentToken = "D"
        when:
        initStage.checkOutReleaseManagerRepository(buildParams, gitService, logger)

        then:
        1 * gitService.remoteBranchExists("release/${buildParams.changeId}") >> true
        1 * gitService.checkout("*/release/${buildParams.changeId}", _, _)
    }

    def "checkOutReleaseManagerRepo_WhenBranchNotExistsAndWIP"() {
        given:
        Map buildParams = [ : ]
        buildParams.version = "WIP"
        buildParams.changeId = "1.0.0"
        buildParams.targetEnvironmentToken = "D"
        when:
        initStage.checkOutReleaseManagerRepository(buildParams, gitService, logger)

        then:
        1 * gitService.remoteBranchExists("release/${buildParams.changeId}") >> false
        1 * project.setGitReleaseBranch("master")
    }

    def "checkOutReleaseManagerRepo_WhenBranchNotExistsAndNoWip"() {
        given:
        Map buildParams = [ : ]
        buildParams.version = "1.0.0"
        buildParams.changeId = "1.0.0"
        buildParams.targetEnvironmentToken = "D"
        String gitReleaseBranch = "release/${buildParams.changeId}"
        when:
        initStage.checkOutReleaseManagerRepository(buildParams, gitService, logger)

        then:
        1 * gitService.remoteBranchExists(gitReleaseBranch) >> false
        1 * gitService.checkoutNewLocalBranch(gitReleaseBranch)
        1 * project.setGitReleaseBranch(gitReleaseBranch)
    }

    def "buildLoadClousure_WhenRedeployWithRePromote"() {
        given:
        Map buildParams = buildLoadClousure(true, targetEnvironmentToken)

        when:
        initStage.buildLoadClousure(logger, ServiceRegistry.instance, buildParams, project.git, script).run()

        then:
        noExceptionThrown()

        where:
        targetEnvironmentToken << ["P", "Q"]
    }

    def "buildLoadClousure_WhenRedeployWithoutRePromote"() {
        given:
        Map buildParams = buildLoadClousure(false, targetEnvironmentToken)

        when:
        initStage.buildLoadClousure(logger, ServiceRegistry.instance, buildParams, project.git, script).run()

        then:
        thrown(RuntimeException)

        where:
        targetEnvironmentToken << ["P", "Q"]
      }

    def "validateEnvConfig for missing prod config" () {
        given:
        project.data.metadata.environments = [:]
        def message = "The Release Manager configuration misses the location of an OpenShift production " +
            "cluster in the metadata.yml."

        when:
        initStage.validateEnvConfig(logger, ServiceRegistry.instance, util)

        then:
        1 * project.addCommentInReleaseStatus(message)
        1 * util.failBuild(message)
    }

    def "validateEnvConfig for invalid prod config" () {
        given:
        project.data.metadata.environments = ['prod': ['openshiftClusterApiUrl': '', 'openshiftClusterCredentialsId': '']]
        def message = "The Release Manager configuration for environment(s) prod is incorrect in the metadata.yml. " +
            "Please verify the openshift cluster api url and credentials for each environment mentioned."

        when:
        initStage.validateEnvConfig(logger, ServiceRegistry.instance, util)

        then:
        1 * project.addCommentInReleaseStatus(message)
        1 * util.failBuild(message)
    }

    def "validateEnvConfig for valid prod config" () {
        given:
        project.data.metadata.environments = ['prod': ['openshiftClusterApiUrl': 'www.example.com',
                                                       'openshiftClusterCredentialsId': 'testCredentials']
                                            ]
        openShiftService.isValidClusterConfigForEnv(*_) >> true

        when:
        initStage.validateEnvConfig(logger, ServiceRegistry.instance, util)

        then:
        0 * project.addCommentInReleaseStatus(_)
        0 * util.failBuild(_)
        0 * util.warnBuild(_)

    }

}
