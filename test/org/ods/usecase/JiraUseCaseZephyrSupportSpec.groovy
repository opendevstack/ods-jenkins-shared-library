package org.ods.usecase

import org.ods.service.*

import org.ods.parser.JUnitParser

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class JiraUseCaseZephyrSupportSpec extends SpecHelper {

    def "apply test results as test execution statii"() {
        given:
        def steps = Spy(PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, jira)

        def zephyr = Mock(JiraZephyrService)
        def support = new JiraUseCaseZephyrSupport(steps, usecase, zephyr)
        usecase.setSupport(support)

        def project = createProject()
        def testIssues = createJiraTestIssues().each { it.projectId = project.id }
        def testResults = createTestResults()

        when:
        support.applyTestResultsAsTestExecutionStatii(testIssues, testResults)

        then:
        1 * zephyr.createTestExecutionForIssue("1", project.id) >> ["11": []]
        1 * zephyr.updateExecutionForIssuePass("11")
        0 * zephyr./^updateExecutionForIssue.*/("11")

        then:
        1 * zephyr.createTestExecutionForIssue("2", project.id) >> ["12": []]
        1 * zephyr.updateExecutionForIssueFail("12")
        0 * zephyr./^updateExecutionForIssue.*/("12")

        then:
        1 * zephyr.createTestExecutionForIssue("3", project.id) >> ["13": []]
        1 * zephyr.updateExecutionForIssueFail("13")
        0 * zephyr./^updateExecutionForIssue.*/("13")

        then:
        1 * zephyr.createTestExecutionForIssue("4", project.id) >> ["14": []]
        1 * zephyr.updateExecutionForIssueBlocked("14")
        0 * zephyr./^updateExecutionForIssue.*/("14")

        then:
        // Leave test execution at initial status UNEXECUTED otherwise
        1 * zephyr.createTestExecutionForIssue("5", project.id) >> ["15": []]
        0 * zephyr./^updateExecutionForIssue.*/("15")
    }

    def "apply test results to test issues"() {
        given:
        def steps = Spy(PipelineSteps)
        def usecase = Mock(JiraUseCase)

        def zephyr = Mock(JiraZephyrService)
        def support = Spy(new JiraUseCaseZephyrSupport(steps, usecase, zephyr))
        usecase.setSupport(support)

        def testIssues = createJiraTestIssues()
        def testResults = createTestResults()

        when:
        support.applyTestResultsToTestIssues(testIssues, testResults)

        then:
        1 * usecase.applyTestResultsAsTestIssueLabels(testIssues, testResults)
        _ * zephyr.createTestExecutionForIssue(*_) >> ["1": []]
        1 * support.applyTestResultsAsTestExecutionStatii(testIssues, testResults)
    }

    def "get automated test issues for project"() {
        given:
        def steps = Spy(PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, jira)

        def zephyr = Mock(JiraZephyrService)
        def support = new JiraUseCaseZephyrSupport(steps, usecase, zephyr)
        usecase.setSupport(support)

        def project = createProject()

        def jqlQuery = [
            jql: "project = ${project.id} AND issuetype in ('Test') AND labels = 'AutomatedTest'",
            expand: [ "renderedFields" ],
            fields: [ "components", "description", "issuelinks", "issuetype", "summary" ]
        ]

        when:
        support.getAutomatedTestIssues(project.id)

        then:
        1 * zephyr.getProject(project.id)
        1 * jira.getIssuesForJQLQuery(jqlQuery)
    }
}
