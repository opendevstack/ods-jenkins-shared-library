import org.ods.service.JiraService

// Searches Jira issues using JQL
def call(Map metadata, String query) {
    def result = []

    withCredentials([ usernamePassword(credentialsId: metadata.services.jira.credentials.id, usernameVariable: "JIRA_USERNAME", passwordVariable: "JIRA_PASSWORD") ]) {
        result = new JiraService(env.JIRA_URL, env.JIRA_USERNAME, env.JIRA_PASSWORD)
            .getIssuesForJQLQuery(query)
    }

    // Transform the result into a simple format
    return JiraService.Helper.toSimpleIssuesFormat(result)
}

return this
