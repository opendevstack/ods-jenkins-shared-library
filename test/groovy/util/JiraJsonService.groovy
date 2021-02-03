package util


import groovy.json.JsonOutput
import kong.unirest.Unirest
import org.apache.http.client.utils.URIBuilder

class JiraJsonService {

    URI baseURL

    String username
    String password

    JiraJsonService(String baseURL, String username, String password) {
        try {
            this.baseURL = new URIBuilder(baseURL).build()
        } catch (e) {
            throw new IllegalArgumentException("Error: unable to connect to Jira. '${baseURL}' is not a valid URI.").initCause(e)
        }

        this.username = username
        this.password = password
    }

    def createIssueType(String type, String projectKey, String summary, String description, String fixVersion = null) {
        def response = Unirest.post("${this.baseURL}/rest/api/2/issue")
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson(
                [
                    fields: [
                        project: [
                            key: projectKey.toUpperCase()
                        ],
                        summary: summary,
                        description: description,
                        fixVersions: [
                           [name: fixVersion]
                        ],
                        issuetype: [
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

        return response.getBody()
    }

    def getDocGenData(String projectKey) {
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

        return response.getBody()
    }

    def getDeltaDocGenData(String projectKey, String version) {
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

        return response.getBody()
    }

    def getFileFromJira(String url) {
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
            data: response.getBody()
        ]
    }

    def getIssueTypeMetadata(String projectKey, String issueTypeId) {
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

        return response.getBody()
    }

    def getIssueTypes(String projectKey) {
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

        return response.getBody()
    }

    def getVersionsForProject(String projectKey) {
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

        return response.getBody()
    }

    def getProject(String projectKey) {
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

        return response.getBody()
    }

    def getProjectVersions(String projectKey) {
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

        return response.getBody()
    }

    def getLabelsFromIssue(String issueIdOrKey) {
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

        return response.getBody()
    }


    def searchByJQLQuery(Map query) {
        query.maxResults = 1000
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

        return response.getBody()
    }

    def getTextFieldsOfIssue(String issueIdOrKey, List fields) {
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

        return response.getBody()
    }

    def isVersionEnabledForDelta(String projectKey, String versionName) {
        def response = Unirest.get("${this.baseURL}/rest/platform/1.1/productreleases/{projectKey}/versions/{version}")
            .routeParam('projectKey', projectKey.toUpperCase())
            .routeParam('version', versionName)
            .basicAuth(this.username, this.password)
            .header('Accept', 'application/json')
            .asString()

        return response.getBody()
    }
}
