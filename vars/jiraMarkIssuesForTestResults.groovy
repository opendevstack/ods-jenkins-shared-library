import org.ods.service.JiraService

// Adds a commment to a Jira issue
def call(Map metadata, Map testResults, Map jiraIssues) {
    def jiraLabels = ["Error", "Failed", "Missing", "Skipped", "Succeeded"]

    withCredentials([ usernamePassword(credentialsId: metadata.services.jira.credentials.id, usernameVariable: "JIRA_USERNAME", passwordVariable: "JIRA_PASSWORD") ]) {
        def jira = new JiraService(env.JIRA_URL, env.JIRA_USERNAME, env.JIRA_PASSWORD)

        def jiraIssuesProcessed = [:]
        testResults.each { testsuiteName, testsuite ->
            testsuite.each { testcaseName, testcase ->
                // Find the matching Jira issue for the given executed testcase
                def jiraIssue = jiraIssues.find { issueId, issue ->
                    issue.summary == testcaseName && issue.parent.summary == testsuiteName
                }

                if (!jiraIssue) {
                    // There is no Jira issue for the executed testcase
                    return
                }

                jiraIssue = jiraIssue.value

                // Remove all labels from the issue
                jira.removeLabelsFromIssue(jiraIssue.id, jiraLabels)

                def jiraLabelsToApply = ["Succeeded"]
                if (testcase.skipped || testcase.error || testcase.failure) {
                    if (testcase.error) {
                        jiraLabelsToApply = ["Error"]
                    }

                    if (testcase.failure) {
                        jiraLabelsToApply = ["Failed"]
                    }

                    if (testcase.skipped) {
                        jiraLabelsToApply = ["Skipped"]
                    }
                }

                // Apply all appropriate labels to the issue
                jira.addLabelsToIssue(jiraIssue.id, jiraLabelsToApply)

                jiraIssuesProcessed << [ (jiraIssue.id): jiraIssue ]
            }
        }

        // Add label "Missing" to all unprocessed issues
        def jiraIssuesUnprocessed = jiraIssues - jiraIssuesProcessed
        jiraIssuesUnprocessed.each { issueId, issue ->
            jira.addLabelsToIssue(issueId, ["Missing"])
        }
    }
}

return this
