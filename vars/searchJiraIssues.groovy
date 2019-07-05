@Grab('org.apache.httpcomponents:httpclient:4.5.9')

import groovy.json.JsonSlurperClassic

import org.apache.http.client.utils.URIBuilder

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

    def uri = new URIBuilder()
        .setScheme(env.JIRA_SCHEME)
        .setHost(env.JIRA_HOST)
        .setPort(Integer.parseInt(env.JIRA_PORT))
        .setPath("/rest/api/2/search")
        .addParameter("jql", query)
        .build()

    def response = httpRequest url: uri.toString(),
        httpMode: 'GET',
        acceptType: 'APPLICATION_JSON',
        authentication: 'jira',
        ignoreSslErrors: true

    return new JsonSlurperClassic().parseText(response.content).issues
}

return this
