package org.ods.usecase

import org.ods.util.IPipelineSteps

class JiraUseCaseSupport extends AbstractJiraUseCaseSupport {

    JiraUseCaseSupport(IPipelineSteps steps, JiraUseCase usecase) {
        super(steps, usecase)
    }

    void applyTestResultsToAutomatedTestIssues(List jiraTestIssues, Map testResults) {
        if (!this.usecase.jira) return

        // Handle Jira issues for which a corresponding test exists in testResults
        def matchedHandler = { result ->
            result.each { issue, testcase ->
                this.usecase.jira.removeLabelsFromIssue(issue.id, this.usecase.JIRA_TEST_CASE_LABELS)

                def labelsToApply = ["Succeeded"]
                if (testcase.skipped || testcase.error || testcase.failure) {
                    if (testcase.error) {
                        labelsToApply = ["Error"]
                    }

                    if (testcase.failure) {
                        labelsToApply = ["Failed"]
                    }

                    if (testcase.skipped) {
                        labelsToApply = ["Skipped"]
                    }
                }

                this.usecase.jira.addLabelsToIssue(issue.id, labelsToApply)
            }
        }

        // Handle Jira issues for which no corresponding test exists in testResults
        def unmatchedHandler = { result ->
            result.each { issue ->
                this.usecase.jira.removeLabelsFromIssue(issue.id, this.usecase.JIRA_TEST_CASE_LABELS)
                this.usecase.jira.addLabelsToIssue(issue.id, ["Missing"])
            }
        }

        this.usecase.matchJiraTestIssuesAgainstTestResults(jiraTestIssues, testResults, matchedHandler, unmatchedHandler)
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
