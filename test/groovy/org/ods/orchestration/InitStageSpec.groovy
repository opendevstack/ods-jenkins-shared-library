package org.ods.orchestration

import org.ods.PipelineScript
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PipelinePhaseLifecycleStage
import org.ods.orchestration.util.Project
import org.ods.services.GitService
import org.ods.services.ServiceRegistry
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import org.ods.util.PipelineSteps
import util.SpecHelper

import static util.FixtureHelper.createProject

class InitStageSpec extends SpecHelper {
    Project project
    InitStage initStage
    IPipelineSteps steps
    PipelineScript script
    MROPipelineUtil util
    JiraUseCase jira
    GitService gitService
    ILogger logger

    def phase = MROPipelineUtil.PipelinePhases.BUILD

    def setup() {
        script = new PipelineScript()
        steps = Mock(PipelineSteps)
        project = Spy(createProject())
        util = Mock(MROPipelineUtil)
        jira = Mock(JiraUseCase)
        gitService = Mock(GitService)
        logger = new Logger(script, true)
        createService()
        initStage = Spy(new InitStage(script, project, project.repositories, null))
    }

    ServiceRegistry createService() {
        def registry = ServiceRegistry.instance

        registry.add(PipelineSteps, steps)
        registry.add(MROPipelineUtil, util)
        registry.add(JiraUseCase, jira)
        registry.add(Logger, logger)

        return registry
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

}
