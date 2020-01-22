package org.ods.usecase

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
    private AbstractJiraUseCaseSupport support

    JiraUseCase(IPipelineSteps steps, JiraService jira) {
        this.steps = steps
        this.jira = jira
    }

    void setSupport(AbstractJiraUseCaseSupport support) {
        this.support = support
    }

    void applyTestResultsAsTestIssueLabels(List jiraTestIssues, Map testResults) {
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
                this.jira.removeLabelsFromIssue(issue.id, this.JIRA_TEST_CASE_LABELS)
                this.jira.addLabelsToIssue(issue.id, ["Missing"])
            }
        }

        this.matchJiraTestIssuesAgainstTestResults(jiraTestIssues, testResults, matchedHandler, unmatchedHandler)
    }

    boolean checkJiraIssueMatchesTestCase(Map issue, String testcaseName) {
        def issueKeyClean = issue.key.replaceAll("-", "")
        return testcaseName.startsWith("${issueKeyClean} ") || testcaseName.startsWith("${issueKeyClean}-") || testcaseName.startsWith("${issueKeyClean}_")
    }

    private String convertHTMLImageSrcIntoBase64Data(String html) {
        def server = this.jira.baseURL

        def pattern = ~/src="(${server}.*\.(?:gif|jpg|jpeg|png))"/
        def result = html.replaceAll(pattern) { match ->
            def src = match[1]
            def img = this.jira.getFileFromJira(src)
            return "src=\"data:${img.contentType};base64,${img.data.encodeBase64()}\""
        }

        return result
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

    List getAutomatedTestIssues(String projectId, String componentName = null, List<String> labelsSelector = []) {
        return this.support.getAutomatedTestIssues(projectId, componentName, labelsSelector)
    }

    List getAutomatedAcceptanceTestIssues(String projectId, String componentName = null) {
        return this.support.getAutomatedAcceptanceTestIssues(projectId, componentName)
    }

    List getAutomatedInstallationTestIssues(String projectId, String componentName = null) {
        return this.support.getAutomatedInstallationTestIssues(projectId, componentName)
    }

    List getAutomatedIntegrationTestIssues(String projectId, String componentName = null) {
        return this.support.getAutomatedIntegrationTestIssues(projectId, componentName)
    }

    List getAutomatedUnitTestIssues(String projectId, String componentName = null) {
        return this.support.getAutomatedUnitTestIssues(projectId, componentName)
    }

    Map getDocumentChapterData(String projectId, String documentType) {
        if (!this.jira) return [:]

        def jqlQuery = [
            jql: "project = ${projectId} AND issuetype = '${IssueTypes.DOCUMENT_CHAPTER}' AND labels = LeVA_Doc:${documentType}",
            expand: ["names", "renderedFields"]
        ]

        def result = this.jira.searchByJQLQuery(jqlQuery)
        if (!result) return [:]

        def numberKey = result.names.find { it.value == CustomIssueFields.HEADING_NUMBER }.key
        def contentFieldKey = result.names.find { it.value == CustomIssueFields.CONTENT }.key

        return result.issues.collectEntries { issue ->
            def number = issue.fields[numberKey]?.trim()

            def content = issue.renderedFields[contentFieldKey]
            if (content.contains("<img")) {
                content = convertHTMLImageSrcIntoBase64Data(content)
            }

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

    Map getIssuesForEpics(List<String> epicKeys, List<String> issueTypes = []) {
        def result = [:]
        if (!this.jira) result

        def epicLinkField = this.jira.getFields().find { it.name == "Epic Link" }
        if (!epicLinkField) {
            throw new RuntimeException("Error: unable to get Jira field 'Epic Link'. Jira returned an empty reponse.")
        }

        def query = "'Epic Link' in (" +  epicKeys.join(", ") + ")"
        if (issueTypes) {
            query += " AND issuetype in (" + issueTypes.collect { "'${it}'" }.join(", ") + ")"
        }

        def jqlQuery = [
            jql: query,
            expand: ["renderedFields"],
            fields: [epicLinkField.id, "description", "summary"]
        ]

        def issues = this.jira.getIssuesForJQLQuery(jqlQuery)
        issues.each { issue ->
            // Derive the epicKey through the Epic Link field's id
            def epicKey = issue.fields[epicLinkField.id]

            if (!result[epicKey]) {
                result[epicKey] = []
            }

            result[epicKey] << toSimpleIssue(issue)
        }

        return result
    }

    Map getIssuesForProject(String projectKey, String componentName = null, List<String> issueTypesSelector = [], List<String> labelsSelector = [], boolean throwOnMissingLinks = false, Closure issueLinkFilter = null) {
        def result = [:]
        if (!this.jira) return result

        // Fetch the component's issues and filter by issuetype
        def query = "project = ${projectKey}"

        if (componentName) {
            query += " AND component = '${componentName}'"
        }

        if (issueTypesSelector) {
            query += " AND issuetype in (" + issueTypesSelector.collect{"'${it}'"}.join(", ") +  ")"
        }

        labelsSelector.each {
            query += " AND labels = '${it}'"
        }

        def linkedIssuesKeys = [] as Set
        def issueTypeEpicKeys = []
        def issuesWithoutLinks = [] as Set

        def issues = this.jira.getIssuesForJQLQuery([
            jql: query,
            expand: ["renderedFields"],
            fields: ["components", "description", "issuelinks", "issuetype", "summary"]
        ])

        issues.each { issue ->
            // Construct simple issue link representations and filter issue links if applicable
            issue.fields.issuelinks = issue.fields.issuelinks.collect { toSimpleIssueLink(it) }
            if (issueLinkFilter) {
                issue.fields.issuelinks.retainAll { issueLinkFilter(it) }
            }

            if (!issue.fields.issuelinks.isEmpty()) {
                // Track keys of linked issues for later retrieval
                linkedIssuesKeys.addAll(
                    issue.fields.issuelinks.collect { issuelink ->
                        // Resolve the linked issue
                        return issuelink.issue.key
                    }
                )
            } else {
                // Track issues that have no issue links
                issuesWithoutLinks << issue.key
            }

            // Track issues of type Epic
            if (issue.fields.issuetype.name == "Epic") {
                issueTypeEpicKeys << issue.key
            }
        }

        // Escalate the existence of issues without links to other issues if applicable
        if (!issuesWithoutLinks.isEmpty() && throwOnMissingLinks) {
            throw new RuntimeException("Error: links are missing for issues: ${issuesWithoutLinks.join(', ')}.")
        }

        // Fetch the Epics' issues if applicable
        def issuesInEpics = [:]
        if (!issueTypeEpicKeys.isEmpty()) {
            issuesInEpics = this.getIssuesForEpics(issueTypeEpicKeys, ["Story"])
        }

        // Fetch the linked issues if applicable
        def linkedIssues = [:]
        if (!linkedIssuesKeys.isEmpty()) {
            linkedIssues = this.jira.getIssuesForJQLQuery([
                jql: "key in (" + linkedIssuesKeys.join(", ") + ")",
                expand: ["renderedFields"],
                fields: ["description"]
            ]).collectEntries { [ (it.key.toString()): it ] }
        }

        issues.each { issue ->
            // Construct a simple issue representation
            def simpleIssue = toSimpleIssue(issue)

            simpleIssue.issuelinks = issue.fields.issuelinks.collect { issuelink ->
                if (linkedIssues[issuelink.issue.key]) {
                    // Mix-in any issues linked to the current issue
                    issuelink.issue += toSimpleIssue(linkedIssues[issuelink.issue.key])
                }

                return issuelink
            }

            if (issue.fields.issuetype.name == "Epic") {
                // Mix-in any issues contained in the Epic
                simpleIssue.issues = issuesInEpics[issue.key] ?: []
            }

            if (issue.fields.components.isEmpty()) {
                issue.fields.components << [ name: "undefined" ]
            }

            issue.fields.components.each { component ->
                if (!result[component.name]) {
                    result[component.name] = []
                }

                if (issueLinkFilter) {
                    // Return issue iff filtering yielded a non-empty result
                    if (!simpleIssue.issuelinks.isEmpty()) {
                        result[component.name] << simpleIssue
                    }
                } else {
                    result[component.name] << simpleIssue
                }
            }
        }

        return result
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

    void reportTestResultsForComponent(String projectId, String componentName, List<String> testTypes, Map testResults) {
        if (!this.jira) return

        // Get automated test case definitions from Jira
        def jiraTestIssues = this.getAutomatedTestIssues(projectId, componentName, testTypes)

        // Apply test results to the test case definitions in Jira
        this.support.applyTestResultsToTestIssues(jiraTestIssues, testResults)

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

    static Map toSimpleIssue(Map issue, Map mixins = [:]) {
        def result = [
            id: issue.id,
            key: issue.key,
            summary: issue.fields.summary
        ]

        if (issue.fields.components) {
            result.components = issue.fields.components.collect { it.name }
        }

        if (issue.fields.issuetype) {
            result.issuetype = [
                name: issue.fields.issuetype.name
            ]
        }

        if (issue.fields.status) {
            result.status = [
                name: issue.fields.status.name
            ]
        }

        result.description = (issue.renderedFields?.description) ? issue.renderedFields.description : issue.fields.description
        if (result.description) {
            result.description = result.description.replaceAll("\u00a0", " ")
        }
              
        return result << mixins
    }

    static Map toSimpleIssueLink(Map issuelink, Map mixins = [:]) {
        def result = [
            id: issuelink.id,
            type: [
                name: issuelink.type.name
            ]
        ]

        if (issuelink.inwardIssue) {
            result.issue = toSimpleIssue(issuelink.inwardIssue)
            result.type.relation = issuelink.type.inward

        }

        if (issuelink.outwardIssue) {
            result.issue = toSimpleIssue(issuelink.outwardIssue)
            result.type.relation = issuelink.type.outward
        }

        return result << mixins
    }
}
