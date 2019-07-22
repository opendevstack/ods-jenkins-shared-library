import org.ods.service.JiraService

// Create Jira bugs for test failures
def call(Map metadata, Collection testFailures, Map jiraIssues) {
    withCredentials([ usernamePassword(credentialsId: metadata.services.jira.credentials.id, usernameVariable: "JIRA_USERNAME", passwordVariable: "JIRA_PASSWORD") ]) {
        def jira = new JiraService(env.JIRA_URL, env.JIRA_USERNAME, env.JIRA_PASSWORD)

        // TODO: also handle errors
        testFailures.each { failure ->
            createBugAndLinkTestCases(metadata, jira, failure, jiraIssues)
        }
    }
}

private def createBugAndLinkTestCases(def metadata, def jira, def failure, def jiraIssues) {
    // Create a Jira bug for the failure in the current project
    def bug = jira.createIssueTypeBug(metadata.id, failure.type, failure.text)
    jira.appendCommentToIssue(bug.key, env.RUN_DISPLAY_URL)

    // Iterate through all test cases affected by this failure
    failure.testsuites.each { testsuiteName, testsuite ->
        testsuite.testcases.each { testcaseName ->
            // Find the issue in Jira representing the test case
            def issue = jiraIssues.find { id, issue ->
                issue.summary == testcaseName
            }.value

            jira.createIssueLinkTypeBlocks(bug, issue)
        }
    }
}

return this
