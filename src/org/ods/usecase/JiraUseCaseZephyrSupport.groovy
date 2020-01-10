package org.ods.usecase

import com.cloudbees.groovy.cps.NonCPS

import org.ods.service.JiraZephyrService
import org.ods.util.IPipelineSteps
import org.ods.util.SortUtil

class JiraUseCaseZephyrSupport extends AbstractJiraUseCaseSupport {

    private JiraZephyrService zephyr

    JiraUseCaseZephyrSupport(IPipelineSteps steps, JiraUseCase usecase, JiraZephyrService zephyr) {
        super(steps, usecase)
        this.zephyr = zephyr
    }

    void applyTestResultsAsTestExecutionStatii(List jiraTestIssues, Map testResults) {
        if (!this.usecase.jira) return
        if (!this.zephyr) return

        jiraTestIssues.each { issue ->
            // Create a new execution with status UNEXECUTED
            def execution = this.zephyr.createTestExecutionForIssue(issue.id, issue.projectId).keySet().first()

            testResults.testsuites.each { testsuite ->
                testsuite.testcases.each { testcase ->
                    if (this.usecase.checkJiraIssueMatchesTestCase(issue, testcase.name)) {
                        def failed = testcase.error || testcase.failure
                        def skipped = testcase.skipped
                        def succeeded = !(testcase.error || testcase.failure || testcase.skipped)

                        if (succeeded) {
                            this.zephyr.updateExecutionForIssuePass(execution)
                        } else if (failed) {
                            this.zephyr.updateExecutionForIssueFail(execution)
                        } else if (skipped) {
                            this.zephyr.updateExecutionForIssueBlocked(execution)
                        }
                    }
                }
            }
        }
    }

    void applyTestResultsToTestIssues(List jiraTestIssues, Map testResults) {
        this.usecase.applyTestResultsAsTestIssueLabels(jiraTestIssues, testResults)
        this.applyTestResultsAsTestExecutionStatii(jiraTestIssues, testResults)
    }

    List getAutomatedTestIssues(String projectId, String componentName = null, List<String> labelsSelector = []) {
        if (!this.usecase.jira) return []
        if (!this.zephyr) return []

        def project = this.zephyr.getProject(projectId)

        def issues = this.usecase.getIssuesForProject(projectId, componentName, ["Test"], labelsSelector << "AutomatedTest", false) { issuelink ->
            return issuelink.type.relation == "is related to" && (issuelink.issue.issuetype.name == "Epic" || issuelink.issue.issuetype.name == "Story")
        }.values().flatten()

        return issues.each { issue ->
            issue.test = [
                description: issue.description,
                details: []
            ]

            def testDetails = this.zephyr.getTestDetailsForIssue(issue.id).collect { testDetails ->
                return testDetails.subMap(["step", "data", "result"])
            }

            issue.test.details = SortUtil.sortIssuesByProperties(testDetails, ["orderId"])

            // The project ID (not key) is mandatory to generate new executions
            issue.projectId = project.id
        }
    }
}
