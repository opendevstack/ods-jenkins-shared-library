package org.ods.orchestration.usecase

import org.ods.orchestration.service.JiraZephyrService
import org.ods.util.IPipelineSteps
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project

@SuppressWarnings(['IfStatementBraces', 'LineLength', 'AbcMetric', 'Instanceof', 'CyclomaticComplexity'])
class JiraUseCaseZephyrSupport extends AbstractJiraUseCaseSupport {

    private JiraZephyrService zephyr
    private MROPipelineUtil util

    JiraUseCaseZephyrSupport(Project project, IPipelineSteps steps, JiraUseCase usecase,
        JiraZephyrService zephyr, MROPipelineUtil util) {
        super(project, steps, usecase)
        this.zephyr = zephyr
        this.util = util
    }

    void applyXunitTestResultsAsTestExecutionStatii(List testIssues, Map testResults) {
        if (!this.usecase.jira) return
        if (!this.zephyr) return

        def testCycleId = '-1'
        if (!testIssues?.isEmpty()) {
            def buildParams = this.project.buildParams

            def versionId = this.project.version?.id ?: project.loadVersionDataFromJira(buildParams.changeId)?.id
            def testCycles = this.zephyr.getTestCycles(this.project.id, versionId)

            // Zephyr test cycle properties
            def name = (buildParams.version == 'WIP'? "Preview: Build ": "${buildParams.targetEnvironmentToken}: Build ") + this.steps.env.BUILD_ID
            def build = this.steps.env.BUILD_URL
            def environment = buildParams.targetEnvironment

            testCycleId = testCycles.find { it.value instanceof Map && it.value.name == name && it.value.build == build && it.value.environment == environment }?.key

            if (!testCycleId) {
                testCycleId = this.zephyr.createTestCycle(this.project.id, versionId, name, build, environment).id
            }
        }

        testIssues.each { testIssue ->
            // Create a new execution with status UNEXECUTED
            def testExecutionId = this.zephyr.createTestExecutionForIssue(
                testIssue.id, this.project.id, testCycleId).keySet().first()

            testResults.testsuites.each { testSuite ->
                testSuite.testcases.each { testCase ->
                    if (this.usecase.checkTestsIssueMatchesTestCase(testIssue, testCase)) {
                        def failed = testCase.error || testCase.failure
                        def skipped = testCase.skipped
                        def succeeded = !(testCase.error || testCase.failure || testCase.skipped)

                        if (succeeded) {
                            this.zephyr.updateTestExecutionForIssuePass(testExecutionId)
                        } else if (failed) {
                            this.zephyr.updateTestExecutionForIssueFail(testExecutionId)
                        } else if (skipped) {
                            this.zephyr.updateTestExecutionForIssueBlocked(testExecutionId)
                        }
                    }
                }
            }
        }
    }

    void applyXunitTestResults(List testIssues, Map testResults) {
        this.usecase.applyXunitTestResultsAsTestIssueLabels(testIssues, testResults)
        this.applyXunitTestResultsAsTestExecutionStatii(testIssues, testResults)
    }

}
