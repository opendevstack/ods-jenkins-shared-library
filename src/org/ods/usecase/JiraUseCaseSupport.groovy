package org.ods.usecase

import org.ods.util.IPipelineSteps
import org.ods.util.Project

class JiraUseCaseSupport extends AbstractJiraUseCaseSupport {

    JiraUseCaseSupport(Project project, IPipelineSteps steps, JiraUseCase usecase) {
        super(project, steps, usecase)
    }

    void applyXunitTestResults(List testIssues, Map testResults) {
        this.usecase.applyXunitTestResultsAsTestIssueLabels(testIssues, testResults)
    }
}
