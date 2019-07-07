// Adds a commment to Jira issue
def call(String issueIdOrKey, String comment) {
    if (!env.JIRA_SCHEME) {
        error "Error: unable to connect to Jira: env.JIRA_SCHEME is undefined"
    }

    if (!env.JIRA_HOST) {
        error "Error: unable to connect to Jira: env.JIRA_HOST is undefined"
    }

    if (!env.JIRA_PORT) {
        error "Error: unable to connect to Jira: env.JIRA_PORT is undefined"
    }

    withCredentials([ usernamePassword(credentialsId: "jira", usernameVariable: "JIRA_USERNAME", passwordVariable: "JIRA_PASSWORD") ]) {
        new org.ods.service.JiraService(
            [
                scheme: env.JIRA_SCHEME,
                host: env.JIRA_HOST,
                port: Integer.parseInt(env.JIRA_PORT),
                username: env.JIRA_USERNAME,
                password: env.JIRA_PASSWORD
            ]
        ).appendCommentToIssue(issueIdOrKey, comment)
    }
}

return this
