package org.ods.usecase

import org.ods.service.JiraService

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class JiraUseCaseSpec extends SpecHelper {

    JiraUseCase createUseCase(PipelineSteps steps, JiraService jira) {
        return new JiraUseCase(steps, jira)
    }

    def "check Jira issue matches test case"() {
        given:
        def steps   = Spy(PipelineSteps)
        def jira    = Mock(JiraService)
        def usecase = createUseCase(steps, jira)

        when:
        def issue = [key: "JIRA-123"]
        def testcaseName = "JIRA123 test"

        then:
        usecase.checkJiraIssueMatchesTestCase(issue, testcaseName)

        when:
        issue = [key: "JIRA-123"]
        testcaseName = "JIRA123-test"

        then:
        usecase.checkJiraIssueMatchesTestCase(issue, testcaseName)

        when:
        issue = [key: "JIRA-123"]
        testcaseName = "JIRA123_test"

        then:
        usecase.checkJiraIssueMatchesTestCase(issue, testcaseName)

        when:
        issue = [key: "JIRA-123"]
        testcaseName = "JIRA123test"

        then:
        !usecase.checkJiraIssueMatchesTestCase(issue, testcaseName)

        when:
        issue = [key: "JIRA-123"]
        testcaseName = "JIRA-123_test"

        then:
        !usecase.checkJiraIssueMatchesTestCase(issue, testcaseName)
    }

    def "create bug and block impacted test cases"() {
        given:
        def projectId      = "PROJECT-1"
        def testCaseIssues = createJiraTestCaseIssues()
        def failure        = createTestResultFailures().first()
        def comment        = "myComment"

        def steps   = Spy(PipelineSteps)
        def jira    = Mock(JiraService)
        def usecase = createUseCase(steps, jira)

        when:
        usecase.createBugAndBlockImpactedTestCases(projectId, testCaseIssues, failure, comment)

        then:
        1 * jira.createIssueTypeBug(projectId, failure.type, failure.text) >> {
            return [ key: "JIRA-BUG" ]
        }

        then:
        1 * jira.appendCommentToIssue("JIRA-BUG", comment)

        then:
        1 * jira.createIssueLinkTypeBlocks([ key: "JIRA-BUG" ], {
            // the Jira issue that shall be linked to the bug
            it.key == "JIRA-6"
        })
    }

    def "label test cases with test results"() {
        given:
        def testCaseIssues = createJiraTestCaseIssues()
        def testResults    = createTestResults()

        def steps   = Spy(PipelineSteps)
        def jira    = Mock(JiraService)
        def usecase = createUseCase(steps, jira)

        when:
        usecase.labelTestCasesWithTestResults(testCaseIssues, testResults)

        then:
        1 * jira.removeLabelsFromIssue("4", { it == JiraUseCase.JIRA_TEST_CASE_LABELS })
        1 * jira.addLabelsToIssue("4", ["Succeeded"])
        0 * jira.addLabelsToIssue("4", _)

        then:
        1 * jira.removeLabelsFromIssue("5", { it == JiraUseCase.JIRA_TEST_CASE_LABELS })
        1 * jira.addLabelsToIssue("5", ["Error"])
        0 * jira.addLabelsToIssue("5", _)

        then:
        1 * jira.removeLabelsFromIssue("6", { it == JiraUseCase.JIRA_TEST_CASE_LABELS })
        1 * jira.addLabelsToIssue("6", ["Failed"])
        0 * jira.addLabelsToIssue("6", _)

        then:
        1 * jira.removeLabelsFromIssue("7", { it == JiraUseCase.JIRA_TEST_CASE_LABELS })
        1 * jira.addLabelsToIssue("7", ["Skipped"])
        0 * jira.addLabelsToIssue("7", _)

        then:
        1 * jira.removeLabelsFromIssue("8", { it == JiraUseCase.JIRA_TEST_CASE_LABELS })
        1 * jira.addLabelsToIssue("8", ["Missing"])
        0 * jira.addLabelsToIssue("8", _)
    }

    def "report test results for project"() {
        given:
        def projectId      = "PROJECT-1"
        def testCaseIssues = createJiraTestCaseIssues(false)
        def testResults    = createTestResults(false)
        def error          = createTestResultErrors().first()
        def failure        = createTestResultFailures().first()

        def steps   = Spy(PipelineSteps)
        def jira    = Mock(JiraService)
        def usecase = createUseCase(steps, jira)

        when:
        usecase.reportTestResultsForProject(projectId, testResults)

        then:
        1 * jira.getIssuesForJQLQuery("project = PROJECT-1 AND labels = AutomatedTest AND issuetype = Task") >> {
            return testCaseIssues
        }

        then:
        1 * jira.removeLabelsFromIssue("4", { it == JiraUseCase.JIRA_TEST_CASE_LABELS })
        1 * jira.addLabelsToIssue("4", ["Succeeded"])
        0 * jira.addLabelsToIssue("4", _)

        then:
        1 * jira.removeLabelsFromIssue("5", { it == JiraUseCase.JIRA_TEST_CASE_LABELS })
        1 * jira.addLabelsToIssue("5", ["Error"])
        0 * jira.addLabelsToIssue("5", _)

        then:
        1 * jira.removeLabelsFromIssue("6", { it == JiraUseCase.JIRA_TEST_CASE_LABELS })
        1 * jira.addLabelsToIssue("6", ["Failed"])
        0 * jira.addLabelsToIssue("6", _)

        then:
        1 * jira.removeLabelsFromIssue("7", { it == JiraUseCase.JIRA_TEST_CASE_LABELS })
        1 * jira.addLabelsToIssue("7", ["Skipped"])
        0 * jira.addLabelsToIssue("7", _)

        then:
        1 * jira.removeLabelsFromIssue("8", { it == JiraUseCase.JIRA_TEST_CASE_LABELS })
        1 * jira.addLabelsToIssue("8", ["Missing"])
        0 * jira.addLabelsToIssue("8", _)

        // create bug and block impacted test cases for error
        then:
        1 * jira.createIssueTypeBug(projectId, error.type, error.text) >> {
            return [ key: "JIRA-BUG-1" ]
        }

        then:
        1 * jira.appendCommentToIssue("JIRA-BUG-1", _)

        then:
        1 * jira.createIssueLinkTypeBlocks([ key: "JIRA-BUG-1" ], {
            // the Jira issue that shall be linked to the bug
            it.key == "JIRA-5"
        })

        // create bug and block impacted test cases for failure
        then:
        1 * jira.createIssueTypeBug(projectId, failure.type, failure.text) >> {
            return [ key: "JIRA-BUG-2" ]
        }

        then:
        1 * jira.appendCommentToIssue("JIRA-BUG-2", _)

        then:
        1 * jira.createIssueLinkTypeBlocks([ key: "JIRA-BUG-2" ], {
            // the Jira issue that shall be linked to the bug
            it.key == "JIRA-6"
        })
    }
}
