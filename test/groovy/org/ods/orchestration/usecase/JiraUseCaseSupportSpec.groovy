package org.ods.orchestration.usecase

import org.ods.orchestration.service.*
import org.ods.orchestration.util.*
import org.ods.util.IPipelineSteps
import org.ods.util.ILogger
import org.ods.util.Logger

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class JiraUseCaseSupportSpec extends SpecHelper {

    JiraUseCase createUseCase(PipelineSteps steps, MROPipelineUtil util, JiraService jira, ILogger logger) {
        return new JiraUseCase(steps, util, jira, logger)
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
        steps = Spy(util.PipelineSteps)
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
