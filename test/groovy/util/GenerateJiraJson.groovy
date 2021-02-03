package util


import org.ods.orchestration.service.JiraService

class GenerateJiraJson {
    public static final String JIRA_JSON_FILES = "build/tmp/json/jira"
    public static final String URL = "http://jira.odsbox.lan:8080"
    public static final String USER = "openshift"
    JiraJsonService jiraJsonService
    JiraService jiraService


    static void main(String[] args) {
        def projectKey = "TES89"
        def version = "1.0"
        def releaseStatusJiraIssueKey = "${projectKey}-123"
        def customFields = ["customfield_10222"]
// /rest/api/2/issue/T4-108?fields=customfield_10213
        new GenerateJiraJson().start(projectKey, version, releaseStatusJiraIssueKey, customFields)
    }

    void start(projectKey, version, releaseStatusJiraIssueKey, customFields) {
        jiraOutputDirExist()
        jiraJsonService= new JiraJsonService(URL, USER, USER)
        jiraService = new JiraService(URL, USER, USER)
        generateJsonFile("getDocGenData", jiraJsonService.getDocGenData(projectKey))
        generateJsonFile("getDeltaDocGenData", jiraJsonService.getDeltaDocGenData(projectKey, version))
        generateJsonFile("getIssueTypes", jiraJsonService.getIssueTypes(projectKey))
        generateIssueTypes(projectKey)
        generateJsonFile("getVersionsForProject", jiraJsonService.getVersionsForProject(projectKey))
        generateJsonFile("getProject", jiraJsonService.getProject(projectKey))
        generateJsonFile("getProjectVersions", jiraJsonService.getProjectVersions(projectKey))
        //generateJsonFile("getLabelsFromIssue", jiraJsonService.getLabelsFromIssue(issueIdOrKey))
        getDocumentChapterData(projectKey)
        customFields.each {
            List field = [it]
            generateJsonFile(it, jiraJsonService.getTextFieldsOfIssue(releaseStatusJiraIssueKey, field))
        }
        generateJsonFile("customfield_10213", jiraJsonService.getTextFieldsOfIssue("T4-108", ["customfield_10213"]))
        //generateJsonFile("getTextFieldsOfIssue", jiraService.isVersionEnabledForDelta(projectKey, versionName))

    }

    private getDocumentChapterData(projectKey) {
        def contentField = 'customfield_10209'
        def headingNumberField = 'customfield_10201'
        def query = [
            fields: ['key', 'status', 'summary', 'labels', 'issuelinks', contentField, headingNumberField],
            jql: "project = ${projectKey} AND issuetype = 'Documentation Chapter'",
            expand: ['renderedFields'],
            maxResults:"1000"
        ]
        generateJsonFile("searchByJQLQuery", jiraJsonService.searchByJQLQuery(query))
    }

    private generateIssueTypes(String projectKey) {
        Map types = jiraService.getIssueTypes(projectKey)
        types.get("values").each {
            generateJsonFile("getIssueTypeMetadata-${it.id}", jiraJsonService.getIssueTypeMetadata(projectKey, it.id))
        }

    }

    private void generateJsonFile(String fileName, def jsonData) {
        new File("${JIRA_JSON_FILES}/${fileName}.json").text = jsonData
    }

    private void jiraOutputDirExist() {
        def json_path = new File(JIRA_JSON_FILES)
        if (json_path.exists()) {
            json_path.deleteDir()
        }
        json_path.mkdirs()
    }
}
