package org.ods.orchestration.service

@Grab(group="com.konghq", module="unirest-java", version="2.4.03", classifier="standalone")

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import kong.unirest.Unirest
import org.apache.http.client.utils.URIBuilder
import org.ods.orchestration.util.StringCleanup
import org.ods.util.ILogger

@SuppressWarnings(['LineLength', 'ParameterName'])
class JiraService {

    protected static Map CHARACTER_REMOVEABLE = [
        '\u00A0': ' ',
    ]

    URI baseURL

    String username
    String password

    private final ILogger logger

    JiraService(String baseURL, String username, String password, ILogger logger) {
        this.logger = logger

        if (!baseURL?.trim()) {
            throw new IllegalArgumentException('Error: unable to connect to Jira. \'baseURL\' is undefined.')
        }

        if (!username?.trim()) {
            throw new IllegalArgumentException('Error: unable to connect to Jira. \'username\' is undefined.')
        }

        if (!password?.trim()) {
            throw new IllegalArgumentException('Error: unable to connect to Jira. \'password\' is undefined.')
        }

        if (baseURL.endsWith('/')) {
            baseURL = baseURL.substring(0, baseURL.size() - 1)
        }

        try {
            this.baseURL = new URIBuilder(baseURL).build()
        } catch (e) {
            throw new IllegalArgumentException("Error: unable to connect to Jira. '${baseURL}' is not a valid URI.").initCause(e)
        }

        this.username = username
        this.password = password
    }

    @NonCPS
    void addLabelsToIssue(String issueIdOrKey, List names) {
        if (!issueIdOrKey?.trim()) {
            throw new IllegalArgumentException('Error: unable to add labels to Jira issue. \'issueIdOrKey\' is undefined.')
        }

        if (!names) {
            throw new IllegalArgumentException('Error: unable to add labels to Jira issue. \'names\' is undefined.')
        }

        def response = Unirest.put("${this.baseURL}/rest/api/2/issue/{issueIdOrKey}")
            .routeParam("issueIdOrKey", issueIdOrKey)
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson(
                [
                    update: [
                        labels: names.collect { name ->
                            [add: name]
                        }
                    ]
                ]
            ))
            .asString()

        response.ifFailure {
            def message = "Error: unable to add labels to Jira issue. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to add labels to Jira issue. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }
    }

    @NonCPS
    void setIssueLabels(String issueIdOrKey, List names) {
        if (!issueIdOrKey?.trim()) {
            throw new IllegalArgumentException('Error: unable to set labels for Jira issue. \'issueIdOrKey\' is undefined.')
        }

        if (names == null) {
            throw new IllegalArgumentException('Error: unable to set labels for Jira issue. \'names\' is undefined.')
        }

        def response = Unirest.put("${this.baseURL}/rest/api/2/issue/{issueIdOrKey}")
            .routeParam("issueIdOrKey", issueIdOrKey)
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson(
                [
                    fields: [
                        labels: names
                    ]
                ]
            ))
            .asString()

        response.ifFailure {
            def message = "Error: unable to set labels for Jira issue. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to set labels for Jira issue. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }
    }

    @NonCPS
    void appendCommentToReleaseStatusIssue(String projectKey, String version, String comment) {
        if (!projectKey?.trim()) {
            throw new IllegalArgumentException('Error: Unable to append comment to the release status issue: \'projectKey\' is undefined')
        }
        if (!version?.trim()) {
            throw new IllegalArgumentException('Error: Unable to append comment to  the release status issue: \'version\' is undefined')
        }
        if (!comment?.trim()) {
            throw new IllegalArgumentException('Error: Unable to append comment to  the release status issue: \'comment\' is undefined')
        }

        def response = Unirest.post("${this.baseURL}/rest/platform/1.1/productreleases/{projectKey}/{version}/status/comment")
            .routeParam("projectKey", projectKey.toUpperCase())
            .routeParam("version", version)
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header('Content-Type', 'application/json')
            .body(JsonOutput.toJson([content: comment])).asString()

        response.ifFailure {
            def message = 'Error: unable to append comment to the release status issue. Jira responded with code: ' +
                "'${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = 'Error: unable to append comment to tne release status issue. ' +
                    "Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }
    }

    @NonCPS
    void appendCommentToIssue(String issueIdOrKey, String comment) {
        if (!issueIdOrKey?.trim()) {
            throw new IllegalArgumentException('Error: unable to append comment to Jira issue. \'issueIdOrKey\' is undefined.')
        }

        if (!comment?.trim()) {
            throw new IllegalArgumentException('Error: unable to append comment to Jira issue. \'comment\' is undefined.')
        }

        def response = Unirest.post("${this.baseURL}/rest/api/2/issue/{issueIdOrKey}/comment")
            .routeParam("issueIdOrKey", issueIdOrKey)
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson(
                [body: comment]
            ))
            .asString()

        response.ifFailure {
            def message = "Error: unable to append comment to Jira issue. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to append comment to Jira issue. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }
    }

    @NonCPS
    void createIssueLinkType(String linkType, Map inwardIssue, Map outwardIssue) {
        if (!linkType?.trim()) {
            throw new IllegalArgumentException('Error: unable to create Jira issue link. \'linkType\' is undefined.')
        }

        if (!inwardIssue) {
            throw new IllegalArgumentException('Error: unable to create Jira issue link. \'inwardIssue\' is undefined.')
        }

        if (!outwardIssue) {
            throw new IllegalArgumentException('Error: unable to create Jira issue link. \'outwardIssue\' is undefined.')
        }

        def response = Unirest.post("${this.baseURL}/rest/api/2/issueLink")
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson(
                [
                    type: [
                        name: linkType
                    ],
                    inwardIssue: [
                        key: inwardIssue.key
                    ],
                    outwardIssue: [
                        key: outwardIssue.key
                    ],
                ]
            ))
            .asString()

        response.ifSuccess {
            if (response.getStatus() != 201) {
                throw new RuntimeException("Error: unable to create Jira issue link. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'.")
            }
        }

        response.ifFailure {
            def message = "Error: unable to create Jira issue link. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to create Jira issue link. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }
    }

    @NonCPS
    void createIssueLinkTypeBlocks(Map inwardIssue, Map outwardIssue) {
        createIssueLinkType("Blocks", inwardIssue, outwardIssue)
    }

    @NonCPS
    Map createIssue(Map args) {
        if (!args.type?.trim()) {
            throw new IllegalArgumentException('Error: unable to create Jira issue. \'type\' is undefined.')
        }

        if (!args.projectKey?.trim()) {
            throw new IllegalArgumentException('Error: unable to create Jira issue. \'projectKey\' is undefined.')
        }

        if (!args.summary?.trim()) {
            throw new IllegalArgumentException('Error: unable to create Jira issue. \'summary\' is undefined.')
        }

        if (!args.description?.trim()) {
            throw new IllegalArgumentException('Error: unable to create Jira issue. \'description\' is undefined.')
        }

        def request = [
            fields: [
                project: [
                    key: args.projectKey.toUpperCase()
                ],
                summary: args.summary,
                description: args.description,
                fixVersions: [
                    [name: args.fixVersion]
                ],
                issuetype: [
                    name: args.type
                ]
            ]
        ]

        if (args.component) {
            request.fields << [components: [[name: args.component]]]
        }
        if (args.priority) {
            request.fields << [priority: [name: args.priority]]
        }

        def response = Unirest.post("${this.baseURL}/rest/api/2/issue")
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson(request))
            .asString()

        response.ifSuccess {
            if (response.getStatus() != 201) {
                throw new RuntimeException("Error: unable to create Jira issue. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'.")
            }
        }

        response.ifFailure {
            def message = "Error: unable to create Jira issue. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to create Jira issue. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(response.getBody())
    }

    Map createIssueTypeBug(String projectKey, String summary, String description, String fixVersion = null) {
        if (!description?.trim()) {
            description = 'N/A - please check logs'
        }

        return createIssue(fixVersion: fixVersion, summary: summary, type: "Bug", projectKey: projectKey,
            description: description)
    }

    @NonCPS
    Map getDocGenData(String projectKey) {
        if (!projectKey?.trim()) {
            throw new IllegalArgumentException('Error: unable to get documentation generation data from Jira. ' +
                '\'projectKey\' is undefined.')
        }

        def response = Unirest.get("${this.baseURL}/rest/platform/1.0/docgenreports/{projectKey}")
            .routeParam("projectKey", projectKey.toUpperCase())
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .asString()

        response.ifFailure {
            def message = 'Error: unable to get documentation generation data. Jira responded with code: ' +
                "'${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = 'Error: unable to get documentation generation data. ' +
                    "Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(StringCleanup.removeCharacters(response.getBody(), CHARACTER_REMOVEABLE))
    }

    @NonCPS
    Map getDeltaDocGenData(String projectKey, String version) {
        if (!projectKey?.trim()) {
            throw new IllegalArgumentException('Error: unable to get documentation generation data from Jira. ' +
                '\'projectKey\' is undefined.')
        }

        def response = Unirest.get("${this.baseURL}/rest/platform/1.1/deltadocgenreports/{projectKey}/{version}")
            .routeParam("projectKey", projectKey.toUpperCase())
            .routeParam("version", version)
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .asString()

        response.ifFailure {
            def message = 'Error: unable to get documentation generation data. Jira responded with code: ' +
                "'${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = 'Error: unable to get documentation generation data. ' +
                    "Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(StringCleanup.removeCharacters(response.getBody(), CHARACTER_REMOVEABLE))
    }

    @NonCPS
    Map getFileFromJira(String url) {
        def response = Unirest.get(url)
            .basicAuth(this.username, this.password)
            .asBytes()

        response.ifFailure {
            def message = "Error: unable to get file from Jira. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to get file from Jira. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        return [
            contentType: response.getHeaders()["Content-Type"][0],
            data: response.getBody(),
        ]
    }

    @NonCPS
    List getIssuesForJQLQuery(Map query) {
        return searchByJQLQuery(query).issues
    }

    @NonCPS
    Map getIssueTypeMetadata(String projectKey, String issueTypeId) {
        if (!projectKey?.trim()) {
            throw new IllegalArgumentException('Error: unable to get Jira issue type metadata. \'projectKey\' is undefined.')
        }

        if (!issueTypeId?.trim()) {
            throw new IllegalArgumentException('Error: unable to get Jira issue type metadata. \'issueTypeId\' is undefined.')
        }

        def response = Unirest.get("${this.baseURL}/rest/api/2/issue/createmeta/{projectKey}/issuetypes/{issueTypeId}")
            .routeParam('projectKey', projectKey.toUpperCase())
            .routeParam('issueTypeId', issueTypeId)
            .basicAuth(this.username, this.password)
            .header('Accept', 'application/json')
            .asString()

        response.ifFailure {
            def message = "Error: unable to get Jira issue type metadata. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to get Jira issue type metadata. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(response.getBody())
    }

    @NonCPS
    Map getIssueTypes(String projectKey) {
        if (!projectKey?.trim()) {
            throw new IllegalArgumentException('Error: unable to get Jira issue types. \'projectKey\' is undefined.')
        }

        def response = Unirest.get("${this.baseURL}/rest/api/2/issue/createmeta/{projectKey}/issuetypes")
            .routeParam('projectKey', projectKey.toUpperCase())
            .basicAuth(this.username, this.password)
            .header('Accept', 'application/json')
            .asString()

        response.ifFailure {
            def message = "Error: unable to get Jira issue types. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to get Jira issue types. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(response.getBody())
    }

    @NonCPS
    List<Map> getVersionsForProject(String projectKey) {
        if (!projectKey?.trim()) {
            throw new IllegalArgumentException('Error: unable to load project version from Jira. \'projectKey\' is undefined.')
        }

        def response = Unirest.get("${this.baseURL}/rest/api/2/project/{projectKey}/versions")
            .routeParam('projectKey', projectKey.toUpperCase())
            .basicAuth(this.username, this.password)
            .header('Accept', 'application/json')
            .asString()

        response.ifFailure {
            def message = "Error: unable to get project version for projectKey ${projectKey}. Jira responded with code: '${response.getStatus()}' and message '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to get project version. Jira could not resolve the URL '${this.baseURL}/rest/api/2/project/${projectKey}/versions'."
            }

            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(response.getBody()).collect { jiraVersion ->
            [id: jiraVersion.id, name: jiraVersion.name]
        }
    }

    @NonCPS
    Map getProject(String projectKey) {
        if (!projectKey?.trim()) {
            throw new IllegalArgumentException('Error: unable to get project from Jira. \'projectKey\' is undefined.')
        }

        def response = Unirest.get("${this.baseURL}/rest/api/2/project/{projectKey}")
            .routeParam('projectKey', projectKey.toUpperCase())
            .basicAuth(this.username, this.password)
            .header('Accept', 'application/json')
            .asString()

        response.ifFailure {
            def message = "Error: unable to get project. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to get project. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(response.getBody())
    }

    @NonCPS
    List getProjectVersions(String projectKey) {
        if (!projectKey?.trim()) {
            throw new IllegalArgumentException('Error: unable to get project versions from Jira. \'projectKey\' is undefined.')
        }

        def response = Unirest.get("${this.baseURL}/rest/api/2/project/{projectKey}/versions")
            .routeParam('projectKey', projectKey.toUpperCase())
            .basicAuth(this.username, this.password)
            .header('Accept', 'application/json')
            .asString()

        response.ifFailure {
            def message = "Error: unable to get project versions. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to get project versions. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(response.getBody()) ?: []
    }

    @NonCPS
    List<String> getLabelsFromIssue(String issueIdOrKey) {
        if (!issueIdOrKey?.trim()) {
            throw new IllegalArgumentException('Error: unable to remove labels from Jira issue. \'issueIdOrKey\' is undefined.')
        }

        def response = Unirest.get("${this.baseURL}/rest/api/2/issue/{issueIdOrKey}")
            .routeParam("issueIdOrKey", issueIdOrKey)
            .queryString('fields', 'labels')
            .basicAuth(this.username, this.password)
            .header('Accept', 'application/json')
            .asString()

        response.ifFailure {
            def message = "Error: unable to get labels from Jira issue. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."
            if (response.getStatus() == 404) {
                message = "Error: unable to get labels from Jira issue. Jira could not be found at: '${this.baseURL}'."
            }
            throw new RuntimeException(message)
        }

        new JsonSlurperClassic().parseText(response.getBody()).getOrDefault("fields", [:]).getOrDefault("labels", [])
    }

    @NonCPS
    void removeLabelsFromIssue(String issueIdOrKey, List names) {
        if (!issueIdOrKey?.trim()) {
            throw new IllegalArgumentException('Error: unable to remove labels from Jira issue. \'issueIdOrKey\' is undefined.')
        }

        if (!names) {
            throw new IllegalArgumentException('Error: unable to remove labels from Jira issue. \'names\' is undefined.')
        }

        def response = Unirest.put("${this.baseURL}/rest/api/2/issue/{issueIdOrKey}")
            .routeParam('issueIdOrKey', issueIdOrKey)
            .basicAuth(this.username, this.password)
            .header('Accept', 'application/json')
            .header('Content-Type', 'application/json')
            .body(JsonOutput.toJson(
                [
                    update: [
                        labels: names.collect { name ->
                            [remove: name]
                        }
                    ]
                ]
            ))
            .asString()

        response.ifFailure {
            def message = "Error: unable to remove labels from Jira issue. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to remove labels from Jira issue. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }
    }

    @NonCPS
    Map searchByJQLQuery(Map query) {
        if (!query) {
            throw new IllegalArgumentException('Error: unable to get Jira issues for JQL query. \'query\' is undefined.')
        }

        if (!query.maxResults) {
            // FIXME: Jira returns a maximum of 50 results per default.
            // Here, we set it to Jira's allowed maximum of 1000 results.
            // If > 1000 results are expected, a paged solution is needed.
            query.maxResults = 1000
        }

        def response = Unirest.post("${this.baseURL}/rest/api/2/search")
            .basicAuth(this.username, this.password)
            .header('Accept', 'application/json')
            .header('Content-Type', 'application/json')
            .body(JsonOutput.toJson(query))
            .asString()

        response.ifFailure {
            def message = "Error: unable to get Jira issues for JQL query. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 400) {
                def matcher = message =~ /The value '(.*)' does not exist for the field 'project'./
                if (matcher.find()) {
                    def project = matcher[0][1]
                    message += " Could it be that the project '${project}' does not exist in Jira?"
                }
            } else if (response.getStatus() == 404) {
                message = "Error: unable to get Jira issues for JQL query. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(StringCleanup.removeCharacters(response.getBody(), CHARACTER_REMOVEABLE))
    }

    @NonCPS
    void updateReleaseStatusIssue(String projectKey, String version, Map fields) {
        logger.debug "-> Updating release status issue for  project ${projectKey}, version ${version} with fields ${fields}"

        if (!projectKey?.trim()) {
            throw new IllegalArgumentException('Error: Unable to update the release status issue: \'projectKey\' is undefined')
        }
        if (!version?.trim()) {
            throw new IllegalArgumentException('Error: Unable to update the release status issue: \'version\' is undefined')
        }
        if (!fields) {
            throw new IllegalArgumentException('Error: Unable to update the release status issue: no data given for updating')
        }

        def response = Unirest.post("${this.baseURL}/rest/platform/1.1/productreleases/{projectKey}/{version}/status")
            .routeParam("projectKey", projectKey.toUpperCase())
            .routeParam("version", version)
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header('Content-Type', 'application/json')
            .body(JsonOutput.toJson(fields)).asString()

        response.ifFailure {
            def message = 'Error: unable to update release status issue. Jira responded with code: ' +
                "'${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = 'Error: unable to update release status issue. ' +
                    "Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }
    }

    @NonCPS
    void updateBuildNumber(String projectKey, String version, String buildNumber) {
        logger.debug "-> Updating build number for project ${projectKey}, version ${version} and buildNumber ${buildNumber}"

        if (!projectKey?.trim()) {
            throw new IllegalArgumentException('Error: Unable to update the build number: \'projectKey\' is undefined')
        }
        if (!version?.trim()) {
            throw new IllegalArgumentException('Error: Unable to update the build number: \'version\' is undefined')
        }
        if (!buildNumber?.trim()) {
            throw new IllegalArgumentException('Error: Unable to update the build number: \'buildNumber\' is undefined')
        }

        def response = Unirest.post("${this.baseURL}/rest/platform/1.1/productreleases/{projectKey}/{version}/buildnumber")
            .routeParam("projectKey", projectKey.toUpperCase())
            .routeParam("version", version)
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header('Content-Type', 'application/json')
            .body(JsonOutput.toJson( [buildNumber: buildNumber] )).asString()

        response.ifFailure {
            def message = 'Error: unable to update the build number. Jira responded with code: ' +
                "'${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = 'Error: unable to update the build number. ' +
                    "Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }
    }

    @NonCPS
    void updateSelectListFieldsOnIssue(String issueIdOrKey, Map fields) {
        if (!issueIdOrKey?.trim()) {
            throw new IllegalArgumentException('Error: unable to update select list fields on Jira issue. \'issueIdOrKey\' is undefined.')
        }

        if (!fields) {
            throw new IllegalArgumentException('Error: unable to update select list fields on Jira issue. \'fields\' is undefined.')
        }

        def response = Unirest.put("${this.baseURL}/rest/api/2/issue/{issueIdOrKey}")
            .routeParam('issueIdOrKey', issueIdOrKey)
            .basicAuth(this.username, this.password)
            .header('Accept', 'application/json')
            .header('Content-Type', 'application/json')
            .body(JsonOutput.toJson(
                [
                    update: fields.collectEntries { id, value_ ->
                        [
                            id,
                            [
                                [
                                    set: [
                                        value: value_ as String
                                    ]
                                ]
                            ]
                        ]
                    }
                ]
            ))
            .asString()

        response.ifSuccess {
            if (response.getStatus() != 204) {
                throw new RuntimeException("Error: unable to update select list fields on Jira issue ${issueIdOrKey}. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'.")
            }
        }

        response.ifFailure {
            def message = "Error: unable to update select list fields on Jira issue ${issueIdOrKey}. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to update select list fields on Jira issue ${issueIdOrKey}. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }
    }

    @NonCPS
    void updateTextFieldsOnIssue(String issueIdOrKey, Map fields) {
        if (!issueIdOrKey?.trim()) {
            throw new IllegalArgumentException('Error: unable to update text fields on Jira issue. \'issueIdOrKey\' is undefined.')
        }

        if (!fields) {
            throw new IllegalArgumentException('Error: unable to update text fields on Jira issue. \'fields\' is undefined.')
        }

        def response = Unirest.put("${this.baseURL}/rest/api/2/issue/{issueIdOrKey}")
            .routeParam('issueIdOrKey', issueIdOrKey)
            .basicAuth(this.username, this.password)
            .header('Accept', 'application/json')
            .header('Content-Type', 'application/json')
            .body(JsonOutput.toJson(
                [
                    update: fields.collectEntries { id, value_ ->
                        [
                            id,
                            [
                                [
                                    set: value_ as String
                                ]
                            ]
                        ]
                    }
                ]
            ))
            .asString()

        response.ifSuccess {
            if (response.getStatus() != 204) {
                throw new RuntimeException("Error: unable to update text fields on Jira issue ${issueIdOrKey}. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'.")
            }
        }

        response.ifFailure {
            def message = "Error: unable to update text fields on Jira issue ${issueIdOrKey}. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to update text fields on Jira issue ${issueIdOrKey}. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }
    }

    @NonCPS
    Map<String, Object> getTextFieldsOfIssue(String issueIdOrKey, List fields) {
        if (!issueIdOrKey?.trim()) {
            throw new IllegalArgumentException('Error: unable to retrieve text fields on Jira issue. \'issueIdOrKey\' is undefined.')
        }

        if (!fields) {
            throw new IllegalArgumentException('Error: unable to retrieve text fields on Jira issue. \'fields\' is undefined.')
        }

        def response = Unirest.get("${this.baseURL}/rest/api/2/issue/{issueIdOrKey}")
            .routeParam('issueIdOrKey', issueIdOrKey)
            .queryString('fields', fields.join(','))
            .basicAuth(this.username, this.password)
            .header('Accept', 'application/json')
            .header('Content-Type', 'application/json')
            .asString()

        response.ifFailure {
            def message = "Error: unable to retrieve text fields (${fields}) on Jira issue ${issueIdOrKey}. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to retrieve text fields (${fields}) on Jira issue ${issueIdOrKey}. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        new JsonSlurperClassic().parseText(response.getBody()).getOrDefault("fields", [:])
    }

    @NonCPS
    Boolean isVersionEnabledForDelta(String projectKey, String versionName) {
        if (!projectKey?.trim()) {
            throw new IllegalArgumentException('Error: unable to check project versions from Jira. ' +
                '\'projectKey\' is undefined.')
        }

        if (!versionName?.trim()) {
            throw new IllegalArgumentException('Error: unable to check project versions from Jira. ' +
                '\'versionName\' is undefined.')
        }

        def response = Unirest.get("${this.baseURL}/rest/platform/1.1/productreleases/{projectKey}/versions/{version}")
            .routeParam('projectKey', projectKey.toUpperCase())
            .routeParam('version', versionName)
            .basicAuth(this.username, this.password)
            .header('Accept', 'application/json')
            .asString()

        response.ifFailure {
            if (response.getStatus() == 400) {
                if (response.getBody().contains("Invalid project versionName.")) {
                    return false
                }
            }
            def message = 'Error: unable to get project versions in url ' +
                "${this.baseURL}/rest/platform/1.1/productreleases/${projectKey.toUpperCase()}/versions/${versionName}" +
                ' Jira responded with code: ' +
                "'${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to get project versions. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }
        return true
    }

    @NonCPS
    Map getComponents(String projectKey, String version, boolean isWorkInProgress) {
        if (!projectKey?.strip()) {
            throw new IllegalArgumentException('Error: unable to check component mismatch from Jira. ' +
                '\'projectKey\' is undefined.')
        }
        if (!version?.strip()) {
            throw new IllegalArgumentException('Error: unable to check component mismatch from Jira. ' +
                '\'version\' is undefined.')
        }

        def request = Unirest.get(this.baseURL.toString() + '/rest/platform/1.1/projects/{projectKey}/components')
            .routeParam('projectKey', projectKey.toUpperCase(Locale.ENGLISH))
            .queryString('changeId', version)
            .queryString('previewMode', String.valueOf(isWorkInProgress))
            .basicAuth(this.username, this.password)
            .header('Accept', 'application/json')
        def response = request.asString()

        response.ifFailure {
            def message = "Error: unable to get component match check in url ${request.url} " +
                "Jira responded with code: '${response.status}' and message: '${response.body}'."

            if (response.status == 404) {
                message = "Error: unable to get component match check. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }
        return new JsonSlurperClassic().parseText(response.body)
    }

    def getIssue(String issueId, String expand = null) {
        if (!issueId?.trim()) {
            throw new IllegalArgumentException("ERROR: unable to get issue transitions from Jira. 'issueId' is undefined.")
        }
        def url = "${this.baseURL}/rest/api/2/issue/${issueId}"
        if (expand) {
            url += "?expand=${expand}"
        }
        def response =
            Unirest.get(url)
                .basicAuth(this.username, this.password)
                .header("Accept", "application/json")
                .asString()
        response.ifFailure {
            def message = "ERROR: unable to get issue with transitions from Jira. " +
                "Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "ERROR: unable to get issue with transitions from Jira. " +
                    "Jira could not be found at: ${this.getBaseURL()}"
            }

            throw new RuntimeException(message)
        }
        return new JsonSlurperClassic().parseText(response.getBody())
    }

    def doTransition(issueId, transition) {
        if (!issueId?.trim()) {
            throw new IllegalArgumentException("ERROR: unable to transition issue. 'issueId' is undefined.")
        }
        if (!transition || !transition.id?.trim()) {
            throw new IllegalArgumentException("ERROR: unable to transition issue. Transition id is undefined.")
        }

        def response =
            Unirest.post("${this.baseURL}/rest/api/2/issue/${issueId}/transitions")
                .basicAuth(this.username, this.password)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .body(JsonOutput.toJson(
                    [transition: ["id": transition.id]]
                ))
                .asString()
        response.ifFailure {
            def message = "ERROR: unable to issue transitions from Jira. " +
                "Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "ERROR: unable to issue transitions from Jira. Jira could not be found at: ${this.getBaseURL()}"
            }

            throw new RuntimeException(message)
        }
        return true
    }

}
