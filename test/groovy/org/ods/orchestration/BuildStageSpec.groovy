package org.ods.orchestration

import org.ods.PipelineScript
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.usecase.JUnitTestReportsUseCase
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.services.JenkinsService
import org.ods.services.ServiceRegistry
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import org.ods.util.PipelineSteps
import util.SpecHelper

import static util.FixtureHelper.createProject

class BuildStageSpec extends SpecHelper {
    Project project
    BuildStage buildStage
    IPipelineSteps steps
    PipelineScript script
    MROPipelineUtil util
    JiraUseCase jira
    JUnitTestReportsUseCase junit
    JenkinsService jenkins
    LeVADocumentScheduler levaDocScheduler
    ILogger logger

    def phase = MROPipelineUtil.PipelinePhases.BUILD

    def setup() {
        script = new PipelineScript()
        steps = Mock(PipelineSteps)
        levaDocScheduler = Mock(LeVADocumentScheduler)
        project = Spy(createProject())
        util = Mock(MROPipelineUtil)
        jira = Mock(JiraUseCase)
        junit = Mock(JUnitTestReportsUseCase)
        jenkins = Mock(JenkinsService)
        logger = new Logger(script, !!script.env.DEBUG)
        createService()
        buildStage = Spy(new BuildStage(script, project, project.repositories, null))
    }

    ServiceRegistry createService() {
        def registry = ServiceRegistry.instance

        registry.add(PipelineSteps, steps)
        registry.add(LeVADocumentScheduler, levaDocScheduler)
        registry.add(MROPipelineUtil, util)
        registry.add(JiraUseCase, jira)
        registry.add(JUnitTestReportsUseCase, junit)
        registry.add(JenkinsService, jenkins)
        registry.add(Logger, logger)

        return registry
    }

    def "successful execution"() {
        when:
        buildStage.run()

        then:
        1 * levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START)
        1 * levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)
    }

    def "unit test errors in WIP version doesn't break the stage"() {
        given:
        project.hasFailingTests = true
        project.data.buildParams.version = project.BUILD_PARAM_VERSION_DEFAULT

        when:
        buildStage.run()

        then:
        1 * levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START)
        1 * levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)
        1 * util.failBuild(_)
    }

    def "unit test errors in X version break the stage"() {
        given:
        project.hasFailingTests = true

        when:
        buildStage.run()

        then:
        1 * levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START)
        1 * levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)
        1 * util.failBuild(_)
        IllegalStateException ex = thrown()
        ex.message == 'Failing build as repositories contain errors!\nFailed: []'
    }

}
