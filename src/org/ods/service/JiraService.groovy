package org.ods.service

@Grab(group="com.konghq", module="unirest-java", version="2.3.08", classifier="standalone")

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

import java.net.URI

import kong.unirest.Unirest

import org.apache.http.client.utils.URIBuilder

class JiraService implements Serializable {

    String scheme
    String host
    int port

    String username
    String password

    private URI getBaseURI() {
        return new URIBuilder()
            .setScheme(this.scheme)
            .setHost(this.host)
            .setPort(this.port)
            .build()
    }


    def void appendCommentToIssue(String issueIdOrKey, String comment) {
        def response = Unirest.post("${getBaseURI()}/rest/api/2/issue/{issueIdOrKey}/comment")
            .routeParam("issueIdOrKey", issueIdOrKey)
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson(
                [ body: comment ]
            ))
            .asString()

        response.ifFailure {
            throw new RuntimeException("Error: unable to append comment to issue. Jira responded with code: ${response.getStatus()} and message: ${response.getBody()}")
        }
    }

    def List getIssuesForJQLQuery(String query) {
        def response = Unirest.get("${getBaseURI()}/rest/api/2/search?jql={query}")
            .routeParam("query", query)
            .header("Accept", "application/json")
            .basicAuth(this.username, this.password)
            .asString()

        response.ifFailure {
            throw new RuntimeException("Error: unable to get issues for JQL query. Jira responded with code: ${response.getStatus()} and message: ${response.getBody()}")
        }

        return new JsonSlurperClassic().parseText(response.getBody()).issues
    }
}
