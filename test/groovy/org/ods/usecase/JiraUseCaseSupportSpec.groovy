package org.ods.usecase

import org.ods.service.*
import org.ods.util.IPipelineSteps
import org.ods.util.MROPipelineUtil
import org.ods.util.Project

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class JiraUseCaseSupportSpec extends SpecHelper {

    JiraUseCase createUseCase(PipelineSteps steps, MROPipelineUtil util, JiraService jira) {
        return new JiraUseCase(steps, util, jira)
    }

    JiraUseCaseSupport createUseCaseSupport(PipelineSteps steps, JiraUseCase usecase) {
        return new JiraUseCaseSupport(steps, usecase)
    }

    Project project
    IPipelineSteps steps
    JiraUseCase usecase
    JiraUseCaseSupport support

    def setup() {
        project = createProject()
        steps = Spy(PipelineSteps)
        usecase = Mock(JiraUseCase)

        support = new JiraUseCaseSupport(project, steps, usecase)
        usecase.setSupport(support)
    }

    def "apply test results to test issues"() {
        given:
        def testIssues = createJiraTestIssues()
        def testResults = createTestResults()

        when:
        support.applyXunitTestResults(testIssues, testResults)

        then:
        1 * usecase.applyXunitTestResultsAsTestIssueLabels(testIssues, testResults)
    }
}
