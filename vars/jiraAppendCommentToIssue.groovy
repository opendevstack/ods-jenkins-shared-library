import org.ods.service.JiraService

// Adds a commment to a Jira issue
def call(Map metadata, String issueIdOrKey, String comment) {
    withCredentials([ usernamePassword(credentialsId: metadata.services.jira.credentials.id, usernameVariable: "JIRA_USERNAME", passwordVariable: "JIRA_PASSWORD") ]) {
        new JiraService(env.JIRA_URL, env.JIRA_USERNAME, env.JIRA_PASSWORD)
            .appendCommentToIssue(issueIdOrKey, comment)
    }
}

return this
