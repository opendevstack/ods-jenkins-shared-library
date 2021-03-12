package org.ods.orchestration.usecase

import org.ods.orchestration.parser.JUnitParser
import org.ods.orchestration.service.JiraService
import org.ods.util.IPipelineSteps
import org.ods.util.ILogger
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.orchestration.util.Project.JiraDataItem

@SuppressWarnings(['IfStatementBraces', 'LineLength'])
class JiraUseCase {

    class IssueTypes {
        static final String DOCUMENTATION_TRACKING = 'Documentation'
        static final String DOCUMENTATION_CHAPTER = 'Documentation Chapter'
        static final String RELEASE_STATUS = 'Release Status'
    }

    class CustomIssueFields {
        static final String CONTENT = 'EDP Content'
        static final String HEADING_NUMBER = 'EDP Heading Number'
        static final String DOCUMENT_VERSION = 'Document Version'
        static final String RELEASE_VERSION = 'ProductRelease Version'
    }

    class LabelPrefix {
        static final String DOCUMENT = 'Doc:'
    }

    enum TestIssueLabels {
        Error,
        Failed,
        Missing,
        Skipped,
        Succeeded
    }

    private Project project
    private JiraService jira
    private IPipelineSteps steps
    private AbstractJiraUseCaseSupport support
    private MROPipelineUtil util
    private ILogger logger

    JiraUseCase(Project project, IPipelineSteps steps, MROPipelineUtil util, JiraService jira, ILogger logger) {
        this.project = project
        this.steps = steps
        this.util = util
        this.jira = jira
        this.logger = logger
    }

    void setSupport(AbstractJiraUseCaseSupport support) {
        this.support = support
    }

    void applyXunitTestResultsAsTestIssueLabels(List testIssues, Map testResults) {
        if (!this.jira) return

        // Handle Jira test issues for which a corresponding test exists in testResults
        def matchedHandler = { result ->
            result.each { testIssue, testCase ->
                def issueLabels = [TestIssueLabels.Succeeded as String]
                if (testCase.skipped || testCase.error || testCase.failure) {
                    if (testCase.error) {
                        issueLabels = [TestIssueLabels.Error as String]
                    }

                    if (testCase.failure) {
                        issueLabels = [TestIssueLabels.Failed as String]
                    }

                    if (testCase.skipped) {
                        issueLabels = [TestIssueLabels.Skipped as String]
                    }
                }

                this.jira.removeLabelsFromIssue(testIssue.key, TestIssueLabels.values().collect { it.toString() })
                this.jira.addLabelsToIssue(testIssue.key, issueLabels)
            }
        }

        // Handle Jira test issues for which no corresponding test exists in testResults
        def unmatchedHandler = { result ->
            result.each { testIssue ->
                this.jira.removeLabelsFromIssue(testIssue.key, TestIssueLabels.values().collect { it.toString() })
                this.jira.addLabelsToIssue(testIssue.key, [TestIssueLabels.Missing as String])
            }
        }

        this.matchTestIssuesAgainstTestResults(testIssues, testResults, matchedHandler, unmatchedHandler)
    }

    boolean checkTestsIssueMatchesTestCase(Map testIssue, Map testCase) {
        def issueKeyClean = testIssue.key.replaceAll('-', '')
        return testCase.name.startsWith("${issueKeyClean} ") ||
            testCase.name.startsWith("${issueKeyClean}-") ||
            testCase.name.startsWith("${issueKeyClean}_")
    }

    String convertHTMLImageSrcIntoBase64Data(String html) {
        def server = this.jira.baseURL

        def pattern = ~/src="(${server}.*?\.(?:gif|GIF|jpg|JPG|jpeg|JPEG|png|PNG))"/
        def result = html.replaceAll(pattern) { match ->
            def src = match[1]
            def img = this.jira.getFileFromJira(src)
            return "src=\"data:${img.contentType};base64,${img.data.encodeBase64()}\""
        }

        return result
    }

    void createBugsForFailedTestIssues(List testIssues, Set testFailures, String comment) {
        if (!this.jira) return

        testFailures.each { failure ->
            // FIXME: this.project.versionFromReleaseStatusIssue loads data from Jira and should therefore be called not more
            // than once. However, it's also called via this.getVersionFromReleaseStatusIssue in Project.groovy.
            def bug = this.jira.createIssueTypeBug(
                this.project.jiraProjectKey, failure.type, failure.text, this.project.versionFromReleaseStatusIssue)

            // Maintain a list of all Jira test issues affected by the current bug
            def bugAffectedTestIssues = [:]
            this.walkTestIssuesAndTestResults(testIssues, failure) { testIssue, testCase, isMatch ->
                // Find the testcases within the current failure that corresponds to a Jira test issue
                if (isMatch) {
                    // Add a reference to the current bug to the Jira test issue
                    if (null == testIssue.bugs) {
                        testIssue.bugs = []
                    }
                    testIssue.bugs << bug.key

                    // Add a link to the current bug on the Jira test issue (within Jira)
                    this.jira.createIssueLinkTypeBlocks(bug, testIssue)

                    bugAffectedTestIssues << [(testIssue.key): testIssue]
                }
            }

            // Create a JiraDataItem from the newly created bug
            def bugJiraDataItem = new JiraDataItem(project, [ // add project reference for access to Project.JiraDataItem
              key: bug.key,
              name: failure.type,
              assignee: "Unassigned",
              dueDate: "",
              status: "TO DO",
              tests: bugAffectedTestIssues.keySet() as List
            ], Project.JiraDataItem.TYPE_BUGS)

            // Add JiraDataItem into the Jira data structure
            this.project.data.jira.bugs[bug.key] = bugJiraDataItem

            // Add the resolved JiraDataItem into the Jira data structure
            this.project.data.jiraResolved.bugs[bug.key] = bugJiraDataItem.cloneIt()
            this.project.data.jiraResolved.bugs[bug.key].tests = bugAffectedTestIssues.values() as List

            this.jira.appendCommentToIssue(bug.key, comment)
        }
    }

    /**
     * Obtains all document chapter data attached attached to a given version
     * @param versionName the version name from jira
     * @return Map (key: issue) with all the document chapter issues and its relevant content
     */
    @SuppressWarnings(['AbcMetric'])
    Map<String, Map> getDocumentChapterData(String projectKey, String versionName = null) {
        if (!this.jira) return [:]

        def docChapterIssueFields = this.project.getJiraFieldsForIssueType(JiraUseCase.IssueTypes.DOCUMENTATION_CHAPTER)
        def contentField = docChapterIssueFields[CustomIssueFields.CONTENT].id
        def headingNumberField = docChapterIssueFields[CustomIssueFields.HEADING_NUMBER].id

        def jql = "project = ${projectKey} " +
            "AND issuetype = '${JiraUseCase.IssueTypes.DOCUMENTATION_CHAPTER}'"

        if (versionName) {
            jql = jql + " AND fixVersion = '${versionName}'"
        }

        def jqlQuery = [
            fields: ['key', 'status', 'summary', 'labels', 'issuelinks', contentField, headingNumberField],
            jql: jql,
            expand: ['renderedFields'],
        ]

        def result = this.jira.searchByJQLQuery(jqlQuery)
        if (!result || result.total == 0) {
            this.logger.warn("There are no document chapters assigned to this version. Using JQL query: '${jqlQuery}'.")
            return [:]
        }

        return result.issues.collectEntries { issue ->
            def number = issue.fields.find { field ->
                headingNumberField == field.key && field.value
            }
            if (!number) {
                throw new IllegalArgumentException("Error: could not find heading number for issue '${issue.key}'.")
            }
            number = number.getValue().trim()

            def content = issue.renderedFields.find { field ->
                contentField == field.key && field.value
            }
            content = content ? content.getValue() : ""

            def documentTypes = (issue.fields.labels ?: [])
                .findAll{String l -> l.startsWith(LabelPrefix.DOCUMENT)}
                .collect{String l -> l.replace(LabelPrefix.DOCUMENT, '')}
            if (documentTypes.size() == 0) {
                throw new IllegalArgumentException("Error: issue '${issue.key}' of type " +
                    "'${JiraUseCase.IssueTypes.DOCUMENTATION_CHAPTER}' contains no " +
                    "document labels. There should be at least one label starting with '${LabelPrefix.DOCUMENT}'")
            }


            def predecessorLinks = issue.fields.issuelinks
                .findAll { it.type.name == "Succeeds" && it.outwardIssue?.key }
                .collect{ it.outwardIssue.key }

            return [(issue.key as String): [
                    section: "sec${number.replaceAll(/\./, "s")}".toString(),
                    number: number,
                    heading: issue.fields.summary,
                    documents: documentTypes,
                    content: content?.replaceAll("\u00a0", " ") ?: "",
                    status: issue.fields.status.name,
                    key: issue.key as String,
                    predecessors: predecessorLinks.isEmpty()? [] : predecessorLinks,
                    versions: versionName? [versionName] : [],
                ]
            ]
        }
    }

    String getVersionFromReleaseStatusIssue() {
        if (!this.jira) return ""

        def releaseStatusIssueKey = this.project.buildParams.releaseStatusJiraIssueKey as String
        def releaseStatusIssueFields = this.project.getJiraFieldsForIssueType(JiraUseCase.IssueTypes.RELEASE_STATUS)

        def productReleaseVersionField = releaseStatusIssueFields[CustomIssueFields.RELEASE_VERSION]
        def versionField = this.jira.getTextFieldsOfIssue(releaseStatusIssueKey, [productReleaseVersionField.id])
        if (!versionField || !versionField[productReleaseVersionField.id]?.name) {
            throw new IllegalArgumentException('Unable to obtain version name from release status issue' +
                " ${releaseStatusIssueKey}. Please check that field with name" +
                " '${productReleaseVersionField.name}' and id '${productReleaseVersionField.id}' " +
                'has a correct version value.')
        }

        return versionField[productReleaseVersionField.id].name
    }

    void matchTestIssuesAgainstTestResults(List testIssues, Map testResults, Closure matchedHandler, Closure unmatchedHandler = null) {
        def result = [
            matched: [:],
            unmatched: []
        ]

        this.walkTestIssuesAndTestResults(testIssues, testResults) { testIssue, testCase, isMatch ->
            if (isMatch) {
                result.matched << [
                    (testIssue): testCase
                ]
            }
        }

        testIssues.each { testIssue ->
            if (!result.matched.keySet().contains(testIssue)) {
                result.unmatched << testIssue
            }
        }

        if (matchedHandler) {
            matchedHandler(result.matched)
        }

        if (unmatchedHandler) {
            unmatchedHandler(result.unmatched)
        }
    }

    void reportTestResultsForComponent(String componentName, List<String> testTypes, Map testResults) {
        if (!this.jira) return

        def testLevel = "${componentName ?: 'project'}"
        if (logger.debugMode) {
            logger.debug('Reporting unit test results to corresponding test cases in Jira for' +
              " '${testLevel}' type: '${testTypes}'\rresults: ${testResults}")
        }

        logger.startClocked("${testLevel}-jira-fetch-tests-${testTypes}")
        def testIssues = this.project.getAutomatedTests(componentName, testTypes)
        logger.debugClocked("${testLevel}-jira-fetch-tests-${testTypes}",
            "Found automated tests for ${(componentName ?: 'project')} type: ${testTypes}: " +
            "${testIssues?.size()}")

        this.util.warnBuildIfTestResultsContainFailure(testResults)
        this.matchTestIssuesAgainstTestResults(testIssues, testResults, null) { unexecutedJiraTests ->
            if (!unexecutedJiraTests.isEmpty()) {
                this.util.warnBuildAboutUnexecutedJiraTests(unexecutedJiraTests)
            }
        }

        logger.startClocked("${testLevel}-jira-report-tests-${testTypes}")
        this.support.applyXunitTestResults(testIssues, testResults)
        logger.debugClocked("${testLevel}-jira-report-tests-${testTypes}")
        if (['Q', 'P'].contains(this.project.buildParams.targetEnvironmentToken)) {
            logger.startClocked("${testLevel}-jira-report-bugs-${testTypes}")
            // Create bugs for erroneous test issues
            def errors = JUnitParser.Helper.getErrors(testResults)
            this.createBugsForFailedTestIssues(testIssues, errors, this.steps.env.RUN_DISPLAY_URL)

            // Create bugs for failed test issues
            def failures = JUnitParser.Helper.getFailures(testResults)
            this.createBugsForFailedTestIssues(testIssues, failures, this.steps.env.RUN_DISPLAY_URL)
            logger.debugClocked("${testLevel}-jira-report-bugs-${testTypes}")
        }
    }

    void reportTestResultsForProject(List<String> testTypes, Map testResults) {
        // No componentName passed to method to get all automated issues from project
        this.reportTestResultsForComponent(
            null, testTypes, testResults)
    }

    void updateJiraReleaseStatusBuildNumber() {
        if (!this.jira) return

        def releaseStatusIssueKey = this.project.buildParams.releaseStatusJiraIssueKey
        def releaseStatusIssueFields = this.project.getJiraFieldsForIssueType(JiraUseCase.IssueTypes.RELEASE_STATUS)

        def releaseStatusIssueBuildNumberField = releaseStatusIssueFields['Release Build']
        this.jira.updateTextFieldsOnIssue(releaseStatusIssueKey, [(releaseStatusIssueBuildNumberField.id): "${this.project.buildParams.version}-${this.steps.env.BUILD_NUMBER}"])
    }

    void updateJiraReleaseStatusResult(String message, boolean isError) {
        if (!this.jira) return

        def status = isError ? 'Failed' : 'Successful'

        def releaseStatusIssueKey = this.project.buildParams.releaseStatusJiraIssueKey
        def releaseStatusIssueFields = this.project.getJiraFieldsForIssueType(JiraUseCase.IssueTypes.RELEASE_STATUS)

        def releaseStatusIssueReleaseManagerStatusField = releaseStatusIssueFields['Release Manager Status']
        this.jira.updateSelectListFieldsOnIssue(releaseStatusIssueKey, [(releaseStatusIssueReleaseManagerStatusField.id): status])

        logger.startClocked("jira-update-release-${releaseStatusIssueKey}")
        addCommentInReleaseStatus(message)
        logger.debugClocked("jira-update-release-${releaseStatusIssueKey}")
    }

    void addCommentInReleaseStatus(String message) {
        def releaseStatusIssueKey = this.project.buildParams.releaseStatusJiraIssueKey
        if (message) {
            this.jira.appendCommentToIssue(releaseStatusIssueKey, "${message}\n\nSee: ${this.steps.env.RUN_DISPLAY_URL}")
        }

    }

    Long getLatestDocVersionId(List<Map> trackingIssues) {
        def documentationTrackingIssueFields = this.project.getJiraFieldsForIssueType(IssueTypes.DOCUMENTATION_TRACKING)
        def documentVersionField = documentationTrackingIssueFields[CustomIssueFields.DOCUMENT_VERSION].id as String

        // We will use the biggest ID available
        def versionList = trackingIssues.collect { issue ->
            def versionNumber = 0L

            def version = this.jira.getTextFieldsOfIssue(issue.key as String, [documentVersionField])?.getAt(documentVersionField)
            if (version) {
                try {
                    versionNumber = version.toLong()
                } catch (NumberFormatException _) {
                    this.logger.warn("Document tracking issue '${issue.key}' does not contain a valid numerical" +
                        "version. It contains value '${version}'.")
                }
            }

            return versionNumber
        }

        def result = versionList.max()
        logger.debug("Retrieved max doc version ${versionList.max()} from doc tracking issues " +
            "${trackingIssues.collect { it.key } }")

        return result
    }

    private void walkTestIssuesAndTestResults(List testIssues, Map testResults, Closure visitor) {
        testResults.testsuites.each { testSuite ->
            testSuite.testcases.each { testCase ->
                def testIssue = testIssues.find { testIssue ->
                    this.checkTestsIssueMatchesTestCase(testIssue, testCase)
                }

                def isMatch = testIssue != null
                visitor(testIssue, testCase, isMatch)
            }
        }
    }
}
