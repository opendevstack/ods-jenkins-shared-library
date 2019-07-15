import org.ods.service.JiraService

// Searches Jira issues using JQL
def call(String query) {
    return new JiraService(env.JIRA_URL, env.JIRA_USERNAME, env.JIRA_PASSWORD)
        .getIssuesForJQLQuery(query)
}

return this
