package org.ods.usecase

import org.ods.parser.JUnitParser
import org.ods.service.JiraService
import org.ods.util.IPipelineSteps

class JiraUseCase {

    static final List JIRA_TEST_CASE_LABELS = [ "Error", "Failed", "Missing", "Skipped", "Succeeded" ]

    private JiraService jira
    private IPipelineSteps steps

    JiraUseCase(IPipelineSteps steps, JiraService jira) {
        this.steps = steps
        this.jira = jira
    }

    private boolean checkJiraIssueMatchesTestCase(Map issue, String testcaseName) {
        def issueKeyClean = issue.key.replaceAll("-", "")
        return testcaseName.startsWith("${issueKeyClean} ") || testcaseName.startsWith("${issueKeyClean}-") || testcaseName.startsWith("${issueKeyClean}_")
    }

    void createBugAndBlockImpactedTestCases(String projectId, Map jiraTestCaseIssues, Map failure, String comment) {
        if (!this.jira) return

        // Create a Jira bug for the failure in the current project
        def bug = this.jira.createIssueTypeBug(projectId, failure.type, failure.text)
        this.jira.appendCommentToIssue(bug.key, comment)

        // Iterate through all test cases affected by this failure
        failure.testsuites.each { testsuiteName, testsuite ->
            testsuite.testcases.each { testcaseName ->
                // Find the issue in Jira representing the test case
                def jiraTestCaseIssue = jiraTestCaseIssues.find { id, issue ->
                    checkJiraIssueMatchesTestCase(issue, testcaseName)
                }

                // Attempt to create a link to the bug only if the issue exists
                if (jiraTestCaseIssue) {
                    this.jira.createIssueLinkTypeBlocks(bug, jiraTestCaseIssue.value)
                }
            }
        }
    }

    void labelTestCasesWithTestResults(Map jiraTestCaseIssues, Map testResults) {
        if (!this.jira) return

        def testCasesProcessed = [:]
        testResults.each { testsuiteName, testsuite ->
            testsuite.each { testcaseName, testcase ->
                // Find the test case issue in Jira that matches the executed test case
                def jiraTestCaseIssue = jiraTestCaseIssues.find { issueId, issue ->
                    checkJiraIssueMatchesTestCase(issue, testcaseName)
                }

                if (!jiraTestCaseIssue) {
                    // There is no issue for the executed test case in Jira
                    return
                }

                jiraTestCaseIssue = jiraTestCaseIssue.value

                // Remove all labels from the test case issue in Jira
                this.jira.removeLabelsFromIssue(jiraTestCaseIssue.id, JIRA_TEST_CASE_LABELS)

                def labelsToApply = ["Succeeded"]
                if (testcase.skipped || testcase.error || testcase.failure) {
                    if (testcase.error) {
                        labelsToApply = ["Error"]
                    }

                    if (testcase.failure) {
                        labelsToApply = ["Failed"]
                    }

                    if (testcase.skipped) {
                        labelsToApply = ["Skipped"]
                    }
                }

                // Apply all appropriate labels to the issue
                this.jira.addLabelsToIssue(jiraTestCaseIssue.id, labelsToApply)

                testCasesProcessed << [ (jiraTestCaseIssue.id): jiraTestCaseIssue ]
            }
        }

        // Add label "Missing" to all unprocessed test case issues in Jira
        def testCasesUnprocessed = jiraTestCaseIssues - testCasesProcessed
        testCasesUnprocessed.each { issueId, issue ->
            this.jira.removeLabelsFromIssue(issueId, JIRA_TEST_CASE_LABELS)
            this.jira.addLabelsToIssue(issueId, ["Missing"])
        }
    }

    void notifyLeVaDocumentIssue(String projectId, String documentType, String message) {
        if (!this.jira) return

        def jqlQuery = "project = ${projectId} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}"

        // Search for the Jira issue associated with the document
        def jiraIssues = JiraService.Helper.toSimpleIssuesFormat(this.jira.getIssuesForJQLQuery(jqlQuery))
        if (jiraIssues.size() != 1) {
            throw new RuntimeException("Error: Jira query returned ${jiraIssues.size()} issues: '${jqlQuery}'.")
        }

        // Add a comment to the Jira issue with a link to the report
        this.jira.appendCommentToIssue(jiraIssues.iterator().next().value.key, message)
    }

    void reportTestResultsForProject(String projectId, Map testResults) {
        if (!this.jira) return

        // Transform the JUnit test results into a simple format
        def testResultsSimple = JUnitParser.Helper.toSimpleFormat(testResults)

        // Get (automated) test case definitions from Jira
        def jiraTestCaseIssues = JiraService.Helper.toSimpleIssuesFormat(
            this.jira.getIssuesForJQLQuery("project = ${projectId} AND labels = AutomatedTest AND issuetype = Test")
        )

        // Label test cases according to their test results
        this.labelTestCasesWithTestResults(jiraTestCaseIssues, testResultsSimple)

        // Create Jira bugs for erroneous test cases
        def errors = JUnitParser.Helper.toSimpleErrorsFormat(testResultsSimple)
        errors.each { error ->
            this.createBugAndBlockImpactedTestCases(projectId, jiraTestCaseIssues, error, this.steps.env.RUN_DISPLAY_URL)
        }

        // Create Jira bugs for failed test cases
        def failures = JUnitParser.Helper.toSimpleFailuresFormat(testResultsSimple)
        failures.each { failure ->
            this.createBugAndBlockImpactedTestCases(projectId, jiraTestCaseIssues, failure, this.steps.env.RUN_DISPLAY_URL)
        }
    }
}
