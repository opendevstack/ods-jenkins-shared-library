package org.ods.usecase

import groovy.json.JsonOutput

import org.ods.parser.JUnitParser
import org.ods.service.JiraService
import org.ods.util.IPipelineSteps

class JiraUseCase {

    class IssueTypes {
        static final String DOCUMENT_CHAPTER = "Documentation Chapter"
        static final String LEVA_DOCUMENTATION = "LeVA Documentation"
    }

    class CustomIssueFields {
        static final String CONTENT = "Content"
        static final String HEADING_NUMBER = "Heading Number"
    }

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

    void createBugsAndBlockImpactedTestCases(String projectId, List jiraTestIssues, Set failures, String comment) {
        if (!this.jira) return

        failures.each { failure ->
            def bug = this.jira.createIssueTypeBug(projectId, failure.type, failure.text)

            walkJiraTestIssuesAndTestResults(jiraTestIssues, failure) { issue, testcase, isMatch ->
                if (isMatch) this.jira.createIssueLinkTypeBlocks(bug, issue)
            }

            this.jira.appendCommentToIssue(bug.key, comment)
        }
    }

    List getAutomatedTestIssues(String projectId, String componentId = null) {
        if (!this.jira) return []

        def query = "project = ${projectId} AND issuetype = Test AND labels = AutomatedTest"
        if (componentId) {
            query += " AND component = '${componentId}'"
        }

        def jqlQuery = [
            jql: query
        ]

        return this.jira.getIssuesForJQLQuery(jqlQuery).each { issue ->
            issue.isRelatedTo = this.getLinkedIssuesForIssue(issue.key, "is related to") ?: []
        }
    }

    Map getDocumentChapterData(String projectId, String documentType) {
        if (!this.jira) return [:]

        def jqlQuery = [
            jql: "project = ${projectId} AND issuetype = '${IssueTypes.DOCUMENT_CHAPTER}' AND labels = LeVA_Doc:${documentType}",
            expand: [ "names", "renderedFields" ]
        ]

        def result = this.jira.searchByJQLQuery(jqlQuery)

        def contentFieldKey = result.names.find { it.value == CustomIssueFields.CONTENT }.key
        def numberKey = result.names.find { it.value == CustomIssueFields.HEADING_NUMBER }.key

        return result.issues.collectEntries { issue ->
            def content = issue.renderedFields[contentFieldKey]
            def number = issue.fields[numberKey]?.trim()

            return [
                "sec${number.replaceAll(/\./, "s")}".toString(),
                [
                    number: number,
                    heading: issue.fields.summary,
                    content: content?.replaceAll("\u00a0", " ") ?: ""
                ]
            ]
        }
    }

    List getLinkedIssuesForIssue(String issueKey, String relationType = "") {
        if (!this.jira) return []

        def query = "issue in linkedIssues('${issueKey}'"

        if (relationType.trim()) {
            query += ", '${relationType}'"
        }

        query += ")"

        def jqlQuery = [
            jql: query
        ]

        return this.jira.getIssuesForJQLQuery(jqlQuery)
    }

    List getStoriesInEpic(String epicKey) {
        if (!this.jira) return []

        def jqlQuery = [
            jql: "issuetype = Story AND 'Epic Link' = ${epicKey}"
        ]

        return this.jira.getIssuesForJQLQuery(jqlQuery)
    }

    void labelTestIssuesWithTestResults(List jiraTestIssues, Map testResults) {
        if (!this.jira) return

        // Handle Jira issues for which a corresponding test exists in testResults
        def matchedHandler = { result ->
            result.each { issue, testcase ->
                this.jira.removeLabelsFromIssue(issue.id, this.JIRA_TEST_CASE_LABELS)

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

                this.jira.addLabelsToIssue(issue.id, labelsToApply)
            }
        }

        // Handle Jira issues for which no corresponding test exists in testResults
        def unmatchedHandler = { result ->
            result.each { issue ->
                this.jira.removeLabelsFromIssue(issue.id, JIRA_TEST_CASE_LABELS)
                this.jira.addLabelsToIssue(issue.id, ["Missing"])
            }
        }

        this.matchJiraTestIssuesAgainstTestResults(jiraTestIssues, testResults, matchedHandler, unmatchedHandler)
    }

    void matchJiraTestIssuesAgainstTestResults(List jiraTestIssues, Map testResults, Closure matchedHandler, Closure unmatchedHandler = null) {
        def result = [
            matched: [:],
            mismatched: []
        ]

        walkJiraTestIssuesAndTestResults(jiraTestIssues, testResults) { issue, testcase, isMatch ->
            if (isMatch) {
                result.matched << [
                    (issue): testcase
                ]
            }
        }

        jiraTestIssues.each { issue ->
            if (!result.matched.keySet().contains(issue)) {
                result.mismatched << issue
            }
        }

        matchedHandler(result.matched)
        unmatchedHandler(result.mismatched)
    }

    void notifyLeVaDocumentTrackingIssue(String projectId, String documentType, String message) {
        if (!this.jira) return

        def jqlQuery = [ jql: "project = ${projectId} AND issuetype = '${IssueTypes.LEVA_DOCUMENTATION}' AND labels = LeVA_Doc:${documentType}" ]

        // Search for the Jira issue associated with the document
        def jiraIssues = this.jira.getIssuesForJQLQuery(jqlQuery)
        if (jiraIssues.size() != 1) {
            throw new RuntimeException("Error: Jira query returned ${jiraIssues.size()} issues: '${jqlQuery}'.")
        }

        // Add a comment to the Jira issue with a link to the report
        this.jira.appendCommentToIssue(jiraIssues.first().key, message)
    }

    void reportTestResultsForComponent(String projectId, String componentId, Map testResults) {
        if (!this.jira) return

        // Get automated test case definitions from Jira
        def jiraTestIssues = this.getAutomatedTestIssues(projectId, componentId)

        // Label test cases according to their test results
        this.labelTestIssuesWithTestResults(jiraTestIssues, testResults)

        // Create Jira bugs for erroneous test cases
        def errors = JUnitParser.Helper.getErrors(testResults)
        this.createBugsAndBlockImpactedTestCases(projectId, jiraTestIssues, errors, this.steps.env.RUN_DISPLAY_URL)

        // Create Jira bugs for failed test cases
        def failures = JUnitParser.Helper.getFailures(testResults)
        this.createBugsAndBlockImpactedTestCases(projectId, jiraTestIssues, failures, this.steps.env.RUN_DISPLAY_URL)
    }

    private void walkJiraTestIssuesAndTestResults(List jiraTestIssues, Map testResults, Closure visitor) {
        testResults.testsuites.each { testsuite ->
            testsuite.testcases.each { testcase ->
                def issue = jiraTestIssues.find { issue ->
                    this.checkJiraIssueMatchesTestCase(issue, testcase.name)
                }

                def isMatch = issue != null
                visitor(issue, testcase, isMatch)
            }
        }
    }
}
