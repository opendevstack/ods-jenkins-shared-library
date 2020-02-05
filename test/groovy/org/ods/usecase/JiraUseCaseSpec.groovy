package org.ods.usecase

import org.ods.service.JiraService
import org.ods.util.MROPipelineUtil

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class JiraUseCaseSpec extends SpecHelper {

    def "apply test results as test issue labels"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, util, jira)

        def testIssues = createJiraTestIssues()
        def testResults = createTestResults()

        when:
        usecase.applyTestResultsAsTestIssueLabels(testIssues, testResults)

        then:
        1 * jira.removeLabelsFromIssue("1", { it == JiraUseCase.JIRA_TEST_CASE_LABELS })
        1 * jira.addLabelsToIssue("1", ["Succeeded"])
        0 * jira.addLabelsToIssue("1", _)

        then:
        1 * jira.removeLabelsFromIssue("2", { it == JiraUseCase.JIRA_TEST_CASE_LABELS })
        1 * jira.addLabelsToIssue("2", ["Error"])
        0 * jira.addLabelsToIssue("2", _)

        then:
        1 * jira.removeLabelsFromIssue("3", { it == JiraUseCase.JIRA_TEST_CASE_LABELS })
        1 * jira.addLabelsToIssue("3", ["Failed"])
        0 * jira.addLabelsToIssue("3", _)

        then:
        1 * jira.removeLabelsFromIssue("4", { it == JiraUseCase.JIRA_TEST_CASE_LABELS })
        1 * jira.addLabelsToIssue("4", ["Skipped"])
        0 * jira.addLabelsToIssue("4", _)

        then:
        1 * jira.removeLabelsFromIssue("5", { it == JiraUseCase.JIRA_TEST_CASE_LABELS })
        1 * jira.addLabelsToIssue("5", ["Missing"])
        0 * jira.addLabelsToIssue("5", _)
    }

    def "check Jira issue matches test case"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, util, jira)

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

    def "create bugs and block impacted test cases"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, util, jira)

        def project = createProject()
        def testIssues = createJiraTestIssues()
        def failures = createTestResultFailures()
        def comment = "myComment"

        def bug = [ key: "JIRA-BUG" ]

        when:
        usecase.createBugsAndBlockImpactedTestCases(project.id, testIssues, failures, comment)

        then:
        1 * jira.createIssueTypeBug(project.id, failures.first().type, failures.first().text) >> bug

        then:
        1 * jira.createIssueLinkTypeBlocks(bug, {
            // the Jira issue that shall be linked to the bug
            it.key == "JIRA-3"
        })

        then:
        1 * jira.appendCommentToIssue(bug.key, comment)
    }

    def "get automated test issues"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def support = Mock(JiraUseCaseSupport)
        def usecase = new JiraUseCase(steps, util, jira)
        usecase.setSupport(support)

        def project = createProject()
        def issues = createJiraIssues()

        when:
        usecase.getAutomatedTestIssues(project.id)

        then:
        1 * support.getAutomatedTestIssues(project.id, null, []) >> []
    }

    def "get automated test issues with componentName"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def support = Mock(JiraUseCaseSupport)
        def usecase = new JiraUseCase(steps, util, jira)
        usecase.setSupport(support)

        def project = createProject()
        def componentName = "myComponent"

        when:
        usecase.getAutomatedTestIssues(project.id, componentName)

        then:
        1 * support.getAutomatedTestIssues(project.id, componentName, []) >> []
    }

    def "get automated test issues with labelsSelector"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def support = Mock(JiraUseCaseSupport)
        def usecase = new JiraUseCase(steps, util, jira)
        usecase.setSupport(support)

        def project = createProject()
        def componentName = "myComponent"
        def labelsSelector = ["UnitTest"]

        when:
        usecase.getAutomatedTestIssues(project.id, componentName, labelsSelector)

        then:
        1 * support.getAutomatedTestIssues(project.id, componentName, labelsSelector) >> []
    }

    def "get automated acceptance test issues"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def support = Mock(JiraUseCaseSupport)
        def usecase = new JiraUseCase(steps, util, jira)
        usecase.setSupport(support)

        def project = createProject()
        def issues = createJiraIssues()

        when:
        usecase.getAutomatedAcceptanceTestIssues(project.id)

        then:
        1 * support.getAutomatedAcceptanceTestIssues(project.id, null) >> []
    }

    def "get automated installation test issues"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def support = Mock(JiraUseCaseSupport)
        def usecase = new JiraUseCase(steps, util, jira)
        usecase.setSupport(support)

        def project = createProject()
        def issues = createJiraIssues()

        when:
        usecase.getAutomatedInstallationTestIssues(project.id)

        then:
        1 * support.getAutomatedInstallationTestIssues(project.id, null) >> []
    }

    def "get automated integration test issues"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def support = Mock(JiraUseCaseSupport)
        def usecase = new JiraUseCase(steps, util, jira)
        usecase.setSupport(support)

        def project = createProject()
        def issues = createJiraIssues()

        when:
        usecase.getAutomatedIntegrationTestIssues(project.id)

        then:
        1 * support.getAutomatedIntegrationTestIssues(project.id, null) >> []
    }

    def "get automated unit test issues"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def support = Mock(JiraUseCaseSupport)
        def usecase = new JiraUseCase(steps, util, jira)
        usecase.setSupport(support)

        def project = createProject()
        def issues = createJiraIssues()

        when:
        usecase.getAutomatedUnitTestIssues(project.id)

        then:
        1 * support.getAutomatedUnitTestIssues(project.id, null) >> []
    }

    def "get document chapter data"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, util, jira)

        def project = createProject()
        def documentType = "myDocumentType"

        def jqlQuery = [
            jql: "project = ${project.id} AND issuetype = '${JiraUseCase.IssueTypes.DOCUMENT_CHAPTER}' AND labels = LeVA_Doc:${documentType}",
            expand: [ "names", "renderedFields" ]
        ]

        def jiraIssue1 = createJiraIssue("1")
        jiraIssue1.fields["0"] = "1.0"
        jiraIssue1.renderedFields = [:]
        jiraIssue1.renderedFields["1"] = "<html>myContent1</html>"
        jiraIssue1.renderedFields.description = "<html>1-description</html>"

        def jiraIssue2 = createJiraIssue("2")
        jiraIssue2.fields["0"] = "2.0"
        jiraIssue2.renderedFields = [:]
        jiraIssue2.renderedFields["1"] = "<html>myContent2</html>"
        jiraIssue2.renderedFields.description = "<html>2-description</html>"

        def jiraIssue3 = createJiraIssue("3")
        jiraIssue3.fields["0"] = "3.0"
        jiraIssue3.renderedFields = [:]
        jiraIssue3.renderedFields["1"] = "<html>myContent3</html>"
        jiraIssue3.renderedFields.description = "<html>3-description</html>"

        def jiraResult = [
            issues: [ jiraIssue1, jiraIssue2, jiraIssue3 ],
            names: [
                "0": JiraUseCase.CustomIssueFields.HEADING_NUMBER,
                "1": JiraUseCase.CustomIssueFields.CONTENT
            ]
        ]

        when:
        def result = usecase.getDocumentChapterData(project.id, documentType)

        then:
        1 * jira.searchByJQLQuery(jqlQuery) >> jiraResult

        then:
        def expected = [
            "sec1s0": [
                number: "1.0",
                heading: "1-summary",
                content: "<html>myContent1</html>"
            ],
            "sec2s0": [
                number: "2.0",
                heading: "2-summary",
                content: "<html>myContent2</html>"
            ],
            "sec3s0": [
                number: "3.0",
                heading: "3-summary",
                content: "<html>myContent3</html>"
            ]
        ]

        result == expected
    }

    def "get issues for epics"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, util, jira)

        def epicKeys = ["myEpic-1", "myEpic-2"]

        def epicLinkField = createJiraField("customfield_1", "Epic Link")

        def jqlQuery = [
            jql: "'Epic Link' in (${epicKeys[0]}, ${epicKeys[1]})",
            expand: ["renderedFields"],
            fields: [epicLinkField.id, "description", "summary"]
        ]

        def issue1 = createJiraIssue("1")
        issue1.fields[epicLinkField.id] = epicKeys[0]

        def issue2 = createJiraIssue("2")
        issue2.fields[epicLinkField.id] = epicKeys[0]

        def issue3 = createJiraIssue("3")
        issue3.fields[epicLinkField.id] = epicKeys[1]

        when:
        def result = usecase.getIssuesForEpics(epicKeys)

        then:
        1 * jira.getFields() >> [epicLinkField]
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> [issue1, issue2, issue3]

        then:
        def expected = [
            (epicKeys[0]): [issue1, issue2].collect { usecase.toSimpleIssue(it) },
            (epicKeys[1]): [issue3].collect { usecase.toSimpleIssue(it) }
        ]

        result == expected
    }

    def "get issues for epics with issueTypes"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, util, jira)

        def epicKeys = ["myEpic-1", "myEpic-2"]
        def issueTypes = ["Story"]

        def epicLinkField = createJiraField("customfield_1", "Epic Link")

        def jqlQuery = [
            jql: "'Epic Link' in (${epicKeys[0]}, ${epicKeys[1]}) AND issuetype in ('${issueTypes[0]}')",
            expand: ["renderedFields"],
            fields: [epicLinkField.id, "description", "summary"]
        ]

        when:
        usecase.getIssuesForEpics(epicKeys, issueTypes)

        then:
        1 * jira.getFields() >> [epicLinkField]
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> [] // don't care
    }

    def "get issues for project"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, util, jira)

        def project = createProject()

        def jqlQuery1 = [
            jql: "project = ${project.id}",
            expand: ["renderedFields"],
            fields: ["components", "description", "issuelinks", "issuetype", "summary"]
        ]

        def issues1 = createJiraIssues()
        issues1[0].fields.issuetype = [
            name: "Epic"
        ]

        def jqlQuery2 = [
            jql: "key in (JIRA-100, JIRA-101, JIRA-200)",
            expand: ["renderedFields"],
            fields: ["description"]
        ]

        def issues2 = [
            createJiraIssue("100"),
            createJiraIssue("101"),
            createJiraIssue("200")
        ]

        when:
        def components = usecase.getIssuesForProject(project.id)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery1) >> issues1
        1 * jira.getFields() >> [createJiraField("customfield_001", "Epic Link")]
        1 * jira.getIssuesForJQLQuery(jqlQuery2) >> issues2

        then:
        components.size() == 4

        then:
        def issue1 = usecase.toSimpleIssue(createJiraIssue("1"), [
            components: [
                "myComponentA",
                "myComponentB",
                "myComponentC"
            ],
            issuelinks: [
                usecase.toSimpleIssueLink(createJiraIssueLink("1", createJiraIssue("100"))),
                usecase.toSimpleIssueLink(createJiraIssueLink("2", createJiraIssue("101")))
            ],
            issues: [],
            issuetype: [
                name: "Epic"
            ]
        ])

        def issue2 = usecase.toSimpleIssue(createJiraIssue("2"), [
            components: [
                "myComponentA",
                "myComponentB"
            ],
            issuelinks: [
                usecase.toSimpleIssueLink(createJiraIssueLink("1", createJiraIssue("200")))
            ],
            issuetype: [
                name: "Story"
            ]
        ])

        def issue3 = usecase.toSimpleIssue(createJiraIssue("3"), [
            components: [
                "myComponentA"
            ],
            issuelinks: [],
            issuetype: [
                name: "Story"
            ]
        ])

        def issue4 = usecase.toSimpleIssue(createJiraIssue("4"), [
            issuelinks: [],
            issuetype: [
                name: "Story"
            ]
        ])

        components["myComponentA"].size == 3
        components["myComponentA"] == [ issue1, issue2, issue3 ]

        components["myComponentB"].size == 2
        components["myComponentB"] == [ issue1, issue2 ]

        components["myComponentC"].size == 1
        components["myComponentC"] == [ issue1 ]

        components["undefined"].size == 1
        components["undefined"] == [ issue4 ]
    }

    def "get issues for project with componentName"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, util, jira)

        def project = createProject()
        def componentName = "myComponent"

        def jqlQuery = [
            jql: "project = ${project.id} AND component = '${componentName}'",
            expand: ["renderedFields"],
            fields: ["components", "description", "issuelinks", "issuetype", "summary"]
        ]

        when:
        usecase.getIssuesForProject(project.id, componentName)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> [] // don't care
    }

    def "get issues for project with issueTypesSelector"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, util, jira)

        def project = createProject()
        def componentName = "myComponent"
        def issueTypesSelector = ["myIssueType-1", "myIssueType-2"]

        def jqlQuery = [
            jql: "project = ${project.id} AND component = '${componentName}' AND issuetype in ('${issueTypesSelector[0]}', '${issueTypesSelector[1]}')",
            expand: ["renderedFields"],
            fields: ["components", "description", "issuelinks", "issuetype", "summary"]
        ]

        when:
        usecase.getIssuesForProject(project.id, componentName, issueTypesSelector)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> [] // don't care
    }

    def "get issues for project with issueTypesSelector including Epic"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, util, jira)

        def project = createProject()
        def componentName = "myComponent"
        def issueTypesSelector = ["Epic"]

        def jqlQuery1 = [
            jql: "project = ${project.id} AND component = '${componentName}' AND issuetype in ('${issueTypesSelector[0]}')",
            expand: ["renderedFields"],
            fields: ["components", "description", "issuelinks", "issuetype", "summary"]
        ]

        def issues1 = createJiraIssues()
        issues1.find{ it.key == "JIRA-1" }.fields.issuetype = [
            name: "Epic"
        ]
        issues1.find{ it.key == "JIRA-2" }.fields.issuetype = [
            name: "Epic"
        ]
        issues1.find{ it.key == "JIRA-3" }.fields.issuetype = [
            name: "Epic"
        ]

        def epicLinkField = createJiraField("customfield_001", "Epic Link")

        def jqlQuery2 = [
            jql: "'Epic Link' in (JIRA-1, JIRA-2, JIRA-3) AND issuetype in ('Story')",
            expand: ["renderedFields"],
            fields: [epicLinkField.id, "description", "summary"]
        ]

        def issues2 = [ createJiraIssue("10"), createJiraIssue("20") ]
        issues2[0].fields[epicLinkField.id] = "JIRA-1"
        issues2[0].fields.issuetype = [ name: "Story" ]
        issues2[1].fields[epicLinkField.id] = "JIRA-2"
        issues2[1].fields.issuetype = [ name: "Story" ]

        def jqlQuery3 = [
            jql: "key in (JIRA-100, JIRA-101, JIRA-200)",
            expand: ["renderedFields"],
            fields: ["description"]
        ]

        def issues3 = [
            createJiraIssue("100"),
            createJiraIssue("101"),
            createJiraIssue("200")
        ]

        when:
        def components = usecase.getIssuesForProject(project.id, componentName, issueTypesSelector)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery1) >> issues1
        1 * jira.getFields() >> [epicLinkField]
        1 * jira.getIssuesForJQLQuery(jqlQuery2) >> issues2
        1 * jira.getIssuesForJQLQuery(jqlQuery3) >> issues3

        then:
        def issue1 = usecase.toSimpleIssue(createJiraIssue("1"), [
            components: [
                "myComponentA",
                "myComponentB",
                "myComponentC"
            ],
            issuelinks: [
                usecase.toSimpleIssueLink(createJiraIssueLink("1", createJiraIssue("100"))),
                usecase.toSimpleIssueLink(createJiraIssueLink("2", createJiraIssue("101")))
            ],
            issues: [usecase.toSimpleIssue(issues2[0])],
            issuetype: [
                name: "Epic"
            ]
        ])

        def issue2 = usecase.toSimpleIssue(createJiraIssue("2"), [
            components: [
                "myComponentA",
                "myComponentB"
            ],
            issuelinks: [
                usecase.toSimpleIssueLink(createJiraIssueLink("1", createJiraIssue("200")))
            ],
            issues: [usecase.toSimpleIssue(issues2[1])],
            issuetype: [
                name: "Epic"
            ]
        ])

        def issue3 = usecase.toSimpleIssue(createJiraIssue("3"), [
            components: [
                "myComponentA"
            ],
            issuelinks: [],
            issues: [],
            issuetype: [
                name: "Epic"
            ]
        ])

        components["myComponentA"].size == 3
        components["myComponentA"] == [ issue1, issue2, issue3 ]

        components["myComponentB"].size == 2
        components["myComponentB"] == [ issue1, issue2 ]

        components["myComponentC"].size == 1
        components["myComponentC"] == [ issue1 ]
    }

    def "get issues for project with labelsSelector"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, util, jira)

        def project = createProject()
        def componentName = "myComponent"
        def issueTypesSelector = ["Story"]
        def labelsSelector = ["myLabel1", "myLabel2"]

        def jqlQuery = [
            jql: "project = ${project.id} AND component = '${componentName}' AND issuetype in ('${issueTypesSelector[0]}') AND labels = '${labelsSelector[0]}' AND labels = '${labelsSelector[1]}'",
            expand: ["renderedFields"],
            fields: ["components", "description", "issuelinks", "issuetype", "summary"]
        ]

        when:
        def components = usecase.getIssuesForProject(project.id, componentName, issueTypesSelector, labelsSelector)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> [] // don't care
    }

    def "get issues for project with issueLinkFilter"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, util, jira)

        def project = createProject()
        def componentName = "myComponent"
        def issueLinkFilter = { issuelink ->
            return issuelink.type.relation == "relates to"
        }

        def issues1 = createJiraIssues()
        issues1[0].fields.issuetype = [
            name: "Epic"
        ]

        def epicLinkField = createJiraField("customfield_001", "Epic Link")

        def issues2 = [
            createJiraIssue("100"),
            createJiraIssue("101"),
            createJiraIssue("200")
        ]

        when:
        def components = usecase.getIssuesForProject(project.id, componentName, [], [], false, issueLinkFilter)

        then:
        1 * jira.getIssuesForJQLQuery(_) >> issues1
        1 * jira.getFields() >> [epicLinkField]
        1 * jira.getIssuesForJQLQuery(_) >> [] // don't care
        1 * jira.getIssuesForJQLQuery(_) >> issues2

        then:
        def issue1 = usecase.toSimpleIssue(createJiraIssue("1"), [
            components: [
                "myComponentA",
                "myComponentB",
                "myComponentC"
            ],
            issuelinks: [
                usecase.toSimpleIssueLink(createJiraIssueLink("1", createJiraIssue("100"))),
                usecase.toSimpleIssueLink(createJiraIssueLink("2", createJiraIssue("101")))
            ],
            issues: [],
            issuetype: [
                name: "Epic"
            ]
        ])

        def issue2 = usecase.toSimpleIssue(createJiraIssue("2"), [
            components: [
                "myComponentA",
                "myComponentB"
            ],
            issuelinks: [
                usecase.toSimpleIssueLink(createJiraIssueLink("1", createJiraIssue("200")))
            ],
            issuetype: [
                name: "Story"
            ]
        ])

        components["myComponentA"].size == 2
        components["myComponentA"] == [ issue1, issue2 ]

        components["myComponentB"].size == 2
        components["myComponentB"] == [ issue1, issue2 ]

        components["myComponentC"].size == 1
        components["myComponentC"] == [ issue1 ]
    }

    def "get issues for project with throwOnMissingLinks"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, util, jira)

        def project = createProject()
        def componentName = "myComponent"

        def issues = createJiraIssues()

        when:
        usecase.getIssuesForProject(project.id, componentName, [], [], true)

        then:
        1 * jira.getIssuesForJQLQuery(_) >> issues

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: links are missing for issues: JIRA-3, JIRA-4."
    }

    def "match Jira test issues against test results"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, util, jira)

        def testIssues = createJiraTestIssues()
        def testResults = createTestResults()

        def matched = [:]
        def matchedHandler = { result ->
            matched = result.collectEntries { jiraTestIssue, testcase ->
                [ (jiraTestIssue.key.toString()), testcase.name ]
            }
        }

        def mismatched = [:]
        def mismatchedHandler = { result ->
            mismatched = result.collect { it.key }
        }

        when:
        usecase.matchJiraTestIssuesAgainstTestResults(testIssues, testResults, matchedHandler, mismatchedHandler)

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

    def "notify LeVA document issue"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, util, jira)

        def project = createProject()
        def documentType = "myType"
        def message = "myMessage"

        def jqlQuery = [ jql: "project = ${project.id} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.notifyLeVaDocumentTrackingIssue(project.id, documentType, message)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]

        then:
        1 * jira.appendCommentToIssue(documentIssue.key, message)
    }

    def "notify LeVA document issue with query returning != 1 issue"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, util, jira)

        def project = createProject()
        def documentType = "myType"
        def message = "myMessage"

        def jqlQuery = [ jql: "project = ${project.id} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]
        def documentIssues = createJiraDocumentIssues()

        when:
        usecase.notifyLeVaDocumentTrackingIssue(project.id, documentType, message)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> [] // don't care

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: Jira query returned 0 issues: '${jqlQuery}'."

        when:
        usecase.notifyLeVaDocumentTrackingIssue(project.id, documentType, message)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> documentIssues

        then:
        e = thrown(RuntimeException)
        e.message == "Error: Jira query returned 3 issues: '${jqlQuery}'."
    }

    def "report test results for component in DEV"() {
        given:
        def steps = Spy(PipelineSteps)

        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def support = Mock(JiraUseCaseSupport)
        def usecase = new JiraUseCase(steps, util, jira)
        usecase.setSupport(support)

        def project = createProject()
        def componentName = "myComponent"
        def testTypes = ["myTestType"]
        def testResults = createTestResults()

        def testIssues = createJiraTestIssues()
        def buildParams = createBuildParams()
        buildParams.targetEnvironmentToken = "D"

        when:
        usecase.reportTestResultsForComponent(project.id, componentName, testTypes, testResults)

        then:
        1 * support.getAutomatedTestIssues(project.id, componentName, testTypes) >> testIssues

        then:
        1 * support.applyTestResultsToTestIssues(testIssues, testResults)

        then:
        _ * util.getBuildParams() >> buildParams
    }

    def "report test results for component in QA"() {
        given:
        def steps = Spy(PipelineSteps)

        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def support = Mock(JiraUseCaseSupport)
        def usecase = new JiraUseCase(steps, util, jira)
        usecase.setSupport(support)

        def project = createProject()
        def componentName = "myComponent"
        def testTypes = ["myTestType"]
        def testResults = createTestResults()

        def testIssues = createJiraTestIssues()
        def buildParams = createBuildParams()
        buildParams.targetEnvironmentToken = "Q"
        def testIssuesLinkedIssueKeys = testIssues.collect{ it.fields.issuelinks[0].outwardIssue.key }
        def error = createTestResultErrors().first()
        def errorBug = [ key: "JIRA-BUG-1" ]
        def failure = createTestResultFailures().first()
        def failureBug = [ key: "JIRA-BUG-2" ]

        when:
        usecase.reportTestResultsForComponent(project.id, componentName, testTypes, testResults)

        then:
        1 * support.getAutomatedTestIssues(project.id, componentName, testTypes) >> testIssues

        then:
        1 * support.applyTestResultsToTestIssues(testIssues, testResults)

        then:
        _ * util.getBuildParams() >> buildParams

        // create bug and block impacted test cases for error
        then:
        1 * jira.createIssueTypeBug(project.id, error.type, error.text) >> errorBug

        then:
        1 * jira.createIssueLinkTypeBlocks(errorBug, {
            // the Jira issue that shall be linked to the bug
            it.key == "JIRA-2"
        })

        then:
        1 * jira.appendCommentToIssue(errorBug.key, _)

        // create bug and block impacted test cases for failure
        then:
        1 * jira.createIssueTypeBug(project.id, failure.type, failure.text) >> failureBug

        then:
        1 * jira.createIssueLinkTypeBlocks(failureBug, {
            // the Jira issue that shall be linked to the bug
            it.key == "JIRA-3"
        })

        then:
        1 * jira.appendCommentToIssue(failureBug.key, _)
    }

    def "report test results for component in PROD"() {
        given:
        def steps = Spy(PipelineSteps)

        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def support = Mock(JiraUseCaseSupport)
        def usecase = new JiraUseCase(steps, util, jira)
        usecase.setSupport(support)

        def project = createProject()
        def componentName = "myComponent"
        def testTypes = ["myTestType"]
        def testResults = createTestResults()

        def testIssues = createJiraTestIssues()
        def buildParams = createBuildParams()
        buildParams.targetEnvironmentToken = "P"
        def testIssuesLinkedIssueKeys = testIssues.collect{ it.fields.issuelinks[0].outwardIssue.key }
        def error = createTestResultErrors().first()
        def errorBug = [ key: "JIRA-BUG-1" ]
        def failure = createTestResultFailures().first()
        def failureBug = [ key: "JIRA-BUG-2" ]

        when:
        usecase.reportTestResultsForComponent(project.id, componentName, testTypes, testResults)

        then:
        1 * support.getAutomatedTestIssues(project.id, componentName, testTypes) >> testIssues

        then:
        1 * support.applyTestResultsToTestIssues(testIssues, testResults)

        then:
        _ * util.getBuildParams() >> buildParams

        // create bug and block impacted test cases for error
        then:
        1 * jira.createIssueTypeBug(project.id, error.type, error.text) >> errorBug

        then:
        1 * jira.createIssueLinkTypeBlocks(errorBug, {
            // the Jira issue that shall be linked to the bug
            it.key == "JIRA-2"
        })

        then:
        1 * jira.appendCommentToIssue(errorBug.key, _)

        // create bug and block impacted test cases for failure
        then:
        1 * jira.createIssueTypeBug(project.id, failure.type, failure.text) >> failureBug

        then:
        1 * jira.createIssueLinkTypeBlocks(failureBug, {
            // the Jira issue that shall be linked to the bug
            it.key == "JIRA-3"
        })

        then:
        1 * jira.appendCommentToIssue(failureBug.key, _)
    }

    def "walk Jira test issues and test results"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, util, jira)

        def testIssues = createJiraTestIssues()
        def testResults = createTestResults()

        def result = [:]
        def visitor = { jiraTestIssue, testcase, isMatch ->
            if (isMatch) result[jiraTestIssue.key] = testcase.name
        }

        when:
        usecase.walkJiraTestIssuesAndTestResults(testIssues, testResults, visitor)

        then:
        def expected = [
            "JIRA-1": "JIRA1_my-testcase-1",
            "JIRA-2": "JIRA2_my-testcase-2",
            "JIRA-3": "JIRA3_my-testcase-3",
            "JIRA-4": "JIRA4_my-testcase-4"
        ]

        result == expected
    }
}
