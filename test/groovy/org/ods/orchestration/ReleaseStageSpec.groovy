package org.ods.orchestration

import org.ods.PipelineScript
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.services.ServiceRegistry
import org.ods.orchestration.usecase.JUnitTestReportsUseCase
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.util.IPipelineSteps
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import util.SpecHelper
import util.PipelineSteps
import org.ods.util.Logger
import org.ods.util.ILogger

import static util.FixtureHelper.createProject

class ReleaseStageSpec extends SpecHelper {

    Project project
    ReleaseStage releaseStage
    IPipelineSteps steps
    PipelineScript script
    MROPipelineUtil util
    LeVADocumentScheduler levaDocScheduler
    ILogger logger

    def phase = MROPipelineUtil.PipelinePhases.RELEASE

    def setup() {
        this.script = new PipelineScript()
        this.steps = new PipelineSteps()
        this.levaDocScheduler = Mock(LeVADocumentScheduler)
        this.project = Spy(createProject())
        this.util = Mock(MROPipelineUtil)
        this.logger = new Logger(script, !!script.env.DEBUG)

        ServiceRegistry.instance.add(org.ods.util.PipelineSteps, this.steps)
        ServiceRegistry.instance.add(LeVADocumentScheduler, this.levaDocScheduler)
        ServiceRegistry.instance.add(MROPipelineUtil, this.util)
        ServiceRegistry.instance.add(Logger, this.logger)

        this.releaseStage = Spy(new ReleaseStage(this.script, this.project, this.project.repositories))
    }

    def "succesful execution"() {
        when:
        releaseStage.execute()

        then:
        1 * levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START)
        1 * util.executeRepoGroups(*_)
        1 * levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)
    }

}
