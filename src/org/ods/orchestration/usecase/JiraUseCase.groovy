package org.ods.orchestration.usecase

import com.cloudbees.groovy.cps.NonCPS
import org.ods.orchestration.JiraNotPresentException
import org.ods.orchestration.parser.JUnitParser
import org.ods.orchestration.service.JiraService
import org.ods.orchestration.util.ConcurrentCache
import org.ods.util.IPipelineSteps
import org.ods.util.ILogger
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.orchestration.util.Project.JiraDataItem

@SuppressWarnings(['IfStatementBraces', 'LineLength'])
class JiraUseCase {

    private static final String VULNERABILITY_NAME_PLACEHOLDER = "<CVE>"

    private static final String SECURITY_VULNERABILITY_ISSUE_SUMMARY = "Remotely exploitable security " +
        "vulnerability with solution detected by Aqua with name " + VULNERABILITY_NAME_PLACEHOLDER

    private static final String SECURITY_VULNERABILITY_ISSUE_PRIORITY = "Highest"

    private static final String JIRA_COMPONENT_TECHNOLOGY_PREFIX = 'Technology-'

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

    private Project project
    private JiraService jira
    private IPipelineSteps steps
    private AbstractJiraUseCaseSupport support
    private MROPipelineUtil util
    private ILogger logger
    private ConcurrentCache docVersions

    JiraUseCase(Project project, IPipelineSteps steps, MROPipelineUtil util, JiraService jira, ILogger logger) {
        this.project = project
        this.steps = steps
        this.util = util
        this.jira = jira
        this.logger = logger

        def computeIfAbsent = { key ->
            def documentationTrackingIssueFields =
                this.project.getJiraFieldsForIssueType(IssueTypes.DOCUMENTATION_TRACKING)
            def documentVersionField = documentationTrackingIssueFields[CustomIssueFields.DOCUMENT_VERSION].id as String
            def version = 0L
            def versionField =
                this.jira.getTextFieldsOfIssue(key as String, [documentVersionField])?.getAt(documentVersionField)
            if (versionField) {
                try {
                    version = versionField.toLong()
                } catch (NumberFormatException _) {
                    version = 0L
                }
            }
            return version
        }

        this.docVersions = new ConcurrentCache<String, Long>(computeIfAbsent)
    }

    void setSupport(AbstractJiraUseCaseSupport support) {
        this.support = support
    }

    @NonCPS
    void setJira(JiraService jira) {
        this.jira = jira
    }

    @NonCPS
    JiraService getJira() {
        return jira
    }

    void applyXunitTestResultsAsTestIssueLabels(List testIssues, Map testResults) {
        if (!this.jira) return

        // Handle Jira test issues for which a corresponding test exists in testResults
        def matchedHandler = { result ->
            result.each { testIssue, testCase ->
                // Remove all the results from preceding executions
                this.jira.removeLabelsFromIssue(testIssue.key, TestIssueLabels.values().collect() { it.name() })

                def issueLabels = [TestIssueLabels.Succeeded as String]
                if (testCase.error) {
                    issueLabels = [TestIssueLabels.Error as String]
                }

                if (testCase.failure) {
                    issueLabels = [TestIssueLabels.Failed as String]
                }

                if (testCase.skipped) {
                    issueLabels = [TestIssueLabels.Skipped as String]
                }

                this.jira.addLabelsToIssue(testIssue.key, issueLabels)
            }
        }

        // Handle Jira test issues for which no corresponding test exists in testResults
        def unmatchedHandler = { result ->
            result.each { testIssue ->
                // Remove all the results from preceding executions
                this.jira.removeLabelsFromIssue(testIssue.key, TestIssueLabels.values().collect() { it.name() })
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

    @NonCPS
    String convertHTMLImageSrcIntoBase64Data(String html) {
        def server = this.jira.baseURL
        html = this.thumbnailImageReplacement(html)
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
            String version = this.project.versionFromReleaseStatusIssue
            def bug = this.jira.createIssueTypeBug(
                this.project.jiraProjectKey, failure.type, failure.text, version)

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
              tests: bugAffectedTestIssues.keySet() as List,
              versions: [ "${version}" ]
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
            this.thumbnailImageReplacement(content)

            def documentTypes = (issue.fields.labels ?: [])
                .findAll { String l -> l.startsWith(LabelPrefix.DOCUMENT) }
                .collect { String l -> l.replace(LabelPrefix.DOCUMENT, '') }
            if (documentTypes.size() == 0) {
                throw new IllegalArgumentException("Error: issue '${issue.key}' of type " +
                    "'${JiraUseCase.IssueTypes.DOCUMENTATION_CHAPTER}' contains no " +
                    "document labels. There should be at least one label starting with '${LabelPrefix.DOCUMENT}'")
            }

            def predecessorLinks = issue.fields.issuelinks
                .findAll { it.type.name == "Succeeds" && it.outwardIssue?.key }
                .collect { it.outwardIssue.key }

            return [(issue.key as String): [
                    section: "sec${number.replaceAll(/\./, "s")}".toString(),
                    number: number,
                    heading: issue.fields.summary,
                    documents: documentTypes,
                    content: content?.replaceAll("\u00a0", " ") ?: " ",
                    status: issue.fields.status.name,
                    key: issue.key as String,
                    predecessors: predecessorLinks.isEmpty()? [] : predecessorLinks,
                    versions: versionName? [versionName] : [],
                ]
            ]
        }
    }

    String getVersionFromReleaseStatusIssue() {
        if (!this.jira) {
            logger.warn("WARNING: this.jira has an invalid value.")
            return ""
        }

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

    void matchTestIssuesAgainstTestResults(List testIssues, Map testResults,
                                           Closure matchedHandler, Closure unmatchedHandler = null,
                                           boolean checkDuplicateTestResults = true) {
        def duplicateKeysErrorMessage = "Error: the following test cases are implemented multiple times each: "
        def duplicatesKeys = []

        def result = [
            matched: [:],
            unmatched: []
        ]

        this.walkTestIssuesAndTestResults(testIssues, testResults) { testIssue, testCase, isMatch ->
            if (isMatch) {
                if (result.matched.get(testIssue) != null) {
                    duplicatesKeys.add(testIssue.key)
                }

                result.matched << [
                    (testIssue): testCase
                ]
            }
        }

        testIssues.each { testIssue ->
            if (!result.matched.keySet().contains(testIssue) && mustRun(testIssue)) {
                result.unmatched << testIssue
            }
        }

        if (matchedHandler) {
            matchedHandler(result.matched)
        }

        if (unmatchedHandler) {
            unmatchedHandler(result.unmatched)
        }

        if (checkDuplicateTestResults && duplicatesKeys) {
            throw new IllegalStateException("${duplicateKeysErrorMessage}${duplicatesKeys.join(', ')}.")
        }
    }

    private boolean mustRun(testIssue) {
        return !project.promotingToProd() ||
            testIssue.testType?.equalsIgnoreCase(Project.TestType.INSTALLATION)
    }

    void reportTestResultsForComponent(String componentName, List<String> testTypes, Map testResults) {
        if (!this.jira) return

        def testComponent = "${componentName ?: 'project'}"
        def testMessage = componentName ? " for component '${componentName}'" : ''
        if (logger.debugMode) {
            logger.debug('Reporting unit test results to corresponding test cases in Jira' +
                "${testMessage}. Test type: '${testTypes}'.\nTest results: ${testResults}")
        }

        logger.startClocked("${testComponent}-jira-fetch-tests-${testTypes}")
        def testIssues = this.project.getAutomatedTests(componentName, testTypes)
        logger.debugClocked("${testComponent}-jira-fetch-tests-${testTypes}",
            "Found automated tests$testMessage. Test type: ${testTypes}: " +
                "${testIssues?.size()}")

        this.util.warnBuildIfTestResultsContainFailure(testResults)
        this.matchTestIssuesAgainstTestResults(testIssues, testResults, null) { unexecutedJiraTests ->
            if (!unexecutedJiraTests.isEmpty()) {
                this.util.warnBuildAboutUnexecutedJiraTests(unexecutedJiraTests)
            }
        }

        logger.startClocked("${testComponent}-jira-report-tests-${testTypes}")
        this.support.applyXunitTestResults(testIssues, testResults)
        logger.debugClocked("${testComponent}-jira-report-tests-${testTypes}")
        if (['Q', 'P'].contains(this.project.buildParams.targetEnvironmentToken)) {
            logger.startClocked("${testComponent}-jira-report-bugs-${testTypes}")
            // Create bugs for erroneous test issues
            def errors = JUnitParser.Helper.getErrors(testResults)
            this.createBugsForFailedTestIssues(testIssues, errors, this.steps.env.RUN_DISPLAY_URL)

            // Create bugs for failed test issues
            def failures = JUnitParser.Helper.getFailures(testResults)
            this.createBugsForFailedTestIssues(testIssues, failures, this.steps.env.RUN_DISPLAY_URL)
            logger.debugClocked("${testComponent}-jira-report-bugs-${testTypes}")
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
        if (!this.jira) {
            logger.warn("updateJiraReleaseStatusResult: Could *NOT* update release status result because jira has invalid value.")
            return
        }

        def status = isError ? 'Failed' : 'Successful'

        logger.info("Updating Jira release status with result ${status} and comment ${message}")

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
            String commentToAdd = "${message}\n\nSee: ${this.steps.env.RUN_DISPLAY_URL}"
            logger.debug("Adding comment to Jira issue with key ${releaseStatusIssueKey}: ${commentToAdd}")
            this.jira.appendCommentToIssue(releaseStatusIssueKey, commentToAdd)
            logger.info("Comment was added to Jira issue with key ${releaseStatusIssueKey}: ${commentToAdd}")
        } else {
            logger.warn("*NO* Comment was added to Jira issue with key ${releaseStatusIssueKey}")
        }
    }

    Long getLatestDocVersionId(List<Map> trackingIssues) {
        logger.debug("Cache of versions from doc tracking issues: ${docVersions}")
        def versionList = trackingIssues.collect { issue ->
            docVersions.get(issue.key)
        }

        // We will use the biggest ID available
        def result = versionList.max()
        logger.debug("Retrieved max doc version ${result} from doc tracking issues " +
            "${trackingIssues.collect { it.key }}")

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

    @NonCPS
    def thumbnailImageReplacement(content) {
        def matcher = content =~ /<a.*id="(.*)_thumb".*href="(.*?)"/
        matcher.each {
            def imageMatcher = content =~ /<a.*id="${it[1]}_thumb".*src="(.*?)"/
            content = content.replace(imageMatcher[0][1], it[2])
        }
        return content
    }

    Map getComponents(String projectKey, String version) {
        if (!this.jira) {
            logger.warn("getComponents: Could *NOT* retrieve components because jira has invalid value.")
            return [:]
        }
        return jira.getComponents(projectKey, version)
    }

    protected List loadJiraSecurityVulnerabilityIssues(String issueSummary, String fixVersion,
                                                       String jiraComponent, projectKey) {
        if (!this.jira) {
            logger.warn("loadJiraSecurityVulnerabilityIssues: Could *NOT* retrieve security vulnerability type issues " +
                "because jira has invalid value.")
            return [:]
        }

        def fields = ['assignee', 'duedate', 'issuelinks', 'status', 'summary']
        def jql = "project = \"${projectKey}\" AND issuetype = \"Security Vulnerability\" " +
            "AND fixVersion = \"${fixVersion}\" " +
            "AND component = \"${jiraComponent}\" " +
            "AND summary ~ \"${issueSummary}\" "

        def jqlQuery = [
            fields: fields,
            jql: jql,
            expand: []
        ]

        return jira.getIssuesForJQLQuery(jqlQuery) ?: []
    }

    List createSecurityVulnerabilityIssues(List aquaCriticalVulnerabilityRepos) {
        def securityVulnerabilityIssueKeys = [];
        try {
            for (def repo : aquaCriticalVulnerabilityRepos) {
                def jiraComponentId = getJiraComponentId(repo)
                for (def vulnerability : repo.data.openshift.aquaCriticalVulnerability) {
                    def vulerabilityMap = vulnerability as Map
                    def issueKey = createOrUpdateSecurityVulnerabilityIssue(
                        vulerabilityMap.name,
                        jiraComponentId,
                        buildSecurityVulnerabilityIssueDescription(
                            vulerabilityMap,
                            repo.data.openshift.gitUrl,
                            repo.data.openshift.gitBranch,
                            repo.data.openshift.repoName,
                            repo.data.openshift.nexusReportLink))
                    securityVulnerabilityIssueKeys.add(issueKey)
                }
            }
        } catch (JiraNotPresentException e) {
            logger.warn(e.getMessage())
            return []
        }
        return securityVulnerabilityIssueKeys
    }

    String createOrUpdateSecurityVulnerabilityIssue(String vulnerabilityName, String jiraComponentId,
                                                    String description) {
        if (!jira) {
            throw new JiraNotPresentException("JiraUseCase not present, cannot create security vulnerability issue.")
        }

        def issueSummary =  SECURITY_VULNERABILITY_ISSUE_SUMMARY.replace(VULNERABILITY_NAME_PLACEHOLDER,
            vulnerabilityName)

        def fixVersion = null
        if (project.isVersioningEnabled) {
            fixVersion = project.getVersionName()
        }
        def fullJiraComponentName = JIRA_COMPONENT_TECHNOLOGY_PREFIX + jiraComponentId

        List securityVulnerabilityIssues = loadJiraSecurityVulnerabilityIssues(issueSummary,
            fixVersion, fullJiraComponentName, project.jiraProjectKey)
        if (securityVulnerabilityIssues?.size() >= 1) { // Transition the issue to "TO DO" state
            transitionIssueToToDo(securityVulnerabilityIssues.get(0).id)
            return (securityVulnerabilityIssues.get(0) as Map)?.key
        } else { // Create the issue
            return (createIssueTypeSecurityVulnerability(fixVersion: fixVersion, component: fullJiraComponentName,
                priority: SECURITY_VULNERABILITY_ISSUE_PRIORITY, projectKey: project.jiraProjectKey,
                summary: issueSummary, description: description)
                as Map)?.key
        }
    }

    String buildSecurityVulnerabilityIssueDescription(Map vulnerability, String gitUrl, String gitBranch,
                                                      String repoName, String nexusReportLink) {
        StringBuilder message = new StringBuilder()
        message.append("\nAqua security scan detected the remotely exploitable critical " +
            "vulnerability with name *${vulnerability.name as String}* in repository *[${repoName}|${gitUrl}]* " +
            "in branch *${gitBranch}*." )
        message.append("\n\n*Description:* " + vulnerability.description as String)
        message.append("\n\n*Solution:* " + vulnerability.solution as String)

        if (nexusReportLink != null) {
            message.append("\n\nYou can find the complete security scan report *[here|${nexusReportLink}]*.")
        }

        return message.toString()
    }

    Map createIssueTypeSecurityVulnerability(Map args) {
        return jira?.createIssue(fixVersion: args.fixVersion, component: args.component,
            priority: args.priority, summary: args.summary, type: "Security Vulnerability",
            projectKey: args.projectKey, description: args.description)
    }

    void transitionIssueToToDo(String issueId) {
        int maxAttemps = 10;
        while (maxAttemps-- > 0) {
            Map response = jira?.getIssueStatusWithTransitions(issueId)
            if (response.status.equalsIgnoreCase("to do")) { // Issue is already in TO DO state
                return
            }
            Map possibleTransitionsByName = response.transitions.
                collectEntries { t -> [t.name.toString().toLowerCase(), t] }
            if (possibleTransitionsByName.containsKey("implement")) { // We need to transition the issue
                jira?.doTransition(issueId, possibleTransitionsByName.get("implement"))
            } else if (possibleTransitionsByName.containsKey("confirm dod")) { // We need to transition the issue
                jira?.doTransition(issueId, possibleTransitionsByName.get("confirm dod"))
            } else if (possibleTransitionsByName.containsKey("reopen")) { // We need just one transiton
                jira?.doTransition(issueId, possibleTransitionsByName.get("reopen"))
            } else {
                throw new IllegalStateException("Unexpected issue transition states " +
                    "found: ${possibleTransitionsByName.keySet()}")
            }
        }
        throw new IllegalStateException("The issue didn't reach the TODO state in a reasonable number of " +
            "transitions. Please check the Issue workflow to detect potential loops.")
    }

    String getJiraComponentId(def repo) {
        return repo.data?.openshift?.jiraComponentId
    }
}
