package org.ods.orchestration.usecase

import org.ods.util.IPipelineSteps
import org.ods.orchestration.util.Context

class JiraUseCaseSupport extends AbstractJiraUseCaseSupport {

    JiraUseCaseSupport(Context context, IPipelineSteps steps, JiraUseCase usecase) {
        super(context, steps, usecase)
    }

    void applyXunitTestResults(List testIssues, Map testResults) {
        this.usecase.applyXunitTestResultsAsTestIssueLabels(testIssues, testResults)
    }
}
