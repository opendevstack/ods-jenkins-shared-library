package util

import groovy.json.JsonSlurper
import org.ods.orchestration.service.JiraService

class GenerateJiraJson {
    public static final String JIRA_JSON_FILES = "build/tmp/json/jira"
    public static final String URL = "http://jira.odsbox.lan:8080"
    public static final String USER = "openshift"
    public static final String PRODUCT_RELEASE_VERSION = "ProductRelease Version"
    private static final String EDP_HEADING_NUMBER = "EDP Heading Number"
    private static final String EDP_CONTENT = "EDP Content"
    private static final String DOCUMENT_VERSION = "Document Version"

    JiraJsonService jiraJsonService
    JiraService jiraService

    String productReleaseVersionCustomField
    String headingNumberCustomField
    String edpContentCustomField
    String documentVersion

    static void main(String[] args) {
        def projectKey = "TES89"
        def version = "1.0"
        def releaseStatusJiraIssueKey = "${projectKey}-123"
        new GenerateJiraJson().start(projectKey, version, releaseStatusJiraIssueKey)
    }

    void start(projectKey, version, releaseStatusJiraIssueKey) {
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
        getDocumentChapterData(projectKey)

        [documentVersion, productReleaseVersionCustomField].each {
            List field = [it]
            generateJsonFile(it, jiraJsonService.getTextFieldsOfIssue(releaseStatusJiraIssueKey, field))
        }

       // generateJsonFile(productReleaseVersionCustomField, jiraJsonService.getTextFieldsOfIssue(releaseStatusJiraIssueKey, [productReleaseVersionCustomField]))
        //generateJsonFile("getTextFieldsOfIssue", jiraService.isVersionEnabledForDelta(projectKey, versionName))
        //generateJsonFile("getLabelsFromIssue", jiraJsonService.getLabelsFromIssue(issueIdOrKey))
    }

    private getDocumentChapterData(projectKey) {
        def query = [
            fields: ['key', 'status', 'summary', 'labels', 'issuelinks', edpContentCustomField, headingNumberCustomField],
            jql: "project = ${projectKey} AND issuetype = 'Documentation Chapter'",
            expand: ['renderedFields'],
            maxResults:"1000"
        ]
        generateJsonFile("searchByJQLQuery", jiraJsonService.searchByJQLQuery(query))
    }

    private generateIssueTypes(String projectKey) {
        Map types = jiraService.getIssueTypes(projectKey)
        types.get("values").each {
            generateJsonFile("getIssueTypeMetadata-${it.id}", getIssueTypeMetadata(projectKey, it))
        }

    }

    private String getIssueTypeMetadata(String projectKey, it) {
        String metadata = jiraJsonService.getIssueTypeMetadata(projectKey, it.id)
        if (metadata.contains(PRODUCT_RELEASE_VERSION))
            setProductReleaseVersionCustomField(getCustomFieldId(metadata, PRODUCT_RELEASE_VERSION))
        if (metadata.contains(DOCUMENT_VERSION))
            setDocumentVersion(getCustomFieldId(metadata, DOCUMENT_VERSION))
        if (metadata.contains(EDP_HEADING_NUMBER)){
            setHeadingNumberCustomField(getCustomFieldId(metadata, EDP_HEADING_NUMBER))
            setEdpContentCustomField(getCustomFieldId(metadata, EDP_CONTENT))
        }

        return metadata
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

    void setHeadingNumberCustomField(String metadataValue, description) {
        headingNumberCustomField = getCustomFieldId (metadataValue, description)
    }

    void setEdpContentCustomField(String metadataValue, description) {
        edpContentCustomField = getCustomFieldId (metadataValue, description)
    }

    private Object getCustomFieldId(String metadataValue, String description) {
        def slurper = new JsonSlurper().parseText(metadataValue)
        def field = slurper.values.find { it.name == description }
        return field.fieldId
    }
}
