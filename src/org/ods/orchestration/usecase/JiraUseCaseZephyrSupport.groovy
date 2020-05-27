package org.ods.orchestration.usecase

import org.ods.orchestration.service.JiraZephyrService
import org.ods.util.IPipelineSteps
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project

@SuppressWarnings(['IfStatementBraces', 'LineLength', 'AbcMetric', 'Instanceof'])
class JiraUseCaseZephyrSupport extends AbstractJiraUseCaseSupport {

    private JiraZephyrService zephyr
    private MROPipelineUtil util

    JiraUseCaseZephyrSupport(Project project, IPipelineSteps steps, JiraUseCase usecase, JiraZephyrService zephyr, MROPipelineUtil util) {
        super(project, steps, usecase)
        this.zephyr = zephyr
        this.util = util
    }

    void applyXunitTestResultsAsTestExecutionStatii(List testIssues, Map testResults) {
        this.steps.echo ("usecases: ${this.zephyr} / ${this.usecase.jira}")
        if (!this.usecase.jira) return
        if (!this.zephyr) return

        def testCycleId = "-1"
        if (!testIssues?.isEmpty()) {
            this.steps.echo ("testissues: ${testIssues}")
            def buildParams = this.project.buildParams

            def versionId = this.project.version?.id ?: "-1"
            def testCycles = this.zephyr.getTestCycles(this.project.id, versionId)

            this.steps.echo ("testCycles: ${testCycles}")
            // Zephyr test cycle properties
            def name = buildParams.targetEnvironmentToken + ": Build " + this.steps.env.BUILD_ID
            def build = this.steps.env.BUILD_URL
            def environment = buildParams.targetEnvironment

            this.steps.echo ("testCyclesName: ${name} / ${build} / ${environment}")
            testCycleId = testCycles.find { it.value instanceof Map && it.value.name == name && it.value.build == build && it.value.environment == environment }?.key
            if (!testCycleId) {
                testCycleId = this.zephyr.createTestCycle(this.project.id, versionId, name, build, environment).id
            }
            this.steps.echo ("testcycleId ${testCycleId}")
        }

        testIssues.each { testIssue ->
            // Create a new execution with status UNEXECUTED
            def testExecutionId = this.zephyr.createTestExecutionForIssue(testIssue.id, this.project.id, testCycleId).keySet().first()
            this.steps.echo ("${testIssue} ${testExecutionId}")
            
            testResults.testsuites.each { testSuite ->
                testSuite.testcases.each { testCase ->
                    this.steps.echo ("Testcase ${testCase}")
                  
                    if (this.usecase.checkTestsIssueMatchesTestCase(testIssue, testCase)) {
                        this.steps.echo ("found matching ${testCase}")
                        def failed = testCase.error || testCase.failure
                        def skipped = testCase.skipped
                        def succeeded = !(testCase.error || testCase.failure || testCase.skipped)
                        this.steps.echo ("> ${failed} / ${skipped} / ${succeeded}")
                        
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
