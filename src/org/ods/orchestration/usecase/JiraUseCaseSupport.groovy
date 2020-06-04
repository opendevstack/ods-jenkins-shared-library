package org.ods.orchestration.usecase

import org.ods.util.IPipelineSteps
import org.ods.orchestration.util.Project

class JiraUseCaseSupport extends AbstractJiraUseCaseSupport {

    JiraUseCaseSupport(Project project, IPipelineSteps steps, JiraUseCase usecase) {
        super(project, steps, usecase)
    }

    void applyXunitTestResults(List testIssues, Map testResults) {
        this.usecase.applyXunitTestResultsAsTestIssueLabels(testIssues, testResults)
    }

}
