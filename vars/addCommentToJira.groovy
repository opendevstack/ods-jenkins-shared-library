import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
@Grab('org.apache.httpcomponents:httpclient:4.5.9')
import org.apache.http.client.utils.URIBuilder

def call(projectMetadata, taskKey, comment) {
    def jiraIssueURI = new URIBuilder()
            .setScheme("https")
            .setHost("jira.biscrum.com")
            .setPort(443)
            .setPath("/rest/api/2/issue/${taskKey}/comment")
            .build()

    httpRequest url: jiraIssueURI.toString(),
            consoleLogResponseBody: true,
            httpMode: 'POST',
            acceptType: 'APPLICATION_JSON',
            contentType: 'APPLICATION_JSON',
            authentication: 'cd-user-with-password',
            ignoreSslErrors: true,
            requestBody: JsonOutput.toJson([ body: "${comment}" ])


}