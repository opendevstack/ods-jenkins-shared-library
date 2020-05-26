package org.ods.orchestration.usecase

import org.ods.util.IPipelineSteps
import org.ods.orchestration.util.Context

@SuppressWarnings('AbstractClassWithPublicConstructor')
abstract class AbstractJiraUseCaseSupport {

    protected Context context
    protected IPipelineSteps steps
    protected JiraUseCase usecase

    AbstractJiraUseCaseSupport(Context context, IPipelineSteps steps, JiraUseCase usecase) {
        this.context = context
        this.steps = steps
        this.usecase = usecase
    }

    abstract void applyXunitTestResults(List testIssues, Map testResults)
}
