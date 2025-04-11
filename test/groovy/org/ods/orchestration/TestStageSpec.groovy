package org.ods.orchestration

import org.ods.orchestration.TestStage
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.services.JenkinsService
import org.ods.services.ServiceRegistry
import org.ods.orchestration.usecase.JUnitTestReportsUseCase
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.util.IPipelineSteps
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PipelinePhaseLifecycleStage
import org.ods.orchestration.util.Project
import org.ods.util.Logger
import org.ods.util.ILogger

import util.PipelineSteps
import util.SpecHelper

import static util.FixtureHelper.createProject

class TestStageSpec extends SpecHelper {
    Project project
    TestStage testStage
    IPipelineSteps script
    MROPipelineUtil util
    JiraUseCase jira
    JUnitTestReportsUseCase junit
    JenkinsService jenkins
    LeVADocumentScheduler levaDocScheduler
    ILogger logger

    def phase = MROPipelineUtil.PipelinePhases.TEST

    def setup() {
        script = new PipelineSteps()
        levaDocScheduler = Mock(LeVADocumentScheduler)
        project = Spy(createProject())
        util = Mock(MROPipelineUtil)
        jira = Mock(JiraUseCase)
        junit = Mock(JUnitTestReportsUseCase)
        jenkins = Mock(JenkinsService)
        logger = new Logger(script, !!script.env.DEBUG)
        createService()
        testStage = Spy(new TestStage(script, project, project.repositories, null))
    }

    ServiceRegistry createService() {
        def registry = ServiceRegistry.instance

        registry.add(IPipelineSteps, script)
        registry.add(LeVADocumentScheduler, levaDocScheduler)
        registry.add(MROPipelineUtil, util)
        registry.add(JiraUseCase, jira)
        registry.add(JUnitTestReportsUseCase, junit)
        registry.add(JenkinsService, jenkins)
        registry.add(Logger, logger)

        return registry
    }

    def "succesful execution"() {
        given:
        junit.parseTestReportFiles(*_) >> [:]

        when:
        testStage.run()

        then:
        1 * levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.POST_START)
        1 * util.prepareExecutePhaseForReposNamedJob(*_)
        1 * levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.PRE_END, [:], _)
    }

    def "in TEST repo only one call per test types to report test results in Jira"() {
        given:
        script.env >> [WORKSPACE: "", BUILD_ID: 1]
        jenkins.unstashFilesIntoPath(_, _, "JUnit XML Report") >> true
        junit.loadTestReportsFromPath(_) >> []
        junit.parseTestReportFiles(_) >> [ testsuites: [] ]

        when:
        testStage.run()

        then:
        1 * testStage.getTestResults(script, _, Project.TestType.INSTALLATION) >> [testReportFiles: [], testResults: [testsuites:[]]]
        1 * testStage.getTestResults(script, _, Project.TestType.INTEGRATION) >> [testReportFiles: [], testResults: [testsuites:[]]]
        1 * testStage.getTestResults(script, _, Project.TestType.ACCEPTANCE) >> [testReportFiles: [], testResults: [testsuites:[]]]
        1 * util.prepareExecutePhaseForReposNamedJob(MROPipelineUtil.PipelinePhases.TEST, project.repositories, _, _) >> { phase_, repos_, preExecuteRepo_, postExecuteRepo_ ->
            postExecuteRepo_.call(script, [type: MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST] as Map)
            return []
        }
        1 * jira.reportTestResultsForProject([Project.TestType.INSTALLATION], _)
        1 * jira.reportTestResultsForProject([Project.TestType.INTEGRATION], _)
        1 * jira.reportTestResultsForProject([Project.TestType.ACCEPTANCE], _)
    }

    def "get test results from file"() {
        given:
        script.env >> [WORKSPACE : "", BUILD_ID : 1]
        jenkins.unstashFilesIntoPath(_, _, "JUnit XML Report") >> true
        junit.loadTestReportsFromPath(_) >> []
        junit.parseTestReportFiles(_) >> [:]

        when:
        testStage.getTestResults(script, project.repositories.first(), "acceptance")

        then:
        1 * junit.loadTestReportsFromPath(_)
    }
}
