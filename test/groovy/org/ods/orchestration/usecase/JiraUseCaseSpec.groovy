package org.ods.orchestration.usecase


import org.ods.orchestration.service.JiraService
import org.ods.orchestration.util.TestResults
import org.ods.util.IPipelineSteps
import org.ods.util.ILogger
import org.ods.util.Logger
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import spock.lang.Ignore
import util.SpecHelper

import static util.FixtureHelper.*

class JiraUseCaseSpec extends SpecHelper {

    JiraService jira
    Project project
    IPipelineSteps steps
    JiraUseCase usecase
    MROPipelineUtil util
    ILogger logger

    def setup() {
        project = Spy(createProject())
        steps = Spy(util.PipelineSteps)
        util = Mock(MROPipelineUtil)
        jira = Mock(JiraService) {
            createIssueTypeBug(_, _, _) >> {
                [
                    key   : "BUG-3",
                    fields: [
                        summary: "bug summary / name"
                    ]
                ]
            }
        }
        logger = Mock(Logger)
        usecase = Spy(new JiraUseCase(project, steps, util, jira, logger))
    }

    def "apply test results as test issue labels"() {
        given:
        def testIssues = createJiraTestIssues()
        def testResults = createTestResults()

        when:
        usecase.applyXunitTestResultsAsTestIssueLabels(testIssues, testResults)

        then:
        1 * jira.removeLabelsFromIssue("JIRA-1", ['Error', 'Failed', 'Missing', 'Skipped', 'Succeeded'])
        1 * jira.addLabelsToIssue("JIRA-1", ["Succeeded"])
        0 * jira.addLabelsToIssue("JIRA-1", _)

        then:
        1 * jira.removeLabelsFromIssue("JIRA-2", ['Error', 'Failed', 'Missing', 'Skipped', 'Succeeded'])
        1 * jira.addLabelsToIssue("JIRA-2", ["Error"])
        0 * jira.addLabelsToIssue("JIRA-2", _)

        then:
        1 * jira.removeLabelsFromIssue("JIRA-3", ['Error', 'Failed', 'Missing', 'Skipped', 'Succeeded'])
        1 * jira.addLabelsToIssue("JIRA-3", ["Failed"])
        0 * jira.addLabelsToIssue("JIRA-3", _)

        then:
        1 * jira.removeLabelsFromIssue("JIRA-4", ['Error', 'Failed', 'Missing', 'Skipped', 'Succeeded'])
        1 * jira.addLabelsToIssue("JIRA-4", ["Skipped"])
        0 * jira.addLabelsToIssue("JIRA-4", _)

        then:
        1 * jira.removeLabelsFromIssue("JIRA-5",['Error', 'Failed', 'Missing', 'Skipped', 'Succeeded'])
        1 * jira.addLabelsToIssue("JIRA-5", ["Missing"])
        0 * jira.addLabelsToIssue("JIRA-5", _)
    }

    def "check Jira issue matches test case"() {
        when:
        def issue = [key: "JIRA-123"]
        def testcase = [name: "JIRA123 test"]

        then:
        usecase.checkTestsIssueMatchesTestCase(issue, testcase)

        when:
        issue = [key: "JIRA-123"]
        testcase.name = "JIRA123-test"

        then:
        usecase.checkTestsIssueMatchesTestCase(issue, testcase)

        when:
        issue = [key: "JIRA-123"]
        testcase.name = "JIRA123_test"

        then:
        usecase.checkTestsIssueMatchesTestCase(issue, testcase)

        when:
        issue = [key: "JIRA-123"]
        testcase.name = "JIRA123test"

        then:
        !usecase.checkTestsIssueMatchesTestCase(issue, testcase)

        when:
        issue = [key: "JIRA-123"]
        testcase.name = "JIRA-123_test"

        then:
        !usecase.checkTestsIssueMatchesTestCase(issue, testcase)

        when:
        issue = [key: "JIRA-123"]
        testcase.name = "123_test"

        then:
        !usecase.checkTestsIssueMatchesTestCase(issue, testcase)
    }

    def "create bugs and block impacted test cases"() {
        given:
        // Test Parameters
        project.data.buildParams.changeId = '1.0'
        def testIssues = createSockShopJiraTestIssues()
        def failures = createSockShopTestResultFailures()
        def comment = "myComment"

        // Stubbed Method Responses
        def bug = [
            key   : "BUG-1",
            fields: [
                summary: "my-bug"
            ]
        ]

        when:
        usecase.createBugsForFailedTestIssues(testIssues, failures, comment)

        then:
        1 * jira.createIssueTypeBug(project.jiraProjectKey, failures.first().type, failures.first().text, "1.0") >> bug

        then:
        1 * jira.createIssueLinkTypeBlocks(bug, {
            // the Jira issue that shall be linked to the bug
            it.key == "NET-140"
        })

        then:
        1 * jira.appendCommentToIssue(bug.key, comment)

        then:
        // verify that bug gets created and registered on the correct test issue
        project.data.jira.bugs.containsKey("BUG-1")
        project.data.jiraResolved.bugs.containsKey("BUG-1")
        testIssues.find { it.key == "NET-140" }.bugs.contains("BUG-1")
    }

    def "get document chapter data"() {
        given:
        // Test Parameters

        def docChapterFields = [
            (JiraUseCase.CustomIssueFields.HEADING_NUMBER): [id:"0"],
            (JiraUseCase.CustomIssueFields.CONTENT): [id: "1"],
        ]

        // Argument Constraints
        def jqlQuery = [
            fields: ['key', 'status', 'summary', 'labels', 'issuelinks',
                     docChapterFields[JiraUseCase.CustomIssueFields.CONTENT].id,
                     docChapterFields[JiraUseCase.CustomIssueFields.HEADING_NUMBER].id],
            jql: "project = ${project.jiraProjectKey} AND issuetype = '${JiraUseCase.IssueTypes.DOCUMENTATION_CHAPTER}'",
            expand: ['renderedFields'],
        ]

        // Stubbed Method Responses
        def jiraIssue1 = createJiraIssue("1", null, null, null, "DONE")
        jiraIssue1.fields["0"] = "1.0"
        jiraIssue1.fields.labels = [JiraUseCase.LabelPrefix.DOCUMENT+ "CSD"]
        jiraIssue1.renderedFields = [:]
        jiraIssue1.renderedFields["1"] = "<html>myContent1</html>"
        jiraIssue1.renderedFields.description = "<html>1-description</html>"

        def jiraIssue2 = createJiraIssue("2", null, null, null, "DONE")
        jiraIssue2.fields["0"] = "2.0"
        jiraIssue2.fields.labels = [JiraUseCase.LabelPrefix.DOCUMENT+ "SSDS"]
        jiraIssue2.renderedFields = [:]
        jiraIssue2.renderedFields["1"] = "<html>myContent2</html>"
        jiraIssue2.renderedFields.description = "<html>2-description</html>"

        def jiraIssue3 = createJiraIssue("3", null, null, null, "DONE")
        jiraIssue3.fields["0"] = "3.0"
        jiraIssue3.fields.labels = [JiraUseCase.LabelPrefix.DOCUMENT+ "DTP"]
        jiraIssue3.renderedFields = [:]
        jiraIssue3.renderedFields["1"] = "<html>myContent3</html>"
        jiraIssue3.renderedFields.description = "<html>3-description</html>"

        def jiraResult = [
            issues: [jiraIssue1, jiraIssue2, jiraIssue3],
        ]

        when:
        def result = usecase.getDocumentChapterData(project.jiraProjectKey)

        then:
        1 * project.getJiraFieldsForIssueType(JiraUseCase.IssueTypes.DOCUMENTATION_CHAPTER) >> docChapterFields
        1 * jira.searchByJQLQuery(jqlQuery) >> jiraResult

        then:
        def expected = [
            'JIRA-1': [
                section: 'sec1s0',
                number : '1.0',
                heading: '1-summary',
                documents: ['CSD'],
                content: '<html>myContent1</html>',
                status: 'DONE',
                key: 'JIRA-1',
                predecessors: [],
                versions: [],
            ],
            'JIRA-2': [
                section: 'sec2s0',
                number : '2.0',
                heading: '2-summary',
                documents: ['SSDS'],
                content: '<html>myContent2</html>',
                status: 'DONE',
                key: 'JIRA-2',
                predecessors: [],
                versions: [],
            ],
            'JIRA-3': [
                section: 'sec3s0',
                number : '3.0',
                heading: '3-summary',
                documents: ['DTP'],
                content: '<html>myContent3</html>',
                status: 'DONE',
                key: 'JIRA-3',
                predecessors: [],
                versions: [],
            ]
        ]

        result['JIRA-1'] == expected['JIRA-1']
        result['JIRA-2'] == expected['JIRA-2']
        result['JIRA-3'] == expected['JIRA-3']
    }

    def "get document chapter data with version"() {
        given:
        // Test Parameters
        def version = "myVersion"
        def predecessorKey = "PRED-1"

        def docChapterFields = [
            (JiraUseCase.CustomIssueFields.HEADING_NUMBER): [id:"0"],
            (JiraUseCase.CustomIssueFields.CONTENT): [id: "1"],
        ]

        // Argument Constraints
        def jqlQuery = [
            fields: ['key', 'status', 'summary', 'labels', 'issuelinks',
                     docChapterFields[JiraUseCase.CustomIssueFields.CONTENT].id,
                     docChapterFields[JiraUseCase.CustomIssueFields.HEADING_NUMBER].id],
            jql: "project = ${project.jiraProjectKey} AND issuetype = '${JiraUseCase.IssueTypes.DOCUMENTATION_CHAPTER}'" +
                " AND fixVersion = '${version}'",
            expand: ['renderedFields'],
        ]

        // Stubbed Method Responses
        def jiraIssue1 = createJiraIssue("1", null, null, null, "DONE")
        jiraIssue1.fields["0"] = "1.0"
        jiraIssue1.fields.labels = [JiraUseCase.LabelPrefix.DOCUMENT+ "CSD"]
        jiraIssue1.renderedFields = [:]
        jiraIssue1.renderedFields["1"] = "<html>myContent1</html>"
        jiraIssue1.renderedFields.description = "<html>1-description</html>"

        def jiraIssue2 = createJiraIssue("2", null, null, null, "DONE")
        jiraIssue2.fields["0"] = "2.0"
        jiraIssue2.fields.labels = [JiraUseCase.LabelPrefix.DOCUMENT+ "SSDS"]
        jiraIssue2.fields.issuelinks = [
            [type:[name: "Succeeds"], outwardIssue: [key: predecessorKey]],
            [type:[name: "Succeeds"], inwardIssue: [key: "Should not appwar the successor"]],
        ]
        jiraIssue2.renderedFields = [:]
        jiraIssue2.renderedFields["1"] = "<html>myContent2</html>"
        jiraIssue2.renderedFields.description = "<html>2-description</html>"

        def jiraIssue3 = createJiraIssue("3", null, null, null, "DONE")
        jiraIssue3.fields["0"] = "3.0"
        jiraIssue3.fields.labels = [JiraUseCase.LabelPrefix.DOCUMENT+ "DTP"]
        jiraIssue3.fields.issuelinks = [
            [type:[name: "AnotherLink"], outwardIssue: [key: "Should not appear other links outward"]],
            [type:[name: "AnotherLink2"], inwardIssue: [key: "Should not appear other links inward"]],
        ]
        jiraIssue3.renderedFields = [:]
        jiraIssue3.renderedFields["1"] = "<html>myContent3</html>"
        jiraIssue3.renderedFields.description = "<html>3-description</html>"

        def jiraResult = [
            issues: [jiraIssue1, jiraIssue2, jiraIssue3],
        ]

        when:
        def result = usecase.getDocumentChapterData(project.jiraProjectKey, version)

        then:
        1 * project.getJiraFieldsForIssueType(JiraUseCase.IssueTypes.DOCUMENTATION_CHAPTER) >> docChapterFields
        1 * jira.searchByJQLQuery(jqlQuery) >> jiraResult

        then:
        def expected = [
            'JIRA-1': [
                section: 'sec1s0',
                number : '1.0',
                heading: '1-summary',
                documents: ['CSD'],
                content: '<html>myContent1</html>',
                status: 'DONE',
                key: 'JIRA-1',
                predecessors: [],
                versions: [version],
            ],
            'JIRA-2': [
                section: 'sec2s0',
                number : '2.0',
                heading: '2-summary',
                documents: ['SSDS'],
                content: '<html>myContent2</html>',
                status: 'DONE',
                key: 'JIRA-2',
                predecessors: [predecessorKey],
                versions: [version],
            ],
            'JIRA-3': [
                section: 'sec3s0',
                number : '3.0',
                heading: '3-summary',
                documents: ['DTP'],
                content: '<html>myContent3</html>',
                status: 'DONE',
                key: 'JIRA-3',
                predecessors: [],
                versions: [version],
            ]
        ]

        result['JIRA-1'] == expected['JIRA-1']
        result['JIRA-2'] == expected['JIRA-2']
        result['JIRA-3'] == expected['JIRA-3']
    }

    def "get document chapter data with version and content empty"() {
        given:
        // Test Parameters
        def version = "myVersion"
        def predecessorKey = "PRED-1"

        def docChapterFields = [
            (JiraUseCase.CustomIssueFields.HEADING_NUMBER): [id:"0"],
            (JiraUseCase.CustomIssueFields.CONTENT): [id: "1"],
        ]

        // Argument Constraints
        def jqlQuery = [
            fields: ['key', 'status', 'summary', 'labels', 'issuelinks',
                     docChapterFields[JiraUseCase.CustomIssueFields.CONTENT].id,
                     docChapterFields[JiraUseCase.CustomIssueFields.HEADING_NUMBER].id],
            jql: "project = ${project.jiraProjectKey} AND issuetype = '${JiraUseCase.IssueTypes.DOCUMENTATION_CHAPTER}'" +
                " AND fixVersion = '${version}'",
            expand: ['renderedFields'],
        ]

        // Stubbed Method Responses
        def jiraIssue1 = createJiraIssue("1", null, null, null, "DONE")
        jiraIssue1.fields["0"] = "1.0"
        jiraIssue1.fields.labels = [JiraUseCase.LabelPrefix.DOCUMENT+ "CSD"]
        jiraIssue1.renderedFields = [:]
        jiraIssue1.renderedFields["1"] = "<html>myContent1</html>"
        jiraIssue1.renderedFields.description = "<html>1-description</html>"

        def jiraIssue2 = createJiraIssue("2", null, null, null, "DONE")
        jiraIssue2.fields["0"] = "2.0"
        jiraIssue2.fields.labels = [JiraUseCase.LabelPrefix.DOCUMENT+ "SSDS"]
        jiraIssue2.fields.issuelinks = [
            [type:[name: "Succeeds"], outwardIssue: [key: predecessorKey]],
            [type:[name: "Succeeds"], inwardIssue: [key: "Should not appwar the successor"]],
        ]
        jiraIssue2.renderedFields = [:]
        jiraIssue2.renderedFields["1"] = "<html>myContent2</html>"
        jiraIssue2.renderedFields.description = "<html>2-description</html>"

        def jiraIssue3 = createJiraIssue("3", null, null, null, "DONE")
        jiraIssue3.fields["0"] = "3.0"
        jiraIssue3.fields.labels = [JiraUseCase.LabelPrefix.DOCUMENT+ "DTP"]
        jiraIssue3.fields.issuelinks = [
            [type:[name: "AnotherLink"], outwardIssue: [key: "Should not appear other links outward"]],
            [type:[name: "AnotherLink2"], inwardIssue: [key: "Should not appear other links inward"]],
        ]
        jiraIssue3.renderedFields = [:]
        jiraIssue3.renderedFields["1"] = "" //Empty content
        jiraIssue3.renderedFields.description = "<html>3-description</html>"

        def jiraResult = [
            issues: [jiraIssue1, jiraIssue2, jiraIssue3],
        ]

        when:
        def result = usecase.getDocumentChapterData(project.jiraProjectKey, version)

        then:
        1 * project.getJiraFieldsForIssueType(JiraUseCase.IssueTypes.DOCUMENTATION_CHAPTER) >> docChapterFields
        1 * jira.searchByJQLQuery(jqlQuery) >> jiraResult

        then:
        def expected = [
            'JIRA-1': [
                section: 'sec1s0',
                number : '1.0',
                heading: '1-summary',
                documents: ['CSD'],
                content: '<html>myContent1</html>',
                status: 'DONE',
                key: 'JIRA-1',
                predecessors: [],
                versions: [version],
            ],
            'JIRA-2': [
                section: 'sec2s0',
                number : '2.0',
                heading: '2-summary',
                documents: ['SSDS'],
                content: '<html>myContent2</html>',
                status: 'DONE',
                key: 'JIRA-2',
                predecessors: [predecessorKey],
                versions: [version],
            ],
            'JIRA-3': [
                section: 'sec3s0',
                number : '3.0',
                heading: '3-summary',
                documents: ['DTP'],
                content: ' ', // With empty content a blank must be returned to avoid problems with preceding
                status: 'DONE',
                key: 'JIRA-3',
                predecessors: [],
                versions: [version],
            ]
        ]

        result['JIRA-1'] == expected['JIRA-1']
        result['JIRA-2'] == expected['JIRA-2']
        result['JIRA-3'] == expected['JIRA-3']
    }

    def "get document chapter data with multiple doclabels"() {
        given:
        // Test Parameters
        def version = "myVersion"
        def predecessorKey = "PRED-1"

        def docChapterFields = [
            (JiraUseCase.CustomIssueFields.HEADING_NUMBER): [id:"0"],
            (JiraUseCase.CustomIssueFields.CONTENT): [id: "1"],
        ]

        // Argument Constraints
        def jqlQuery = [
            fields: ['key', 'status', 'summary', 'labels', 'issuelinks',
                     docChapterFields[JiraUseCase.CustomIssueFields.CONTENT].id,
                     docChapterFields[JiraUseCase.CustomIssueFields.HEADING_NUMBER].id],
            jql: "project = ${project.jiraProjectKey} AND issuetype = '${JiraUseCase.IssueTypes.DOCUMENTATION_CHAPTER}'" +
                " AND fixVersion = '${version}'",
            expand: ['renderedFields'],
        ]

        // Stubbed Method Responses
        def jiraIssue1 = createJiraIssue("1", null, null, null, "DONE")
        jiraIssue1.fields["0"] = "1.0"
        jiraIssue1.fields.labels = [JiraUseCase.LabelPrefix.DOCUMENT+ "CSD", JiraUseCase.LabelPrefix.DOCUMENT+ "SSDS"]
        jiraIssue1.renderedFields = [:]
        jiraIssue1.renderedFields["1"] = "<html>myContent1</html>"
        jiraIssue1.renderedFields.description = "<html>1-description</html>"

        def jiraResult = [
            issues: [jiraIssue1],
        ]

        when:
        def result = usecase.getDocumentChapterData(project.jiraProjectKey, version)

        then:
        1 * project.getJiraFieldsForIssueType(JiraUseCase.IssueTypes.DOCUMENTATION_CHAPTER) >> docChapterFields
        1 * jira.searchByJQLQuery(jqlQuery) >> jiraResult

        then:
        def expected = [
            'JIRA-1': [
                section: 'sec1s0',
                number : '1.0',
                heading: '1-summary',
                documents: ['CSD', 'SSDS'],
                content: '<html>myContent1</html>',
                status: 'DONE',
                key: 'JIRA-1',
                predecessors: [],
                versions: [version],
            ]
        ]

        result == expected
    }

    def "match Jira test issues against test results"() {
        given:
        def testIssues = createJiraTestIssues()
        def testResults = createTestResults()

        def matched = [:]
        def matchedHandler = { result ->
            matched = result.collectEntries { jiraTestIssue, testcase ->
                [(jiraTestIssue.key.toString()), testcase.name]
            }
        }

        def mismatched = [:]
        def mismatchedHandler = { result ->
            mismatched = result.collect { it.key }
        }

        when:
        usecase.matchTestIssuesAgainstTestResults(testIssues, testResults, matchedHandler, mismatchedHandler)

        then:
        def expectedMatched = [
            "JIRA-1": "JIRA1_my-testcase-1",
            "JIRA-2": "JIRA2_my-testcase-2",
            "JIRA-3": "JIRA3_my-testcase-3",
            "JIRA-4": "JIRA4_my-testcase-4"
        ]

        def expectedMismatched = [
            "JIRA-5"
        ]

        matched == expectedMatched
        mismatched == expectedMismatched
    }

    def "match Jira test issues against test results when deploying into P"() {
        given:
        def testIssues = createJiraTestIssues()
        def testResults = createTestResults()

        def matched = [:]
        def matchedHandler = { result ->
            matched = result.collectEntries { jiraTestIssue, testcase ->
                [(jiraTestIssue.key.toString()), testcase.name]
            }
        }

        def mismatched = [:]
        def mismatchedHandler = { result ->
            mismatched = result.collect { it.key }
        }
        project.data.buildParams.targetEnvironmentToken = 'P'

        when:
        testIssues[4].testType = Project.TestType.INSTALLATION
        usecase.matchTestIssuesAgainstTestResults(testIssues, testResults, matchedHandler, mismatchedHandler)
        def expectedMatched = [
            "JIRA-1": "JIRA1_my-testcase-1",
            "JIRA-2": "JIRA2_my-testcase-2",
            "JIRA-3": "JIRA3_my-testcase-3",
            "JIRA-4": "JIRA4_my-testcase-4"
        ]

        def expectedMismatched = [
            "JIRA-5"
        ]

        then:
        matched == expectedMatched
        mismatched == expectedMismatched

        when:
        testIssues[4].testType = Project.TestType.INTEGRATION
        usecase.matchTestIssuesAgainstTestResults(testIssues, testResults, matchedHandler, mismatchedHandler)
        expectedMatched = [
            "JIRA-1": "JIRA1_my-testcase-1",
            "JIRA-2": "JIRA2_my-testcase-2",
            "JIRA-3": "JIRA3_my-testcase-3",
            "JIRA-4": "JIRA4_my-testcase-4"
        ]

        expectedMismatched = []

        then:
        matched == expectedMatched
        mismatched == expectedMismatched

        when:
        testIssues[4].testType = Project.TestType.ACCEPTANCE
        usecase.matchTestIssuesAgainstTestResults(testIssues, testResults, matchedHandler, mismatchedHandler)
        expectedMatched = [
            "JIRA-1": "JIRA1_my-testcase-1",
            "JIRA-2": "JIRA2_my-testcase-2",
            "JIRA-3": "JIRA3_my-testcase-3",
            "JIRA-4": "JIRA4_my-testcase-4"
        ]

        expectedMismatched = []

        then:
        matched == expectedMatched
        mismatched == expectedMismatched

        when:
        testIssues[4].testType = Project.TestType.UNIT
        usecase.matchTestIssuesAgainstTestResults(testIssues, testResults, matchedHandler, mismatchedHandler)
        expectedMatched = [
            "JIRA-1": "JIRA1_my-testcase-1",
            "JIRA-2": "JIRA2_my-testcase-2",
            "JIRA-3": "JIRA3_my-testcase-3",
            "JIRA-4": "JIRA4_my-testcase-4"
        ]

        expectedMismatched = []

        then:
        matched == expectedMatched
        mismatched == expectedMismatched
    }

    def "match Jira test issues against test results having duplicate test results"() {
        given:
        def testIssues = createJiraTestIssues()
        def testResults = createTestResultsWithDuplicates()

        def matched = [:]
        def matchedHandler = { result ->
            matched = result.collectEntries { jiraTestIssue, testcase ->
                [(jiraTestIssue.key.toString()), testcase.name]
            }
        }

        def mismatched = [:]
        def mismatchedHandler = { result ->
            mismatched = result.collect { it.key }
        }

        when:
        usecase.matchTestIssuesAgainstTestResults(testIssues, testResults, matchedHandler, mismatchedHandler)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Error: the following test cases are implemented multiple times each: JIRA-1, JIRA-2.'
    }

    def "match Jira test issues against test results having duplicate test results with flag check duplicate to false"() {
        given:
        def testIssues = createJiraTestIssues()
        def testResults = createTestResultsWithDuplicates()

        def matched = [:]
        def matchedHandler = { result ->
            matched = result.collectEntries { jiraTestIssue, testcase ->
                [(jiraTestIssue.key.toString()), testcase.name]
            }
        }

        def mismatched = [:]
        def mismatchedHandler = { result ->
            mismatched = result.collect { it.key }
        }

        when:
        usecase.matchTestIssuesAgainstTestResults(testIssues, testResults, matchedHandler, mismatchedHandler, false)

        then:
        def expectedMatched = [
            "JIRA-1": "JIRA1_my-testcase-3",
            "JIRA-2": "JIRA2_my-testcase-4"
        ]

        def expectedMismatched = [
            "JIRA-3, JIRA-4, JIRA-5"
        ]

        matched == expectedMatched
        mismatched.toString() == expectedMismatched.toString()
    }

    def "report test results for component in DEV"() {
        given:
        project.buildParams.targetEnvironmentToken = "D"

        def support = Mock(JiraUseCaseSupport)
        usecase.setSupport(support)

        // Test Parameters
        def componentName = "myComponent"
        def testTypes = ["myTestType"]
        def testResults = createSockShopTestResults()

        // Stubbed Method Responses
        def testIssues = createSockShopJiraTestIssues()

        when:
        usecase.reportTestResultsForComponent(componentName, testTypes, testResults)

        then:
        1 * project.getAutomatedTests(componentName, testTypes) >> testIssues

        then:
        1 * support.applyXunitTestResults(testIssues, testResults)
        1 * util.warnBuildIfTestResultsContainFailure(testResults)
        1 * util.warnBuildAboutUnexecutedJiraTests(_)
    }

    def "report test results for component with unexecuted Jira tests"() {
        given:
        def support = Mock(JiraUseCaseSupport)
        usecase.setSupport(support)

        // Test Parameters
        def componentName = "myComponent"
        def testTypes = ["myTestType"]
        def testResults = [:] // unexecuted tests

        // Stubbed Method Responses
        def testIssues = createJiraTestIssues()

        when:
        usecase.reportTestResultsForComponent(componentName, testTypes, testResults)

        then:
        1 * project.getAutomatedTests(componentName, testTypes) >> testIssues

        then:
        1 * util.warnBuildAboutUnexecutedJiraTests(testIssues)
    }

    def "report test results for component in QA"() {
        given:
        project.buildParams.targetEnvironmentToken = "Q"

        def support = Mock(JiraUseCaseSupport)
        usecase.setSupport(support)

        // Test Parameters
        def componentName = "myComponent"
        def testTypes = ["myTestType"]
        def testResults = createSockShopTestResults()

        // Argument Constraints
        def errors = createSockShopTestResultErrors()
        def failures = createSockShopTestResultFailures()

        // Stubbed Method Responses
        def testIssues = createSockShopJiraTestIssues()

        when:
        usecase.reportTestResultsForComponent(componentName, testTypes, testResults)

        then:
        1 * project.getAutomatedTests(componentName, testTypes) >> testIssues

        then:
        1 * support.applyXunitTestResults(testIssues, testResults)
        1 * util.warnBuildIfTestResultsContainFailure(testResults)

        then:
        1 * usecase.createBugsForFailedTestIssues(testIssues, errors, steps.env.RUN_DISPLAY_URL) >> null
        1 * usecase.createBugsForFailedTestIssues(testIssues, failures, steps.env.RUN_DISPLAY_URL) >> null
    }

    def "report test results for component in PROD"() {
        given:
        project.buildParams.targetEnvironmentToken = "Q"

        def support = Mock(JiraUseCaseSupport)
        usecase.setSupport(support)

        // Test Parameters
        def componentName = "myComponent"
        def testTypes = ["myTestType"]
        def testResults = createSockShopTestResults()

        // Argument Constraints
        def errors = createSockShopTestResultErrors()
        def failures = createSockShopTestResultFailures()

        // Stubbed Method Responses
        def testIssues = createSockShopJiraTestIssues()

        when:
        usecase.reportTestResultsForComponent(componentName, testTypes, testResults)

        then:
        1 * project.getAutomatedTests(componentName, testTypes) >> testIssues

        then:
        1 * support.applyXunitTestResults(testIssues, testResults)
        1 * util.warnBuildIfTestResultsContainFailure(testResults)

        then:
        1 * usecase.createBugsForFailedTestIssues(testIssues, errors, steps.env.RUN_DISPLAY_URL) >> null
        1 * usecase.createBugsForFailedTestIssues(testIssues, failures, steps.env.RUN_DISPLAY_URL) >> null
    }

    def "update Jira release status build number"() {
        given:
        project.buildParams.changeId = "someChangeId"
        project.buildParams.version = "1.0"
        steps.env.BUILD_NUMBER = "0815"

        when:
        usecase.updateJiraReleaseStatusBuildNumber()

        then:
        1 * jira.updateBuildNumber(_, 'someChangeId', [
            buildNumber: "1.0-0815",
            env: "D",
        ])
    }

    def "update Jira release status result"() {
        given:
        project.buildParams.changeId = "someChangeId"
        project.buildParams.version = "1.0"
        steps.env.BUILD_NUMBER = "0815"
        steps.env.RUN_DISPLAY_URL = "http://jenkins"
        def TestResults testResults = new TestResults();
        testResults.setSucceeded(1)
        testResults.setFailed(1)
        project.setAggregatedTestResults(testResults)

        def error = new RuntimeException("Oh no!")

        when:
        usecase.updateJiraReleaseStatusResult(error.message, true)

        then:
        1 * jira.updateReleaseStatusIssue(_, 'someChangeId', [
            userEmail: null,
            testResults: [
                skipped: testResults.skipped,
                succeeded: testResults.succeeded,
                failed: testResults.failed,
                error: testResults.error,
                missing: testResults.missing,
            ],
            status: "Failed",
            env: 'D',
        ])

        then:
        1 * jira.appendCommentToReleaseStatusIssue(_, 'someChangeId',
            "${error.message}\n\nSee: ${steps.env.RUN_DISPLAY_URL}" )
    }

    def "update Jira release status result without error"() {
        given:
        project.data.buildParams.changeId = "someChangeId"
        project.buildParams.version = "1.0"
        steps.env.BUILD_NUMBER = "0815"
        def TestResults testResults = new TestResults();
        testResults.setSucceeded(1)
        project.setAggregatedTestResults(testResults)

        when:
        usecase.updateJiraReleaseStatusResult("", false)

        then:
        1 * jira.updateReleaseStatusIssue(_, 'someChangeId', [
            userEmail: null,
            testResults: [
                skipped: testResults.skipped,
                succeeded: testResults.succeeded,
                failed: testResults.failed,
                error: testResults.error,
                missing: testResults.missing,
            ],
            status: "Successful",
            env: 'D',
        ])
    }

    def "walk test issues and test results"() {
        given:
        def testIssues = createJiraTestIssues()
        def testResults = createTestResults()

        def result = [:]
        def visitor = { jiraTestIssue, testcase, isMatch ->
            if (isMatch) result[jiraTestIssue.key] = testcase.name
        }

        when:
        usecase.walkTestIssuesAndTestResults(testIssues, testResults, visitor)

        then:
        def expected = [
            "JIRA-1": "JIRA1_my-testcase-1",
            "JIRA-2": "JIRA2_my-testcase-2",
            "JIRA-3": "JIRA3_my-testcase-3",
            "JIRA-4": "JIRA4_my-testcase-4"
        ]

        result == expected
    }

    def "get HTML Image As Base64"() {
        given:
        def jiraUrl = new URI("https://jira.com")
        def contentType = "contentType"
        def binaryData = "binaryData".bytes
        def binaryDataCoded = binaryData.encodeBase64()

        def jira = Mock(JiraService) {
            getBaseURL() >> {
                jiraUrl
            }
            getFileFromJira(*_) >> {
                [ contentType: contentType, data: binaryData ]
            }
        }
        def usecase = Spy(new JiraUseCase(project, steps, util, jira, logger))

        when: 'we have a simple image tag'
        def result = usecase.convertHTMLImageSrcIntoBase64Data("<img src=\"${jiraUrl}/something.png\">")

        then:
        result == "<img src=\"data:${contentType};base64,${binaryDataCoded}\">"


        when: 'we a complex image tag structure with two extensions'
        result = usecase.convertHTMLImageSrcIntoBase64Data("<img src=\"${jiraUrl}/something.png\" imagetext=\"something.png\">")

        then:
        result == "<img src=\"data:${contentType};base64,${binaryDataCoded}\" imagetext=\"something.png\">"

        when: 'we have two images'
        result = usecase.convertHTMLImageSrcIntoBase64Data("<img src=\"${jiraUrl}/something.png\" imagetext=\"something.png\">aaa<img src=\"${jiraUrl}/something2.png\" imagetext=\"something2.png\">")

        then:
        result == "<img src=\"data:${contentType};base64,${binaryDataCoded}\" imagetext=\"something.png\">aaa<img src=\"data:${contentType};base64,${binaryDataCoded}\" imagetext=\"something2.png\">"
    }

    def "remove thumbnail from image urls"() {
        given:
        def usecase = Spy(new JiraUseCase(project, steps, util, jira, logger))
        def initialHtml = "<p><span class=\"image-wrap\" style=\"\"><a id=\"10007_thumb\" href=\"https://jira-url/secure/attachment/10007/10007_file-name.png\" title=\"file-name.png\" file-preview-type=\"image\" file-preview-id=\"10007\" file-preview-title=\"file-name.png\"><img src=\"https://jira-url/secure/thumbnail/10007/_thumb_10007.png\" style=\"border: 0px solid black\" role=\"presentation\"/></a></span></p>"
        def finalHtml = "<p><span class=\"image-wrap\" style=\"\"><a id=\"10007_thumb\" href=\"https://jira-url/secure/attachment/10007/10007_file-name.png\" title=\"file-name.png\" file-preview-type=\"image\" file-preview-id=\"10007\" file-preview-title=\"file-name.png\"><img src=\"https://jira-url/secure/attachment/10007/10007_file-name.png\" style=\"border: 0px solid black\" role=\"presentation\"/></a></span></p>"

        when: 'we replace the thumbnail image for the good one'
        def result = usecase.thumbnailImageReplacement(initialHtml)

        then: 'the result if the snippet with the good url'
        result == finalHtml

    }
}
