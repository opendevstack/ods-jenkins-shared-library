package org.ods.service

import com.github.tomakehurst.wiremock.client.*

import groovy.json.JsonOutput

import org.apache.http.client.utils.URIBuilder

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class JiraServiceSpec extends SpecHelper {

    JiraService createService(int port, String username, String password) {
        return new JiraService("http://localhost:${port}", username, password)
    }

    def "create with invalid baseURL"() {
        when:
        new JiraService(null, "username", "password")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Jira. 'baseURL' is undefined."

        when:
        new JiraService(" ", "username", "password")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Jira. 'baseURL' is undefined."

        when:
        new JiraService("invalid URL", "username", "password")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Jira. 'invalid URL' is not a valid URI."
    }

    def "create with invalid username"() {
        when:
        new JiraService("http://localhost", null, "password")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Jira. 'username' is undefined."

        when:
        new JiraService("http://localhost", " ", "password")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Jira. 'username' is undefined."
    }

    def "create with invalid password"() {
        when:
        new JiraService("http://localhost", "username", null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Jira. 'password' is undefined."

        when:
        new JiraService("http://localhost", "username", " ")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Jira. 'password' is undefined."
    }


    Map addLabelsToIssueRequestData(Map mixins = [:]) {
        def result = [
            data: [
                issueIdOrKey: "JIRA-123",
                names: ["Label-A", "Label-B", "Label-C"]
            ],
            headers: [
                "Accept": "application/json",
                "Content-Type": "application/json"
            ],
            password: "password",
            username: "username"
        ]

        result.body = JsonOutput.toJson([
            update: [
                labels: result.data.names.collect { name ->
                    [ add: name ]
                }
            ]
        ])

        result.path = "/rest/api/2/issue/${result.data.issueIdOrKey}"

        return result << mixins
    }

    Map addLabelsToIssueResponseData(Map mixins = [:]) {
        def result = [
            status: 200
        ]

        return result << mixins
    }

    def "add labels to issue with invalid issueIdOrKey"() {
        given:
        def request = addLabelsToIssueRequestData()
        def response = addLabelsToIssueResponseData()

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.addLabelsToIssue(null, request.data.names)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to add labels to Jira issue. 'issueIdOrKey' is undefined."

        when:
        service.addLabelsToIssue(" ", request.data.names)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to add labels to Jira issue. 'issueIdOrKey' is undefined."
    }

    def "add labels to issue with invalid names"() {
        given:
        def request = addLabelsToIssueRequestData()
        def response = addLabelsToIssueResponseData()

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.addLabelsToIssue(request.data.issueIdOrKey, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to add labels to Jira issue. 'names' is undefined."

        when:
        service.addLabelsToIssue(request.data.issueIdOrKey, [])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to add labels to Jira issue. 'names' is undefined."
    }

    def "add labels to issue"() {
        given:
        def request = addLabelsToIssueRequestData()
        def response = addLabelsToIssueResponseData()

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.addLabelsToIssue(request.data.issueIdOrKey, request.data.names)

        then:
        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "add labels to issue with HTTP 404 failure"() {
        given:
        def request = addLabelsToIssueRequestData()
        def response = addLabelsToIssueResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.addLabelsToIssue(request.data.issueIdOrKey, request.data.names)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to add labels to Jira issue. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "add labels to issue with HTTP 500 failure"() {
        given:
        def request = addLabelsToIssueRequestData()
        def response = addLabelsToIssueResponseData([
            body: "Sorry, doesn't work!",
            status: 500
        ])

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.addLabelsToIssue(request.data.issueIdOrKey, request.data.names)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to add labels to Jira issue. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }


    Map appendCommentToIssueRequestData(Map mixins = [:]) {
        def result = [
            data: [
                comment: "myComment",
                issueIdOrKey: "JIRA-123",
            ],
            headers: [
                "Accept": "application/json",
                "Content-Type": "application/json"
            ],
            password: "password",
            username: "username"
        ]

        result.body = JsonOutput.toJson([
            body: result.data.comment
        ])

        result.path = "/rest/api/2/issue/${result.data.issueIdOrKey}/comment"

        return result << mixins
    }

    Map appendCommentToIssueResponseData(Map mixins = [:]) {
        def result = [
            status: 200
        ]

        return result << mixins
    }

    def "append comment to issue with invalid issueIdOrKey"() {
        given:
        def request = appendCommentToIssueRequestData()
        def response = appendCommentToIssueResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.appendCommentToIssue(null, request.data.comment)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to append comment to Jira issue. 'issueIdOrKey' is undefined."

        when:
        service.appendCommentToIssue(" ", request.data.comment)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to append comment to Jira issue. 'issueIdOrKey' is undefined."
    }

    def "append comment to issue with invalid comment"() {
        given:
        def request = appendCommentToIssueRequestData()
        def response = appendCommentToIssueResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.appendCommentToIssue(request.data.issueIdOrKey, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to append comment to Jira issue. 'comment' is undefined."

        when:
        service.appendCommentToIssue(request.data.issueIdOrKey, " ")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to append comment to Jira issue. 'comment' is undefined."
    }

    def "append comment to issue"() {
        given:
        def request = appendCommentToIssueRequestData()
        def response = appendCommentToIssueResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.appendCommentToIssue(request.data.issueIdOrKey, request.data.comment)

        then:
        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "append comment to issue with HTTP 404 failure"() {
        given:
        def request = appendCommentToIssueRequestData()
        def response = appendCommentToIssueResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.appendCommentToIssue(request.data.issueIdOrKey, request.data.comment)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to append comment to Jira issue. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "append comment to issue with HTTP 500 failure"() {
        given:
        def request = appendCommentToIssueRequestData()
        def response = appendCommentToIssueResponseData([
            body: "Sorry, doesn't work!",
            status: 500
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.appendCommentToIssue(request.data.issueIdOrKey, request.data.comment)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to append comment to Jira issue. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }


    Map createIssueLinkTypeRequestData(Map mixins = [:]) {
        def result = [
            data: [
                linkType: "myLinkType",
                inwardIssue: [
                    key: "1"
                ],
                outwardIssue: [
                    key: "2"
                ]
            ],
            headers: [
                "Accept": "application/json",
                "Content-Type": "application/json"
            ],
            password: "password",
            path: "/rest/api/2/issueLink",
            username: "username"
        ]

        result.body = JsonOutput.toJson([
            type: [
                name: result.data.linkType
            ],
            inwardIssue: [
                key: result.data.inwardIssue.key
            ],
            outwardIssue: [
                key: result.data.outwardIssue.key
            ]
        ])

        return result << mixins
    }

    Map createIssueLinkTypeResponseData(Map mixins = [:]) {
        def result = [
            status: 201
        ]

        return result << mixins
    }

    def "create issue link type with invalid linkType"() {
        given:
        def request = createIssueLinkTypeRequestData()
        def response = createIssueLinkTypeResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.createIssueLinkType(null, request.data.inwardIssue, request.data.outwardIssue)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Jira issue link. 'linkType' is undefined."

        when:
        service.createIssueLinkType(" ", request.data.inwardIssue, request.data.outwardIssue)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Jira issue link. 'linkType' is undefined."
    }

    def "create issue link type with invalid inwardIssue"() {
        given:
        def request = createIssueLinkTypeRequestData()
        def response = createIssueLinkTypeResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.createIssueLinkType(request.data.linkType, null, request.data.outwardIssue)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Jira issue link. 'inwardIssue' is undefined."

        when:
        service.createIssueLinkType(request.data.linkType, [:], request.data.outwardIssue)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Jira issue link. 'inwardIssue' is undefined."
    }

    def "create issue link type with invalid outwardIssue"() {
        given:
        def request = createIssueLinkTypeRequestData()
        def response = createIssueLinkTypeResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.createIssueLinkType(request.data.linkType, request.data.inwardIssue, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Jira issue link. 'outwardIssue' is undefined."

        when:
        service.createIssueLinkType(request.data.linkType, request.data.inwardIssue, [:])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Jira issue link. 'outwardIssue' is undefined."
    }

    def "create issue link type"() {
        given:
        def request = createIssueLinkTypeRequestData()
        def response = createIssueLinkTypeResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.createIssueLinkType(request.data.linkType, request.data.inwardIssue, request.data.outwardIssue)

        then:
        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "create issue link type with HTTP 404 failure"() {
        given:
        def request = createIssueLinkTypeRequestData()
        def response = createIssueLinkTypeResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.createIssueLinkType(request.data.linkType, request.data.inwardIssue, request.data.outwardIssue)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to create Jira issue link. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "create issue link type with HTTP 500 failure"() {
        given:
        def request = createIssueLinkTypeRequestData()
        def response = createIssueLinkTypeResponseData([
            body: "Sorry, doesn't work!",
            status: 500
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.createIssueLinkType(request.data.linkType, request.data.inwardIssue, request.data.outwardIssue)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to create Jira issue link. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }


    Map createIssueLinkTypeBlocksRequestData(Map mixins = [:]) {
        def result = [
            data: [
                inwardIssue: [
                    key: "1"
                ],
                outwardIssue: [
                    key: "2"
                ]
            ],
            headers: [
                "Accept": "application/json",
                "Content-Type": "application/json"
            ],
            password: "password",
            path: "/rest/api/2/issueLink",
            username: "username"
        ]

        result.body = JsonOutput.toJson([
            type: [
                name: "Blocks"
            ],
            inwardIssue: [
                key: result.data.inwardIssue.key
            ],
            outwardIssue: [
                key: result.data.outwardIssue.key
            ]
        ])

        return result << mixins
    }

    Map createIssueLinkTypeBlocksResponseData(Map mixins = [:]) {
        def result = [
            status: 201
        ]

        return result << mixins
    }

    def "create issue link type 'Blocks'"() {
        given:
        def request = createIssueLinkTypeBlocksRequestData()
        def response = createIssueLinkTypeBlocksResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.createIssueLinkTypeBlocks(request.data.inwardIssue, request.data.outwardIssue)

        then:
        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "create issue link type 'Blocks' with HTTP 404 failure"() {
        given:
        def request = createIssueLinkTypeBlocksRequestData()
        def response = createIssueLinkTypeBlocksResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.createIssueLinkTypeBlocks(request.data.inwardIssue, request.data.outwardIssue)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to create Jira issue link. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "create issue link type 'Blocks' with HTTP 500 failure"() {
        given:
        def request = createIssueLinkTypeBlocksRequestData()
        def response = createIssueLinkTypeBlocksResponseData([
            body: "Sorry, doesn't work!",
            status: 500
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.createIssueLinkTypeBlocks(request.data.inwardIssue, request.data.outwardIssue)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to create Jira issue link. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }


    Map createIssueTypeRequestData(Map mixins = [:]) {
        def result = [
            data: [
                description: "myDescription",
                projectKey: "PROJECT-1",
                summary: "mySummary",
                type: "myType"
            ],
            headers: [
                "Accept": "application/json",
                "Content-Type": "application/json"
            ],
            password: "password",
            path: "/rest/api/2/issue",
            username: "username"
        ]

        result.body = JsonOutput.toJson([
            fields: [
                project: [
                    key: result.data.projectKey
                ],
                summary: result.data.summary,
                description: result.data.description,
                issuetype: [
                    name: result.data.type
                ]
            ]
        ])

        return result << mixins
    }

    Map createIssueTypeResponseData(Map mixins = [:]) {
        def result = [
            status: 201
        ]

        return result << mixins
    }

    def "create issue type with invalid type"() {
        given:
        def request = createIssueTypeRequestData()
        def response = createIssueTypeResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.createIssueType(null, request.data.projectKey, request.data.summary, request.data.description)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Jira issue. 'type' is undefined."

        when:
        service.createIssueType(" ", request.data.projectKey, request.data.summary, request.data.description)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Jira issue. 'type' is undefined."
    }

    def "create issue type with invalid projectKey"() {
        given:
        def request = createIssueTypeRequestData()
        def response = createIssueTypeResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.createIssueType(request.data.type, null, request.data.summary, request.data.description)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Jira issue. 'projectKey' is undefined."

        when:
        service.createIssueType(request.data.type, " ", request.data.summary, request.data.description)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Jira issue. 'projectKey' is undefined."
    }

    def "create issue type with invalid summary"() {
        given:
        def request = createIssueTypeRequestData()
        def response = createIssueTypeResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.createIssueType(request.data.type, request.data.projectKey, null, request.data.description)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Jira issue. 'summary' is undefined."

        when:
        service.createIssueType(request.data.type, request.data.projectKey, " ", request.data.description)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Jira issue. 'summary' is undefined."
    }

    def "create issue type with invalid description"() {
        given:
        def request = createIssueTypeRequestData()
        def response = createIssueTypeResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.createIssueType(request.data.type, request.data.projectKey, request.data.summary, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Jira issue. 'description' is undefined."

        when:
        service.createIssueType(request.data.type, request.data.projectKey, request.data.summary, " ")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Jira issue. 'description' is undefined."
    }

    def "create issue type"() {
        given:
        def request = createIssueTypeRequestData()
        def response = createIssueTypeResponseData([
            body: JsonOutput.toJson([
                "JIRA-123": request.data.summary
            ])
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.createIssueType(request.data.type, request.data.projectKey, request.data.summary, request.data.description)

        then:
        result == [ "JIRA-123": request.data.summary ]

        cleanup:
        stopServer(server)
    }

    def "create issue type with HTTP 404 failure"() {
        given:
        def request = createIssueTypeRequestData()
        def response = createIssueTypeResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.createIssueType(request.data.type, request.data.projectKey, request.data.summary, request.data.description)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to create Jira issue. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "create issue type with HTTP 500 failure"() {
        given:
        def request = createIssueTypeRequestData()
        def response = createIssueTypeResponseData([
            body: "Sorry, doesn't work!",
            status: 500
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.createIssueType(request.data.type, request.data.projectKey, request.data.summary, request.data.description)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to create Jira issue. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }


    Map createIssueTypeBugRequestData(Map mixins = [:]) {
        def result = [
            data: [
                description: "myDescription",
                projectKey: "PROJECT-1",
                summary: "mySummary",
            ],
            headers: [
                "Accept": "application/json",
                "Content-Type": "application/json"
            ],
            password: "password",
            path: "/rest/api/2/issue",
            username: "username"
        ]

        result.body = JsonOutput.toJson([
            fields: [
                project: [
                    key: result.data.projectKey
                ],
                summary: result.data.summary,
                description: result.data.description,
                issuetype: [
                    name: "Bug"
                ]
            ]
        ])

        return result << mixins
    }

    Map createIssueTypeBugResponseData(Map mixins = [:]) {
        def result = [
            status: 201
        ]

        return result << mixins
    }

    def "create issue type 'Bug'"() {
        given:
        def request = createIssueTypeBugRequestData()
        def response = createIssueTypeBugResponseData([
            body: JsonOutput.toJson([
                "JIRA-123": request.data.summary
            ])
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.createIssueTypeBug(request.data.projectKey, request.data.summary, request.data.description)

        then:
        result == [ "JIRA-123": request.data.summary ]

        cleanup:
        stopServer(server)
    }

    def "create issue type 'Bug' with HTTP 404 failure"() {
        given:
        def request = createIssueTypeBugRequestData()
        def response = createIssueTypeBugResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.createIssueTypeBug(request.data.projectKey, request.data.summary, request.data.description)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to create Jira issue. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "create issue type 'Bug' with HTTP 500 failure"() {
        given:
        def request = createIssueTypeBugRequestData()
        def response = createIssueTypeBugResponseData([
            body: "Sorry, doesn't work!",
            status: 500
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.createIssueTypeBug(request.data.projectKey, request.data.summary, request.data.description)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to create Jira issue. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }


    Map getIssuesForJQLQueryRequestData(Map mixins = [:]) {
        def result = [
            data: [
                query: "I want it all!"
            ],
            headers: [
                "Accept": "application/json"
            ],
            password: "password",
            path: "/rest/api/2/search",
            username: "username"
        ]

        result.queryParams = [
            "jql": result.data.query
        ]

        return result << mixins
    }

    Map getIssuesForJQLQueryResponseData(Map mixins = [:]) {
        def result = [
            body: JsonOutput.toJson([
                issues: ["JIRA-123", "JIRA-456", "JIRA-789"]
            ]),
            status: 200
        ]

        return result << mixins
    }

    def "get issues for JQL query with invalid query"() {
        given:
        def request = getIssuesForJQLQueryRequestData()
        def response = getIssuesForJQLQueryResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.getIssuesForJQLQuery(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Jira issues for JQL query. 'query' is undefined."

        when:
        service.getIssuesForJQLQuery(" ")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Jira issues for JQL query. 'query' is undefined."
    }

    def "get issues for JQL query"() {
        given:
        def request = getIssuesForJQLQueryRequestData()
        def response = getIssuesForJQLQueryResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getIssuesForJQLQuery(request.data.query)

        then:
        result == ["JIRA-123", "JIRA-456", "JIRA-789"]

        cleanup:
        stopServer(server)
    }

    def "get issues for JQL query with HTTP 400 failure"() {
        given:
        def request = getIssuesForJQLQueryRequestData()
        def response = getIssuesForJQLQueryResponseData([
            body: "Sorry, doesn't work!",
            status: 400
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.getIssuesForJQLQuery(request.data.query)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get Jira issues for JQL query. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }

    def "get issues for JQL query with HTTP 400 failure because of non-existent project"() {
        given:
        def request = getIssuesForJQLQueryRequestData()
        def response = getIssuesForJQLQueryResponseData([
            body: """{"errorMessages":["The value 'myProject' does not exist for the field 'project'."],"errors":{}""",
            status: 400
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.getIssuesForJQLQuery(request.data.query)

        then:
        def e = thrown(RuntimeException)
        e.message == """Error: unable to get Jira issues for JQL query. Jira responded with code: '${response.status}' and message: '${response.body}'. Could it be that the project 'myProject' does not exist in Jira?"""

        cleanup:
        stopServer(server)
    }

    def "get issues for JQL query with HTTP 404 failure"() {
        given:
        def request = getIssuesForJQLQueryRequestData()
        def response = getIssuesForJQLQueryResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.getIssuesForJQLQuery(request.data.query)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get Jira issues for JQL query. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "get issues for JQL query with HTTP 500 failure"() {
        given:
        def request = getIssuesForJQLQueryRequestData()
        def response = getIssuesForJQLQueryResponseData([
            body: "Sorry, doesn't work!",
            status: 500
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.getIssuesForJQLQuery(request.data.query)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get Jira issues for JQL query. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }


    Map removeLabelsFromIssueRequestData(Map mixins = [:]) {
        def result = [
            data: [
                issueIdOrKey: "JIRA-123",
                names: ["Label-A", "Label-B", "Label-C"]
            ],
            headers: [
                "Accept": "application/json",
                "Content-Type": "application/json"
            ],
            password: "password",
            username: "username"
        ]

        result.body = JsonOutput.toJson([
            update: [
                labels: result.data.names.collect { name ->
                    [ remove: name ]
                }
            ]
        ])

        result.path = "/rest/api/2/issue/${result.data.issueIdOrKey}"

        return result << mixins
    }

    Map removeLabelsFromIssueResponseData(Map mixins = [:]) {
        def result = [
            status: 200
        ]

        return result << mixins
    }

    def "remove labels from issue with invalid issueIdOrKey"() {
        given:
        def request = removeLabelsFromIssueRequestData()
        def response = removeLabelsFromIssueResponseData()

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.removeLabelsFromIssue(null, request.data.names)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to remove labels from Jira issue. 'issueIdOrKey' is undefined."

        when:
        service.removeLabelsFromIssue(" ", request.data.names)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to remove labels from Jira issue. 'issueIdOrKey' is undefined."
    }

    def "remove labels from issue with invalid names"() {
        given:
        def request = removeLabelsFromIssueRequestData()
        def response = removeLabelsFromIssueResponseData()

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.removeLabelsFromIssue(request.data.issueIdOrKey, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to remove labels from Jira issue. 'names' is undefined."

        when:
        service.removeLabelsFromIssue(request.data.issueIdOrKey, [])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to remove labels from Jira issue. 'names' is undefined."
    }

    def "remove labels from issue"() {
        given:
        def request = removeLabelsFromIssueRequestData()
        def response = removeLabelsFromIssueResponseData()

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.removeLabelsFromIssue(request.data.issueIdOrKey, request.data.names)

        then:
        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "remove labels from issue with HTTP 404 failure"() {
        given:
        def request = removeLabelsFromIssueRequestData()
        def response = removeLabelsFromIssueResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.removeLabelsFromIssue(request.data.issueIdOrKey, request.data.names)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to remove labels from Jira issue. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "remove labels from issue with HTTP 500 failure"() {
        given:
        def request = removeLabelsFromIssueRequestData()
        def response = removeLabelsFromIssueResponseData([
            body: "Sorry, doesn't work!",
            status: 500
        ])

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.removeLabelsFromIssue(request.data.issueIdOrKey, request.data.names)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to remove labels from Jira issue. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }

    def "Helper.toSimpleIssuesFormat"() {
        given:
        def issues = createJiraIssues(false)

        when:
        def result = JiraService.Helper.toSimpleIssuesFormat(issues)

        then:
        def issue0815 = [
            id: "0815",
            key: "JIRA-0815",
            summary: "0815-summary",
            description: "0815-description",
            url: "http://0815"
        ]

        def issue4711 = [
            id: "4711",
            key: "JIRA-4711",
            summary: "4711-summary",
            description: "4711-description",
            url: "http://4711"
        ]

        def issue123 = [
            id: "123",
            key: "JIRA-123",
            summary: "123-summary",
            description: "123-description",
            parent: issue0815,
            url: "http://123"
        ]

        def issue456 = [
            id: "456",
            key: "JIRA-456",
            summary: "456-summary",
            description: "456-description",
            parent: issue0815,
            url: "http://456"
        ]

        def issue789 = [
            id: "789",
            key: "JIRA-789",
            summary: "789-summary",
            description: "789-description",
            parent: issue4711,
            url: "http://789"
        ]

        def expected = [
            "0815": issue0815,
            "4711": issue4711,
            "123": issue123,
            "456": issue456,
            "789": issue789
        ]

        result == expected
    }
}
