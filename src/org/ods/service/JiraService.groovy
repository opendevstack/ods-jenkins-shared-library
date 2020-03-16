package org.ods.service

@Grab(group="com.konghq", module="unirest-java", version="2.4.03", classifier="standalone")

import com.cloudbees.groovy.cps.NonCPS

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

import java.net.URI

import kong.unirest.Unirest

import org.apache.http.client.utils.URIBuilder

class JiraService {

    URI baseURL

    String username
    String password

    JiraService(String baseURL, String username, String password) {
        if (!baseURL?.trim()) {
            throw new IllegalArgumentException("Error: unable to connect to Jira. 'baseURL' is undefined.")
        }

        if (!username?.trim()) {
            throw new IllegalArgumentException("Error: unable to connect to Jira. 'username' is undefined.")
        }

        if (!password?.trim()) {
            throw new IllegalArgumentException("Error: unable to connect to Jira. 'password' is undefined.")
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
            throw new IllegalArgumentException("Error: unable to add labels to Jira issue. 'issueIdOrKey' is undefined.")
        }

        if (!names) {
            throw new IllegalArgumentException("Error: unable to add labels to Jira issue. 'names' is undefined.")
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
    void appendCommentToIssue(String issueIdOrKey, String comment) {
        if (!issueIdOrKey?.trim()) {
            throw new IllegalArgumentException("Error: unable to append comment to Jira issue. 'issueIdOrKey' is undefined.")
        }

        if (!comment?.trim()) {
            throw new IllegalArgumentException("Error: unable to append comment to Jira issue. 'comment' is undefined.")
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
            throw new IllegalArgumentException("Error: unable to create Jira issue link. 'linkType' is undefined.")
        }

        if (!inwardIssue) {
            throw new IllegalArgumentException("Error: unable to create Jira issue link. 'inwardIssue' is undefined.")
        }

        if (!outwardIssue) {
            throw new IllegalArgumentException("Error: unable to create Jira issue link. 'outwardIssue' is undefined.")
        }

        def response = Unirest.post("${this.baseURL}/rest/api/2/issueLink")
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson(
                [
                    type        : [
                        name: linkType
                    ],
                    inwardIssue : [
                        key: inwardIssue.key
                    ],
                    outwardIssue: [
                        key: outwardIssue.key
                    ]
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
    Map createIssueType(String type, String projectKey, String summary, String description) {
        if (!type?.trim()) {
            throw new IllegalArgumentException("Error: unable to create Jira issue. 'type' is undefined.")
        }

        if (!projectKey?.trim()) {
            throw new IllegalArgumentException("Error: unable to create Jira issue. 'projectKey' is undefined.")
        }

        if (!summary?.trim()) {
            throw new IllegalArgumentException("Error: unable to create Jira issue. 'summary' is undefined.")
        }

        if (!description?.trim()) {
            throw new IllegalArgumentException("Error: unable to create Jira issue. 'description' is undefined.")
        }

        def response = Unirest.post("${this.baseURL}/rest/api/2/issue")
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson(
                [
                    fields: [
                        project    : [
                            key: projectKey.toUpperCase()
                        ],
                        summary    : summary,
                        description: description,
                        issuetype  : [
                            name: type
                        ]
                    ]
                ]
            ))
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

    Map createIssueTypeBug(String projectKey, String summary, String description) {
        return createIssueType("Bug", projectKey, summary, description)
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
            data       : response.getBody()
        ]
    }

    @NonCPS
    List getIssuesForJQLQuery(Map query) {
        return searchByJQLQuery(query).issues
    }

    @NonCPS
    Map getVersionForProject(String projectKey) {
        if (!projectKey?.trim()) {
            throw new IllegalArgumentException("Error: unable to load project version from Jira. 'projectKey' is undefined.")
        }

        def response = Unirest.get("${this.baseURL}/rest/api/2/project/{projectKey}/versions")
            .routeParam("projectKey", projectKey.toUpperCase())
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .asString()

        response.ifFailure {
            def message = "Error: unable to get project version for projectKey ${projectKey}. Jira responded with code: '${response.getStatus()}' and message '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to get project version. Jira could not resolve the URL '${this.baseURL}/rest/api/2/project/${projectKey}/versions'."
            }

            throw new RuntimeException(message)
        }

        Map jiraVersion = new JsonSlurperClassic().parseText(response.getBody()).last()
        return [
            id  : jiraVersion.id,
            name: jiraVersion.name
        ]
    }

    @NonCPS
    Map getProject(String projectKey) {
        if (!projectKey?.trim()) {
            throw new IllegalArgumentException("Error: unable to get project from Jira. 'projectKey' is undefined.")
        }

        def response = Unirest.get("${this.baseURL}/rest/api/2/project/{projectKey}")
            .routeParam("projectKey", projectKey.toUpperCase())
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
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
            throw new IllegalArgumentException("Error: unable to get project versions from Jira. 'projectKey' is undefined.")
        }

        def response = Unirest.get("${this.baseURL}/rest/api/2/project/{projectKey}/versions")
            .routeParam("projectKey", projectKey.toUpperCase())
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
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
    void removeLabelsFromIssue(String issueIdOrKey, List names) {
        if (!issueIdOrKey?.trim()) {
            throw new IllegalArgumentException("Error: unable to remove labels from Jira issue. 'issueIdOrKey' is undefined.")
        }

        if (!names) {
            throw new IllegalArgumentException("Error: unable to remove labels from Jira issue. 'names' is undefined.")
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
            throw new IllegalArgumentException("Error: unable to get Jira issues for JQL query. 'query' is undefined.")
        }

        if (!query.maxResults) {
            // FIXME: Jira returns a maximum of 50 results per default.
            // Here, we set it to Jira's allowed maximum of 1000 results.
            // If > 1000 results are expected, a paged solution is needed.
            query.maxResults = 1000
        }

        def response = Unirest.post("${this.baseURL}/rest/api/2/search")
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
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

        return new JsonSlurperClassic().parseText(response.getBody())
    }
}
