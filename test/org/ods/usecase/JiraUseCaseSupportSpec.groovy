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

    def "apply test results to test issues"() {
        given:
        def steps = Spy(PipelineSteps)
        def usecase = Mock(JiraUseCase)

        def zephyr = Mock(JiraZephyrService)
        def support = new JiraUseCaseZephyrSupport(steps, usecase, zephyr)
        usecase.setSupport(support)

        def testIssues = createJiraTestIssues()
        def testResults = createTestResults()

        when:
        support.applyTestResultsToTestIssues(testIssues, testResults)

        then:
        1 * usecase.applyTestResultsAsTestIssueLabels(testIssues, testResults)
    }

    def "get automated test issues"() {
        given:
        def steps = Spy(PipelineSteps)
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
        def steps = Spy(PipelineSteps)
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
        def steps = Spy(PipelineSteps)
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
        def steps = Spy(PipelineSteps)
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
        def steps = Spy(PipelineSteps)
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
        def steps = Spy(PipelineSteps)
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
        def steps = Spy(PipelineSteps)
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
        def steps = Spy(PipelineSteps)
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
        def steps = Spy(PipelineSteps)
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
        def steps = Spy(PipelineSteps)
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
        def steps = Spy(PipelineSteps)
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
