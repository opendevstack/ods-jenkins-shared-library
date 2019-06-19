import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
@Grab('org.apache.httpcomponents:httpclient:4.5.9')
import org.apache.http.client.utils.URIBuilder

def call(taskKey, comment) {
    def jiraPort = "${env.JIRA_PORT}" ?: '443'
    def jiraProto = "${env.JIRA_PROTO}" ?: 'https'
    def jiraHost = "${env.JIRA_HOST}" ?: 'jira.biscrum.com'

    def jiraIssueURI = new URIBuilder()
            .setScheme(jiraProto)
            .setHost(jiraHost)
            .setPort(Integer.parseInt(jiraPort))
            .setPath("/rest/api/2/issue/${taskKey}/comment")
            .build()

    httpRequest url: jiraIssueURI.toString(),
            consoleLogResponseBody: true,
            httpMode: 'POST',
            acceptType: 'APPLICATION_JSON',
            contentType: 'APPLICATION_JSON',
            authentication: 'jira',
            ignoreSslErrors: true,
            requestBody: JsonOutput.toJson([ body: "${comment}" ])


}