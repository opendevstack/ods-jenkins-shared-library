package org.ods.orchestration

import org.ods.PipelineScript
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.services.JenkinsService
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

class TestStageSpec extends SpecHelper {

    Project project
    TestStage testStage
    IPipelineSteps steps
    PipelineScript script
    MROPipelineUtil util
    JiraUseCase jira
    JUnitTestReportsUseCase junit
    JenkinsService jenkins
    LeVADocumentScheduler levaDocScheduler
    ILogger logger

    def phase = MROPipelineUtil.PipelinePhases.TEST

    def setup() {
        this.script = new PipelineScript()
        this.steps = new PipelineSteps()
        this.levaDocScheduler = Mock(LeVADocumentScheduler)
        this.project = Spy(createProject())
        this.util = Mock(MROPipelineUtil)
        this.jira = Mock(JiraUseCase)
        this.junit = Mock(JUnitTestReportsUseCase)
        this.jenkins = Mock(JenkinsService)
        this.logger = new Logger(script, !!script.env.DEBUG)

        ServiceRegistry.instance.add(org.ods.util.PipelineSteps, this.steps)
        ServiceRegistry.instance.add(LeVADocumentScheduler, this.levaDocScheduler)
        ServiceRegistry.instance.add(MROPipelineUtil, this.util)
        ServiceRegistry.instance.add(JiraUseCase, this.jira)
        ServiceRegistry.instance.add(JUnitTestReportsUseCase, this.junit)
        ServiceRegistry.instance.add(JenkinsService, this.jenkins)
        ServiceRegistry.instance.add(Logger, this.logger)

        this.testStage = Spy(new TestStage(script, project, project.repositories, null))
    }

    def "succesful execution"() {
        given:
        junit.parseTestReportFiles(*_) >> [:]

        when:
        testStage.execute()

        then:
        1 * levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START)
        1 * util.executeRepoGroups(*_)
        1 * levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, [:], _)
    }

    def "in TEST repo only one call per test types to report test results in Jira"() {
        given:
        steps.env >> [WORKSPACE: "", BUILD_ID: 1]
        jenkins.unstashFilesIntoPath(_, _, "JUnit XML Report") >> true
        junit.loadTestReportsFromPath(_) >> []
        junit.parseTestReportFiles(_) >> [:]

        when:
        testStage.execute()

        then:
        1 * util.executeRepoGroups(project.repositories, _, _, _) >> { repos_, executeRepo_, preExecuteRepo_, postExecuteRepo ->
            postExecuteRepo.call([type: MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST] as Map)
            return []
        }
        1 * jira.reportTestResultsForProject([Project.TestType.INSTALLATION], _)
        1 * jira.reportTestResultsForProject([Project.TestType.INTEGRATION], _)
        1 * jira.reportTestResultsForProject([Project.TestType.ACCEPTANCE], _)
    }

    def "get test results from file"() {
        given:
        steps.env >> [WORKSPACE : "", BUILD_ID : 1]
        jenkins.unstashFilesIntoPath(_, _, "JUnit XML Report") >> true
        junit.loadTestReportsFromPath(_) >> []
        junit.parseTestReportFiles(_) >> [:]

        when:
        testStage.setServices()
        testStage.getTestResults(steps, project.repositories.first(), "acceptance")

        then:
        1 * junit.loadTestReportsFromPath(_)
    }
}
