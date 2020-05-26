package org.ods.orchestration.usecase


import org.ods.orchestration.service.*
import org.ods.orchestration.util.*
import org.ods.util.IPipelineSteps

import static util.FixtureHelper.*

import util.*

class JiraUseCaseZephyrSupportSpec extends SpecHelper {

    JiraService jira
    Context context
    IPipelineSteps steps
    JiraUseCase usecase
    MROPipelineUtil util
    JiraZephyrService zephyr
    JiraUseCaseZephyrSupport support

    def setup() {
        context = Spy(createContext())
        steps = Spy(util.PipelineSteps)
        util = Mock(MROPipelineUtil)
        jira = Mock(JiraService)
        usecase = Spy(new JiraUseCase(context, steps, util, jira))

        zephyr = Mock(JiraZephyrService)
        support = Spy(new JiraUseCaseZephyrSupport(context, steps, usecase, zephyr, util))
        usecase.setSupport(support)
    }

    def "apply xUnit test results as test execution statii"() {
        given:
        def testIssues = createJiraTestIssues()
        def testResults = createTestResults()

        when:
        support.applyXunitTestResultsAsTestExecutionStatii(testIssues, testResults)

        then:
        1 * zephyr.getTestCycles(context.id, "11100")
        1 * zephyr.createTestCycle(context.id, "11100", context.buildParams.targetEnvironmentToken + ": Build " + steps.env.BUILD_ID, steps.env.BUILD_URL, context.buildParams.targetEnvironment) >> [id: "111"]

        then:
        1 * zephyr.createTestExecutionForIssue("1", context.id, "111") >> ["11": []]
        1 * zephyr.updateTestExecutionForIssuePass("11")
        0 * zephyr./^updateTestExecutionForIssue.*/("11")

        then:
        1 * zephyr.createTestExecutionForIssue("2", context.id, "111") >> ["12": []]
        1 * zephyr.updateTestExecutionForIssueFail("12")
        0 * zephyr./^updateTestExecutionForIssue.*/("12")

        then:
        1 * zephyr.createTestExecutionForIssue("3", context.id, "111") >> ["13": []]
        1 * zephyr.updateTestExecutionForIssueFail("13")
        0 * zephyr./^updateTestExecutionForIssue.*/("13")

        then:
        1 * zephyr.createTestExecutionForIssue("4", context.id, "111") >> ["14": []]
        1 * zephyr.updateTestExecutionForIssueBlocked("14")
        0 * zephyr./^updateTestExecutionForIssue.*/("14")

        then:
        // Leave test execution at initial status UNEXECUTED otherwise
        1 * zephyr.createTestExecutionForIssue("5", context.id, "111") >> ["15": []]
        0 * zephyr./^updateTestExecutionForIssue.*/("15")
    }

    def "apply xUnit test results as test execution statii without test issues"() {
        given:
        def testIssues = []
        def testResults = [:]

        when:
        support.applyXunitTestResultsAsTestExecutionStatii(testIssues, testResults)

        then:
        0 * zephyr.createTestCycle(context.id, "-1", null, steps.env.BUILD_URL, context.buildParams.targetEnvironment)
    }

    def "apply xUnit test results"() {
        given:
        def testIssues = createJiraTestIssues()
        def testResults = createTestResults()

        when:
        support.applyXunitTestResults(testIssues, testResults)

        then:
        1 * usecase.applyXunitTestResultsAsTestIssueLabels(testIssues, testResults)
        _ * zephyr.createTestCycle(*_) >> [id: "1"]
        _ * zephyr.createTestExecutionForIssue(*_) >> ["1": []]
        1 * support.applyXunitTestResultsAsTestExecutionStatii(testIssues, testResults)
    }
}
