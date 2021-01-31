package org.ods.orchestration

import org.ods.PipelineScript
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.services.ServiceRegistry
import org.ods.services.GitService
import org.ods.services.OpenShiftService
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

class BuildStageSpec extends SpecHelper {

    Project project
    BuildStage buildStage
    IPipelineSteps steps
    PipelineScript script
    MROPipelineUtil util
    GitService git
    OpenShiftService openShift
    LeVADocumentScheduler levaDocScheduler
    ILogger logger

    def phase = MROPipelineUtil.PipelinePhases.BUILD

    def setup() {
        this.script = new PipelineScript()
        this.steps = new PipelineSteps()
        this.levaDocScheduler = Mock(LeVADocumentScheduler)
        this.project = Spy(createProject())
        this.util = Mock(MROPipelineUtil)
        this.git = Mock(GitService)
        this.openShift = Mock(OpenShiftService)
        this.logger = new Logger(script, !!script.env.DEBUG)

        ServiceRegistry.instance.add(org.ods.util.PipelineSteps, this.steps)
        ServiceRegistry.instance.add(LeVADocumentScheduler, this.levaDocScheduler)
        ServiceRegistry.instance.add(MROPipelineUtil, this.util)
        ServiceRegistry.instance.add(GitService, this.git)
        ServiceRegistry.instance.add(OpenShiftService, this.openShift)
        ServiceRegistry.instance.add(Logger, this.logger)

        this.buildStage = Spy(new BuildStage(this.script, this.project, this.project.repositories, null))
    }

    def "succesful execution"() {
        given:
        this.steps.env >> [WORKSPACE: "", BUILD_ID: 1]

        when:
        buildStage.execute()

        then:
        1 * levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START)
        1 * util.executeRepoGroups(*_)
        1 * levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)
    }

}
