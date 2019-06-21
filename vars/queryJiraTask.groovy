import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
@Grab('org.apache.httpcomponents:httpclient:4.5.9')
import org.apache.http.client.utils.URIBuilder

def call(projectMetadata, query = null) {

    def jiraPort = "${env.JIRA_PORT}" ?: '443'
    def jiraProto = "${env.JIRA_PROTO}" ?: 'https'
    def jiraHost = "${env.JIRA_HOST}" ?: 'jira.biscrum.com'

    // Request the Jira issue with the label VP in the current project
    def cqlQuery = query ?: "project = ${projectMetadata.services.jira.project.key} and labels = VP"
    def jiraSearchURI = new URIBuilder()
            .setScheme(jiraProto)
            .setHost(jiraHost)
            .setPort(Integer.parseInt(jiraPort))
            .setPath("/rest/api/2/search")
            .addParameter("jql", cqlQuery)
            .build()

    def response = httpRequest url: jiraSearchURI.toString(),
            httpMode: 'GET',
            acceptType: 'APPLICATION_JSON',
            authentication: 'jira',
            ignoreSslErrors: true
    // customHeaders: [[ name: 'Authorization', value: "Basic ${credentials}" ]]

    def responseContent = new JsonSlurperClassic().parseText(response.content)
    if (responseContent.total != 1 ) {
        error "Error: Jira reports there is != 1 for query '${cqlQuery}'"
    }
    println "Response: ${JsonOutput.toJson(responseContent)}"
    return responseContent.issues[0]
}