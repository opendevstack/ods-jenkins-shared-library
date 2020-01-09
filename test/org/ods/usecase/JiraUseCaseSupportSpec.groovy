package org.ods.usecase

import org.ods.service.*

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class JiraUseCaseSupportSpec extends SpecHelper {

    JiraUseCase createUseCase(PipelineSteps steps, JiraService jira) {
        return new JiraUseCase(steps, jira)
    }

    JiraUseCaseSupport createUseCaseSupport(PipelineSteps steps, JiraUseCase usecase) {
        return new JiraUseCaseSupport(steps, usecase)
    }

    def "apply test results to automated test issues"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = createUseCase(steps, jira)

        def support = createUseCaseSupport(steps, usecase)
        usecase.setSupport(support)

        def testIssues = createJiraTestIssues()
        def testResults = createTestResults()

        when:
        support.applyTestResultsToAutomatedTestIssues(testIssues, testResults)

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

    def "get automated test issues"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = createUseCase(steps, jira)

        def support = createUseCaseSupport(steps, usecase)
        usecase.setSupport(support)

        def project = createProject()

        def jqlQuery = [
            jql: "project = ${project.id} AND issuetype in ('Test') AND labels in ('AutomatedTest')",
            expand: [ "renderedFields" ],
            fields: [ "components", "description", "issuelinks", "issuetype", "summary" ]
        ]

        when:
        support.getAutomatedTestIssues(project.id)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> []
    }

    def "get automated test issues with componentId"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = createUseCase(steps, jira)

        def support = createUseCaseSupport(steps, usecase)
        usecase.setSupport(support)

        def project = createProject()
        def componentId = "myComponent"

        def jqlQuery = [
            jql: "project = ${project.id} AND component = '${componentId}' AND issuetype in ('Test') AND labels in ('AutomatedTest')",
            expand: [ "renderedFields" ],
            fields: [ "components", "description", "issuelinks", "issuetype", "summary" ]
        ]

        when:
        support.getAutomatedTestIssues(project.id, componentId)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> []
    }

    def "get automated test issues with labelsSelector"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = createUseCase(steps, jira)

        def support = createUseCaseSupport(steps, usecase)
        usecase.setSupport(support)

        def project = createProject()
        def componentId = "myComponent"
        def labelsSelector = ["UnitTest"]

        def jqlQuery = [
            jql: "project = ${project.id} AND component = '${componentId}' AND issuetype in ('Test') AND labels in ('UnitTest', 'AutomatedTest')",
            expand: [ "renderedFields" ],
            fields: [ "components", "description", "issuelinks", "issuetype", "summary" ]
        ]

        when:
        support.getAutomatedTestIssues(project.id, componentId, labelsSelector)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> []
    }

    def "get automated test (unit test) issues"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = createUseCase(steps, jira)

        def support = createUseCaseSupport(steps, usecase)
        usecase.setSupport(support)

        def project = createProject()

        def jqlQuery = [
            jql: "project = ${project.id} AND issuetype in ('Test') AND labels in ('UnitTest', 'AutomatedTest')",
            expand: [ "renderedFields" ],
            fields: [ "components", "description", "issuelinks", "issuetype", "summary" ]
        ]

        when:
        support.getUnitTestIssues(project.id)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> []
    }

    def "get automated test (unit test) issues with componentId"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = createUseCase(steps, jira)

        def support = createUseCaseSupport(steps, usecase)
        usecase.setSupport(support)

        def project = createProject()
        def componentId = "myComponent"

        def jqlQuery = [
            jql: "project = ${project.id} AND component = '${componentId}' AND issuetype in ('Test') AND labels in ('UnitTest', 'AutomatedTest')",
            expand: [ "renderedFields" ],
            fields: [ "components", "description", "issuelinks", "issuetype", "summary" ]
        ]

        when:
        support.getUnitTestIssues(project.id, componentId)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> []
    }

    def "get automated test (integration test) issues"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = createUseCase(steps, jira)

        def support = createUseCaseSupport(steps, usecase)
        usecase.setSupport(support)

        def project = createProject()

        def jqlQuery = [
            jql: "project = ${project.id} AND issuetype in ('Test') AND labels in ('IntegrationTest', 'AutomatedTest')",
            expand: [ "renderedFields" ],
            fields: [ "components", "description", "issuelinks", "issuetype", "summary" ]
        ]

        when:
        support.getIntegrationTestIssues(project.id)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> []
    }

    def "get automated test (integration test) issues with componentId"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = createUseCase(steps, jira)

        def support = createUseCaseSupport(steps, usecase)
        usecase.setSupport(support)

        def project = createProject()
        def componentId = "myComponent"

        def jqlQuery = [
            jql: "project = ${project.id} AND component = '${componentId}' AND issuetype in ('Test') AND labels in ('IntegrationTest', 'AutomatedTest')",
            expand: [ "renderedFields" ],
            fields: [ "components", "description", "issuelinks", "issuetype", "summary" ]
        ]

        when:
        support.getIntegrationTestIssues(project.id, componentId)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> []
    }

    def "get automated test (acceptance test) issues"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = createUseCase(steps, jira)

        def support = createUseCaseSupport(steps, usecase)
        usecase.setSupport(support)

        def project = createProject()

        def jqlQuery = [
            jql: "project = ${project.id} AND issuetype in ('Test') AND labels in ('AcceptanceTest', 'AutomatedTest')",
            expand: [ "renderedFields" ],
            fields: [ "components", "description", "issuelinks", "issuetype", "summary" ]
        ]

        when:
        support.getAcceptanceTestIssues(project.id)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> []
    }

    def "get automated test (acceptance test) issues with componentId"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = createUseCase(steps, jira)

        def support = createUseCaseSupport(steps, usecase)
        usecase.setSupport(support)

        def project = createProject()
        def componentId = "myComponent"

        def jqlQuery = [
            jql: "project = ${project.id} AND component = '${componentId}' AND issuetype in ('Test') AND labels in ('AcceptanceTest', 'AutomatedTest')",
            expand: [ "renderedFields" ],
            fields: [ "components", "description", "issuelinks", "issuetype", "summary" ]
        ]

        when:
        support.getAcceptanceTestIssues(project.id, componentId)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> []
    }

    def "get automated test (installation test) issues"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = createUseCase(steps, jira)

        def support = createUseCaseSupport(steps, usecase)
        usecase.setSupport(support)

        def project = createProject()

        def jqlQuery = [
            jql: "project = ${project.id} AND issuetype in ('Test') AND labels in ('InstallationTest', 'AutomatedTest')",
            expand: [ "renderedFields" ],
            fields: [ "components", "description", "issuelinks", "issuetype", "summary" ]
        ]

        when:
        support.getInstallationTestIssues(project.id)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> []
    }

    def "get automated test (installation test) issues with componentId"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = createUseCase(steps, jira)

        def support = createUseCaseSupport(steps, usecase)
        usecase.setSupport(support)

        def project = createProject()
        def componentId = "myComponent"

        def jqlQuery = [
            jql: "project = ${project.id} AND component = '${componentId}' AND issuetype in ('Test') AND labels in ('InstallationTest', 'AutomatedTest')",
            expand: [ "renderedFields" ],
            fields: [ "components", "description", "issuelinks", "issuetype", "summary" ]
        ]

        when:
        support.getInstallationTestIssues(project.id, componentId)

        then:
        1 * jira.getIssuesForJQLQuery(jqlQuery) >> []
    }
}
