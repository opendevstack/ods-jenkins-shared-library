import org.ods.service.JiraService

// Adds a commment to Jira issue
def call(String issueIdOrKey, String comment) {
    new JiraService(env.JIRA_URL, env.JIRA_USERNAME, env.JIRA_PASSWORD)
        .appendCommentToIssue(issueIdOrKey, comment)
}

return this
