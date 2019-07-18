package org.ods.service

@Grab(group="com.konghq", module="unirest-java", version="2.3.08", classifier="standalone")

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
        if (!baseURL) {
            throw new IllegalArgumentException("Error: unable to connect to Jira. 'baseURL' is undefined")
        }

        if (!username) {
            throw new IllegalArgumentException("Error: unable to connect to Jira. 'username' is undefined")
        }

        if (!password) {
            throw new IllegalArgumentException("Error: unable to connect to Jira. 'password' is undefined")
        }

        try {
            this.baseURL = new URIBuilder(baseURL).build()
        } catch (e) {
            throw new IllegalArgumentException("Error: unable to connect to Jira. '${baseURL}' is not a valid URI")
        }

        this.username = username
        this.password = password
    }

    def void appendCommentToIssue(String issueIdOrKey, String comment) {
        if (!issueIdOrKey) {
            throw new IllegalArgumentException("Error: unable to append comment to Jira issue. 'issueIdOrKey' is undefined")
        }

        if (!comment) {
            throw new IllegalArgumentException("Error: unable to append comment to Jira issue. 'comment' is undefined")
        }

        def response = Unirest.post("${this.baseURL}/rest/api/2/issue/{issueIdOrKey}/comment")
            .routeParam("issueIdOrKey", issueIdOrKey)
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson(
                [ body: comment ]
            ))
            .asString()

        response.ifFailure {
            throw new RuntimeException("Error: unable to append comment to Jira issue. Jira responded with code: ${response.getStatus()} and message: ${response.getBody()}")
        }
    }

    def List getIssuesForJQLQuery(String query) {
        if (!query) {
            throw new IllegalArgumentException("Error: unable to get Jira issues for JQL query. 'query' is undefined")
        }

        def response = Unirest.get("${this.baseURL}/rest/api/2/search?jql={query}")
            .routeParam("query", query)
            .header("Accept", "application/json")
            .basicAuth(this.username, this.password)
            .asString()

        response.ifFailure {
            throw new RuntimeException("Error: unable to get Jira issues for JQL query. Jira responded with code: ${response.getStatus()} and message: ${response.getBody()}")
        }

        return new JsonSlurperClassic().parseText(response.getBody()).issues
    }

    class Helper {
        @NonCPS
        /*
         * Transforms the parser's result into a (unique) set of failures.
         * Annotates each failure with indications on affected testsuite and -case.
         */
        static Map toSimpleIssuesFormat(List issues) {
            /*
             * Produces the following format:
             *
             * [
             *     "4711": [ // Issue
             *         id: 4711,
             *         key: _,
             *         summary: _,
             *         description: _,
             *         url: _,
             *         parent: [
             *             id: _,
             *             key: _,
             *             summary: _,
             *             description: _,
             *             url: _
             *         ]
             *     ],
             *     ...
             * ]
             */

            // Compute parents
            def parents = [:]
            issues.each { issue ->
                if (issue.fields.parent) {
                    def entry = [
                        id: issue.fields.parent.id,
                        key: issue.fields.parent.key,
                        summary: issue.fields.parent.fields.summary,
                        description: issue.fields.parent.fields.description,
                        url: issue.fields.parent.self
                    ]

                    parents << [ (issue.fields.parent.id): entry ]
                }
            }

            def result = [:]
            issues.each { issue ->
                def entry = [
                    id: issue.id,
                    key: issue.key,
                    summary: issue.fields.summary,
                    description: issue.fields.description,
                    url: issue.self
                ]

                // Attach a reference to the parent
                if (issue.fields.parent) {
                    entry.parent = parents[issue.fields.parent.id]
                }

                result << [ (issue.id): entry ]
            }

            return result
        }
    }
}
