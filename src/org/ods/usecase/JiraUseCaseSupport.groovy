package org.ods.usecase

import org.ods.util.IPipelineSteps

class JiraUseCaseSupport extends AbstractJiraUseCaseSupport {

    JiraUseCaseSupport(IPipelineSteps steps, JiraUseCase usecase) {
        super(steps, usecase)
    }

    void applyTestResultsToTestIssues(List jiraTestIssues, Map testResults) {
        this.usecase.applyTestResultsAsTestIssueLabels(jiraTestIssues, testResults)
    }

    List getAutomatedTestIssues(String projectId, String componentName = null, List<String> labelsSelector = []) {
        def issues = this.usecase.getIssuesForProject(projectId, componentName, ["Test"], labelsSelector << "AutomatedTest", false) { issuelink ->
            return issuelink.type.relation == "is related to" && (issuelink.issue.issuetype.name == "Epic" || issuelink.issue.issuetype.name == "Story")
        }.values().flatten()

        return issues.each { issue ->
            issue.test = [
                description: issue.description,
                details: []
            ]
        }
    }
}
