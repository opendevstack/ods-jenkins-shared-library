// Demo Scenario: notify Jira about the creation of a document
@Grab('org.apache.httpcomponents:httpclient:4.5.9')
import org.apache.http.client.utils.URIBuilder
// TODO:
// - retrieve JIRA host name and configurations from Jenkins environment
// - document creation is in the scope of each repository, not the orchestration pipeline
def call(projectMetadata) {
  
//  // Request the Jira issue with the label VP in the current project
//  def jiraSearchURI = new URIBuilder()
//      .setScheme("https")
//      .setHost("jira.biscrum.com")
//      .setPort(443)
//      .setPath("/rest/api/2/search")
//      .addParameter("jql", "project = ${projectMetadata.services.jira.project.key} and labels = VP")
//      .build()
//  def response = httpRequest url: jiraSearchURI.toString(),
//    httpMode: 'GET',
//    acceptType: 'APPLICATION_JSON',
//    authentication: 'jira-credentials'
//
//  def responseContent = new groovy.json.JsonSlurperClassic().parseText(response.content)
//  if (responseContent.total > 1) {
//    error "Error: Jira reports there is > 1 issues with label 'VP' in project '${projectMetadata.services.jira.project.key}'"
//  }
//
//  // Add a comment to the previously queried Jira issue
//  def jiraIssueURI = new URIBuilder()
//      .setScheme("https")
//      .setHost("jira.biscrum.com")
//      .setPort(443)
//      .setPath("/rest/api/2/issue/${responseContent.issues[0].id}/comment")
//      .build()
//
//  httpRequest url: jiraIssueURI.toString(),
//    consoleLogResponseBody: true,
//    httpMode: 'POST',
//    acceptType: 'APPLICATION_JSON',
//    contentType: 'APPLICATION_JSON',
//    authentication: 'jira-credentials',
//    requestBody: groovy.json.JsonOutput.toJson([ body: "A new document has been generated and is available at: http://." ])
}
