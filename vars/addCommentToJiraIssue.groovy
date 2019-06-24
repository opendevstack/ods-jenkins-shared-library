@Grab('org.apache.httpcomponents:httpclient:4.5.9')

import groovy.json.JsonOutput

import org.apache.http.client.utils.URIBuilder

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

    def uri = new URIBuilder()
        .setScheme(env.JIRA_SCHEME)
        .setHost(env.JIRA_HOST)
        .setPort(Integer.parseInt(env.JIRA_PORT))
        .setPath("/rest/api/2/issue/${issueIdOrKey}/comment")
        .build()

    httpRequest url: uri.toString(),
        consoleLogResponseBody: true,
        httpMode: 'POST',
        acceptType: 'APPLICATION_JSON',
        contentType: 'APPLICATION_JSON',
        authentication: 'jira',
        ignoreSslErrors: true,
        requestBody: JsonOutput.toJson(
            [ body: comment ]
        )
}