package org.ods.usecase

import org.ods.service.*

import org.ods.parser.JUnitParser

import org.ods.util.MROPipelineUtil

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class JiraUseCaseZephyrSupportSpec extends SpecHelper {

    def "apply test results as test execution statii"() {
        given:
        def buildParams = createBuildParams()

        def steps = Spy(PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, jira)

        def zephyr = Mock(JiraZephyrService)
        def util = Mock(MROPipelineUtil)
        def support = new JiraUseCaseZephyrSupport(steps, usecase, zephyr, util)
        usecase.setSupport(support)

        def project = createProject()
        def testIssues = createJiraTestIssues().each { it.projectId = project.id }
        def testResults = createTestResults()

        when:
        support.applyTestResultsAsTestExecutionStatii(testIssues, testResults)

        then:
        _ * util.getBuildParams() >> buildParams

        then:
        1 * zephyr.getProjectVersions(project.id) >> []
        1 * zephyr.getProjectCycles(project.id, "-1") 
        1 * zephyr.createTestCycle(project.id, "-1", buildParams.targetEnvironmentToken + ": Build " + steps.env.BUILD_ID, steps.env.BUILD_URL, buildParams.targetEnvironment) >> [id: "111"]

        then:
        1 * zephyr.createTestExecutionForIssue("1", project.id, "111") >> ["11": []]
        1 * zephyr.updateExecutionForIssuePass("11")
        0 * zephyr./^updateExecutionForIssue.*/("11")

        then:
        1 * zephyr.createTestExecutionForIssue("2", project.id, "111") >> ["12": []]
        1 * zephyr.updateExecutionForIssueFail("12")
        0 * zephyr./^updateExecutionForIssue.*/("12")

        then:
        1 * zephyr.createTestExecutionForIssue("3", project.id, "111") >> ["13": []]
        1 * zephyr.updateExecutionForIssueFail("13")
        0 * zephyr./^updateExecutionForIssue.*/("13")

        then:
        1 * zephyr.createTestExecutionForIssue("4", project.id, "111") >> ["14": []]
        1 * zephyr.updateExecutionForIssueBlocked("14")
        0 * zephyr./^updateExecutionForIssue.*/("14")

        then:
        // Leave test execution at initial status UNEXECUTED otherwise
        1 * zephyr.createTestExecutionForIssue("5", project.id, "111") >> ["15": []]
        0 * zephyr./^updateExecutionForIssue.*/("15")
    }

    def "apply test results as test execution statii without test issues"() {
        given:
        def buildParams = createBuildParams()

        def steps = Spy(PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, jira)

        def zephyr = Mock(JiraZephyrService)
        def util = Mock(MROPipelineUtil)
        def support = new JiraUseCaseZephyrSupport(steps, usecase, zephyr, util)
        usecase.setSupport(support)

        def project = createProject()
        def testIssues = []
        def testResults = [:]

        when:
        support.applyTestResultsAsTestExecutionStatii(testIssues, testResults)

        then:
        _ * util.getBuildParams() >> buildParams
        0 * zephyr.getProjectVersions(project.id)
        0 * zephyr.createTestCycle(project.id, "-1", null, steps.env.BUILD_URL, buildParams.targetEnvironment)
    }

    def "apply test results to test issues"() {
        given:
        def steps = Spy(PipelineSteps)
        def usecase = Mock(JiraUseCase)

        def zephyr = Mock(JiraZephyrService)
        def util = Mock(MROPipelineUtil)
        def support = Spy(new JiraUseCaseZephyrSupport(steps, usecase, zephyr, util))
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
        def util = Mock(MROPipelineUtil)
        def support = new JiraUseCaseZephyrSupport(steps, usecase, zephyr, util)
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

    def "get versions for project - version not found in Jira"() {
        given:
        def steps = Spy(PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, jira)

        def zephyr = Mock(JiraZephyrService)
        def util = Mock(MROPipelineUtil)
        def support = new JiraUseCaseZephyrSupport(steps, usecase, zephyr, util)
        usecase.setSupport(support)

        def project = createProject()

        def jqlQuery = [
            jql: "project = ${project.id} AND issuetype in ('Test') AND labels in ('AutomatedTest')",
            expand: [ "renderedFields" ],
            fields: [ "components", "description", "issuelinks", "issuetype", "summary" ]
        ]

        when:
        def result = support.getVersionId(project.id)

        then:
        1 * util.getBuildParams() >> [version: "WIP"]
        1 * zephyr.getProjectVersions(project.id) >> [[name: "0.1", id: "1234"]]
        result == "-1"
    }

    def "get versions for project - version found in Jira"() {
        given:
        def steps = Spy(PipelineSteps)
        def jira = Mock(JiraService)
        def usecase = new JiraUseCase(steps, jira)

        def zephyr = Mock(JiraZephyrService)
        def util = Mock(MROPipelineUtil)
        def support = new JiraUseCaseZephyrSupport(steps, usecase, zephyr, util)
        usecase.setSupport(support)

        def project = createProject()

        def jqlQuery = [
            jql: "project = ${project.id} AND issuetype in ('Test') AND labels in ('AutomatedTest')",
            expand: [ "renderedFields" ],
            fields: [ "components", "description", "issuelinks", "issuetype", "summary" ]
        ]

        when:
        def result = support.getVersionId(project.id)

        then:
        1 * util.getBuildParams() >> [version: "0.1"]
        1 * zephyr.getProjectVersions(project.id) >> [[name: "0.1", id: "1234"]]
        result == "1234"
    }
}
