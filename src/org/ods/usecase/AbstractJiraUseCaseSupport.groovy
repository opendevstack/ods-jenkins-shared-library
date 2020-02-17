package org.ods.usecase

import org.ods.util.IPipelineSteps
import org.ods.util.Project

abstract class AbstractJiraUseCaseSupport {

    protected Project project
    protected IPipelineSteps steps
    protected JiraUseCase usecase

    AbstractJiraUseCaseSupport(Project project, IPipelineSteps steps, JiraUseCase usecase) {
        this.project = project
        this.steps = steps
        this.usecase = usecase
    }

    abstract void applyXunitTestResults(List testIssues, Map testResults)
}
