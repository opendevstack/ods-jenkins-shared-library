import org.ods.service.JiraService

// Adds a commment to a Jira issue
def call(Map metadata, Map testResults, Map jiraIssues) {
    def jiraLabels = ["Error", "Failure", "Missing", "Skipped", "Success"]

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

                def jiraLabelsToApply = ["Success"]
                if (testcase.skipped || testcase.error || testcase.failure) {
                    if (testcase.skipped) {
                        jiraLabelsToApply = ["Skipped"]
                    }

                    if (testcase.error) {
                        jiraLabelsToApply = ["Error"]
                    }

                    if (testcase.failure) {
                        jiraLabelsToApply = ["Failure"]
                    }
                }

                // Remove deprecated labels from the issue
                def jiraLabelsToRemove = jiraLabels - jiraLabelsToApply
                jira.removeLabelsFromIssue(jiraIssue.id, jiraLabelsToRemove)

                // Apply appropriate labels to the issue
                jira.addLabelsToIssue(jiraIssue.id, jiraLabelsToApply)

                jiraIssuesProcessed << jiraIssue
            }
        }

        // Handle unprocessed Jira issues (for which no tests were executed)
        def jiraIssuesUnprocessed = jiraIssues - jiraIssuesProcessed
        jiraIssuesUnprocessed.each { issueId, issue ->
            def jiraLabelsToApply = ["Missing"]

            // Apply appropriate labels to the issue
            jira.addLabelsToIssue(issue.id, jiraLabelsToApply)

            // Remove deprecated labels from the issue
            def jiraLabelsToRemove = jiraLabels - jiraLabelsToApply
            jira.removeLabelsFromIssue(issue.id, jiraLabelsToRemove)
        }
    }
}

return this
