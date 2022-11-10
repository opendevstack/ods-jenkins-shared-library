package org.ods.orchestration

import org.ods.PipelineScript
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.services.GitService
import org.ods.services.ServiceRegistry
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import org.ods.util.PipelineSteps
import util.SpecHelper

import static util.FixtureHelper.createProject

class FinalizeStageSpec extends SpecHelper {
    Project project
    FinalizeStage finalStage
    IPipelineSteps steps
    PipelineScript script
    MROPipelineUtil util
    JiraUseCase jira
    GitService gitService
    LeVADocumentScheduler levaDocScheduler
    ILogger logger

    def setup() {
        script = new PipelineScript()
        steps = Mock(PipelineSteps)
        levaDocScheduler = Mock(LeVADocumentScheduler)
        project = Spy(createProject())
        util = Mock(MROPipelineUtil)
        gitService = Mock(GitService)
        jira = Mock(JiraUseCase)
        logger = new Logger(script, true)
        createService()
        for (repo in project.data.metadata.repositories) {
            repo.data.git = [:]
            repo.data.git.createdExecutionCommit = 'd240853866f20fc3e536cb3bca86c86c54b723ce'

        }
        project.gitData.createdExecutionCommit = 'd240853866f20fc3e536cb3bca86c86c54b723ce'
        finalStage = Spy(new FinalizeStage(script, project, project.data.metadata.repositories))
    }

    ServiceRegistry createService() {
        def registry = ServiceRegistry.instance

        registry.add(PipelineSteps, steps)
        registry.add(MROPipelineUtil, util)
        registry.add(JiraUseCase, jira)
        registry.add(Logger, logger)

        return registry
    }

    def "pushToMasterWhenWIPandNoReleaseBranch"(){
        given:
        Map buildParams = [ : ]
        buildParams.version = "WIP"
        buildParams.changeId = "1.0.0"
        buildParams.targetEnvironmentToken = "D"
        project.buildParams.version = 'WIP'
        project.setGitReleaseBranch('master')

        when:
        finalStage.recordAndPushEnvStateForReleaseManager(steps, logger, gitService)

        then:
        1 * gitService.pushRef('master')
    }

    def "pushToReleaseAndMasterWhenWipAndReleaseBranch"(){
        given:
        Map buildParams = [ : ]
        buildParams.version = "WIP"
        buildParams.changeId = "1.0.0"
        buildParams.targetEnvironmentToken = "D"
        project.buildParams.version = "WIP"
        project.setGitReleaseBranch('release/1.0.0')

        when:
        finalStage.recordAndPushEnvStateForReleaseManager(steps, logger, gitService)

        then:
        1 * gitService.pushRef('master')
        0 * gitService.createTag(project.targetTag)
        1 * gitService.pushForceBranchWithTags(project.gitReleaseBranch)
    }

    def "pushToReleaseAndTag"(){
        given:
        Map buildParams = [ : ]
        buildParams.version = "1.0.0"
        buildParams.changeId = "1.0.0"
        buildParams.targetEnvironmentToken = "D"
        project.gitData.targetTag = "1.0.0"
        gitService.remoteTagExists(project.targetTag) >> false

        when:
        finalStage.recordAndPushEnvStateForReleaseManager(steps, logger, gitService)

        then:
        1 * gitService.pushRef('master')
        1 * gitService.createTag(project.targetTag)
        1 * gitService.pushForceBranchWithTags(project.gitReleaseBranch)
    }

    def "pushToReleaseWithExistingTag"(){
        given:
        Map buildParams = [ : ]
        buildParams.version = "1.0.0"
        buildParams.changeId = "1.0.0"
        buildParams.targetEnvironmentToken = "D"
        project.gitData.targetTag = "1.0.0"
        gitService.remoteTagExists(project.targetTag) >> true

        when:
        finalStage.recordAndPushEnvStateForReleaseManager(steps, logger, gitService)

        then:
        1 * gitService.pushRef('master')
        1 * gitService.createTag(project.targetTag)
        1 * gitService.pushForceBranchWithTags(project.gitReleaseBranch)
    }
}
