package org.ods.usecase

import org.ods.util.IPipelineSteps

abstract class AbstractJiraUseCaseSupport {

    protected IPipelineSteps steps
    protected JiraUseCase usecase

    AbstractJiraUseCaseSupport(IPipelineSteps steps, JiraUseCase usecase) {
        this.steps = steps
        this.usecase = usecase
    }

    abstract void applyTestResultsToAutomatedTestIssues(List jiraTestIssues, Map testResults)

    abstract List getAutomatedTestIssues(String projectId, String componentName = null, List<String> labelsSelector = [])

    /*
    // FIXME: unable to be invoked from child classes because of https://issues.jenkins-ci.org/browse/JENKINS-54277
    List getAutomatedTestIssues(String projectId, String componentName = null, List<String> labelsSelector = []) {
        if (!this.usecase.jira) return []

        return this.usecase.getIssuesForProject(projectId, componentName, ["Test"], labelsSelector << "AutomatedTest", false) { issuelink ->
            return issuelink.type.relation == "is related to" && (issuelink.issue.issuetype.name == "Epic" || issuelink.issue.issuetype.name == "Story")
        }.values().flatten()
    }
    */

    List getAcceptanceTestIssues(String projectId, String componentName = null) {
        return this.getAutomatedTestIssues(projectId, componentName, ["AcceptanceTest"])
    }

    List getInstallationTestIssues(String projectId, String componentName = null) {
        return this.getAutomatedTestIssues(projectId, componentName, ["InstallationTest"])
    }

    List getIntegrationTestIssues(String projectId, String componentName = null) {
        return this.getAutomatedTestIssues(projectId, componentName, ["IntegrationTest"])
    }

    List getUnitTestIssues(String projectId, String componentName = null) {
        return this.getAutomatedTestIssues(projectId, componentName, ["UnitTest"])
    }
}
