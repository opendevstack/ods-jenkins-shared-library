import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
@Grab('org.apache.httpcomponents:httpclient:4.5.9')
import org.apache.http.client.utils.URIBuilder
// TODO:
// - retrieve Jira credentials and host name in the Jenkins credentials store
// - document creation is in the scope of each repository, not the orchestration pipeline
def call(projectMetadata) {
    //def credentials = "admin:admin".bytes.encodeBase64().toString()

    // Request the Jira issue with the label VP in the current project
    def jiraSearchURI = new URIBuilder()
            .setScheme("https")
            .setHost("jira.biscrum.com")
            .setPort(443)
            .setPath("/rest/api/2/search")
            .addParameter("jql", "project = ${projectMetadata.services.jira.project.key} and labels = VP")
            .build()

    def response = httpRequest url: jiraSearchURI.toString(),
            httpMode: 'GET',
            acceptType: 'APPLICATION_JSON',
            authentication: 'cd-user-with-password',
            ignoreSslErrors: true
    // customHeaders: [[ name: 'Authorization', value: "Basic ${credentials}" ]]

    def responseContent = new JsonSlurperClassic().parseText(response.content)
    if (responseContent.total > 1) {
        error "Error: Jira reports there is > 1 issues with label 'VP' in project '${projectMetadata.services.jira.project.key}'"
    }
    println "Response: ${JsonOutput.toJson(responseContent)}"
    return responseContent.issues[0]
}