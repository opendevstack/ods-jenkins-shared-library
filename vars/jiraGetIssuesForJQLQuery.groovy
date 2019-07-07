// Searches Jira issues using JQL
def call(String query) {
    if (!env.JIRA_SCHEME) {
        error "unable to connect to Jira. env.JIRA_SCHEME is undefined"
    }

    if (!env.JIRA_HOST) {
        error "unable to connect to Jira: env.JIRA_HOST is undefined"
    }

    if (!env.JIRA_PORT) {
        error "unable to connect to Jira: env.JIRA_PORT is undefined"
    }

    if (!query) {
        error "unable to search for Jira issues: query parameter is undefined"
    }

    def result = []
    withCredentials([ usernamePassword(credentialsId: "jira", usernameVariable: "JIRA_USERNAME", passwordVariable: "JIRA_PASSWORD") ]) {
        result = new org.ods.service.JiraService(
            [
                scheme: env.JIRA_SCHEME,
                host: env.JIRA_HOST,
                port: Integer.parseInt(env.JIRA_PORT),
                username: env.JIRA_USERNAME,
                password: env.JIRA_PASSWORD
            ]
        ).getIssuesForJQLQuery(query)
    }

    return result
}

return this
