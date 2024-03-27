package org.ods.orchestration.usecase

import org.ods.util.IPipelineSteps
import org.ods.orchestration.util.Project

@SuppressWarnings('AbstractClassWithPublicConstructor')
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
