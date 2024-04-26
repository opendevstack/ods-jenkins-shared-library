package org.ods.orchestration.service

import com.github.tomakehurst.wiremock.client.*

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
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

        when:
        def jira = new JiraService("http://localhostwithtrailing/", "user", "password")

        then:
        jira.baseURL.toString() == "http://localhostwithtrailing"
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

        cleanup:
        stopServer(server)
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

        cleanup:
        stopServer(server)
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

    Map setIssueLabelsRequestData(Map mixins = [:]) {
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
            fields: [
                labels: result.data.names
            ]
        ])

        result.path = "/rest/api/2/issue/${result.data.issueIdOrKey}"

        return result << mixins
    }

    Map setIssueLabelsResponseData(Map mixins = [:]) {
        def result = [
            status: 200
        ]

        return result << mixins
    }

    def "set issue labels with invalid issueIdOrKey"() {
        given:
        def request = setIssueLabelsRequestData()
        def response = setIssueLabelsResponseData()

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.setIssueLabels(null, request.data.names)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to set labels for Jira issue. 'issueIdOrKey' is undefined."

        when:
        service.setIssueLabels(" ", request.data.names)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to set labels for Jira issue. 'issueIdOrKey' is undefined."
    }

    def "set issue labels with invalid names"() {
        given:
        def request = setIssueLabelsRequestData()
        def response = setIssueLabelsResponseData()

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.setIssueLabels(request.data.issueIdOrKey, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to set labels for Jira issue. 'names' is undefined."
    }

    def "set issue labels"() {
        given:
        def request = setIssueLabelsRequestData()
        def response = setIssueLabelsResponseData()

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.setIssueLabels(request.data.issueIdOrKey, request.data.names)

        then:
        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "set issue labels to empty list"() {
        given:
        def request = setIssueLabelsRequestData([
            body: JsonOutput.toJson([
                fields: [
                    labels: []
                ]
            ])
        ])
        def response = setIssueLabelsResponseData()

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.setIssueLabels(request.data.issueIdOrKey, [])

        then:
        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "set issue labels with HTTP 404 failure"() {
        given:
        def request = setIssueLabelsRequestData()
        def response = setIssueLabelsResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.setIssueLabels(request.data.issueIdOrKey, request.data.names)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to set labels for Jira issue. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "set issue labels with HTTP 500 failure"() {
        given:
        def request = setIssueLabelsRequestData()
        def response = setIssueLabelsResponseData([
            body: "Sorry, doesn't work!",
            status: 500
        ])

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.setIssueLabels(request.data.issueIdOrKey, request.data.names)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to set labels for Jira issue. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

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

        cleanup:
        stopServer(server)
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

        cleanup:
        stopServer(server)
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

        cleanup:
        stopServer(server)
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

        cleanup:
        stopServer(server)
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

        cleanup:
        stopServer(server)
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
                fixVersion: null,
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
                fixVersions: [
                    [name: result.data.fixVersion]
                ],
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

        cleanup:
        stopServer(server)
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

        cleanup:
        stopServer(server)
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

        cleanup:
        stopServer(server)
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

        cleanup:
        stopServer(server)
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
                fixVersion: "1.0"
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
                fixVersions: [
                    [name: result.data.fixVersion]
                ],
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
        def result = service.createIssueTypeBug(
            request.data.projectKey, request.data.summary, request.data.description, request.data.fixVersion)

        then:
        result == [ "JIRA-123": request.data.summary ]

        cleanup:
        stopServer(server)
    }

    Map createIssueTypeBugRequestDataWithoutVersion(Map mixins = [:]) {
        def result = [
            data: [
                description: "myDescription",
                projectKey: "PROJECT-1",
                summary: "mySummary"
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
                fixVersions: [
                    [name: null]
                ],
                issuetype: [
                    name: "Bug"
                ]
            ]
        ])

        return result << mixins
    }

    def "create issue type 'Bug' without fix version"() {
        given:
        def request = createIssueTypeBugRequestDataWithoutVersion()
        def response = createIssueTypeBugResponseData([
            body: JsonOutput.toJson([
                "JIRA-123": request.data.summary
            ])
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.createIssueTypeBug(
            request.data.projectKey, request.data.summary, request.data.description)

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
        service.createIssueTypeBug(
            request.data.projectKey, request.data.summary, request.data.description, request.data.fixVersion)

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
        service.createIssueTypeBug(
            request.data.projectKey, request.data.summary, request.data.description, request.data.fixVersion)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to create Jira issue. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }

    Map getDocGenDataRequestData(Map mixins = [:]) {
        def result = [
            data: [
                projectKey: "DEMO"
            ],
            headers: [
                "Accept": "application/json"
            ],
            password: "password",
            username: "username"
        ]

        result.path = "/rest/platform/1.0/docgenreports/${result.data.projectKey}"

        return result << mixins
    }

    Map getDocGenDataResponseData(Map mixins = [:]) {
        def result = [
            body: JsonOutput.toJson([
                project: [:],
                components: [:],
                epics: [:],
                migitations: [:],
                tests: [:]
            ]),
            status: 200
        ]

        return result << mixins
    }

    def "get doc gen data with invalid project key"() {
        given:
        def request = getDocGenDataRequestData()
        def response = getDocGenDataResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getDocGenData(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get documentation generation data from Jira. 'projectKey' is undefined."

        cleanup:
        stopServer(server)
    }

    def "get doc gen data"() {
        given:
        def request = getDocGenDataRequestData()
        def response = getDocGenDataResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getDocGenData("DEMO")

        then:
        result == [
            project: [:],
            components: [:],
            epics: [:],
            migitations: [:],
            tests: [:]
        ]

        cleanup:
        stopServer(server)
    }

    def "get doc gen data with HTTP 400 failure"() {
        given:
        def request = getDocGenDataRequestData()
        def response = getDocGenDataResponseData([
            body: "Sorry, doesn't work!",
            status: 400
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.getDocGenData("DEMO")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get documentation generation data. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }

    def "get doc gen data with HTTP 404 failure"() {
        given:
        def request = getDocGenDataRequestData()
        def response = getDocGenDataResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.getDocGenData("DEMO")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get documentation generation data. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "get doc gen data with HTTP 500 failure"() {
        given:
        def request = getDocGenDataRequestData()
        def response = getDocGenDataResponseData([
            body: "Sorry, doesn't work!",
            status: 500
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.getDocGenData("DEMO")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get documentation generation data. Jira responded with code: '${response.status}' and message: '${response.body}'."

        cleanup:
        stopServer(server)
    }


    Map getIssuesForJQLQueryRequestData(Map mixins = [:]) {
        def result = [
            data: [
                query: [
                    jql: "I want it all!"
                ]
            ],
            headers: [
                "Accept": "application/json"
            ],
            password: "password",
            path: "/rest/api/2/search",
            username: "username"
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

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.getIssuesForJQLQuery(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Jira issues for JQL query. 'query' is undefined."

        when:
        service.getIssuesForJQLQuery([:])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Jira issues for JQL query. 'query' is undefined."

        cleanup:
        stopServer(server)
    }

    def "get issues for JQL query"() {
        given:
        def request = getIssuesForJQLQueryRequestData()
        def response = getIssuesForJQLQueryResponseData()

        def server = createServer(WireMock.&post, request, response)
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

        def server = createServer(WireMock.&post, request, response)
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

        def server = createServer(WireMock.&post, request, response)
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

        def server = createServer(WireMock.&post, request, response)
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

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.getIssuesForJQLQuery(request.data.query)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get Jira issues for JQL query. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }


    Map getIssueTypeMetadataRequestData(Map mixins = [:]) {
        def result = [
            data: [
                projectKey: "DEMO",
                issueTypeId: "1"
            ],
            headers: [
                "Accept": "application/json"
            ],
            password: "password",
            username: "username"
        ]

        result.path = "/rest/api/2/issue/createmeta/${result.data.projectKey}/issuetypes/${result.data.issueTypeId}"

        return result << mixins
    }

    Map getIssueTypeMetadataResponseData(Map mixins = [:]) {
        def result = [
            body: JsonOutput.toJson([
                values: [
                    [
                        fieldId: "customfield_1",
                        name: "Issue Status"
                    ],
                    [
                        fieldId: "customfield_2",
                        name: "Epic Link"
                    ],
                    [
                        fieldId: "issuelinks",
                        name: "Linked Issues"
                    ]
                ]
            ]),
            status: 200
        ]

        return result << mixins
    }

    def "get issue type metadata with invalid project key"() {
        given:
        def request = getIssueTypeMetadataRequestData()
        def response = getIssueTypeMetadataResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getIssueTypeMetadata(null, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Jira issue type metadata. 'projectKey' is undefined."

        cleanup:
        stopServer(server)
    }

    def "get issue type metadata with invalid issue type id"() {
        given:
        def request = getIssueTypeMetadataRequestData()
        def response = getIssueTypeMetadataResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getIssueTypeMetadata("DEMO", null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Jira issue type metadata. 'issueTypeId' is undefined."

        cleanup:
        stopServer(server)
    }

    def "get issue type metadata"() {
        given:
        def request = getIssueTypeMetadataRequestData()
        def response = getIssueTypeMetadataResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getIssueTypeMetadata("DEMO", "1")

        then:
        result == [
            values: [
                [fieldId: "customfield_1", name: "Issue Status"],
                [fieldId: "customfield_2", name: "Epic Link"],
                [fieldId: "issuelinks", name: "Linked Issues"]
            ]
        ]

        cleanup:
        stopServer(server)
    }

    def "get issue type metadata with HTTP 400 failure"() {
        given:
        def request = getIssueTypeMetadataRequestData()
        def response = getIssueTypeMetadataResponseData([
            body: "Sorry, doesn't work!",
            status: 400
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.getIssueTypeMetadata("DEMO", "1")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get Jira issue type metadata. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }

    def "get issue type metadata with HTTP 404 failure"() {
        given:
        def request = getIssueTypeMetadataRequestData()
        def response = getIssueTypeMetadataResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.getIssueTypeMetadata("DEMO", "1")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get Jira issue type metadata. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "get issue type metadata with HTTP 500 failure"() {
        given:
        def request = getIssueTypeMetadataRequestData()
        def response = getIssueTypeMetadataResponseData([
            body: "Sorry, doesn't work!",
            status: 500
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.getIssueTypeMetadata("DEMO", "1")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get Jira issue type metadata. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }


    Map getIssueTypesRequestData(Map mixins = [:]) {
        def result = [
            data: [
                projectKey: "DEMO"
            ],
            headers: [
                "Accept": "application/json"
            ],
            password: "password",
            username: "username"
        ]

        result.path = "/rest/api/2/issue/createmeta/${result.data.projectKey}/issuetypes"

        return result << mixins
    }

    Map getIssueTypesResponseData(Map mixins = [:]) {
        def result = [
            body: JsonOutput.toJson([
                values: [
                    [
                        id: "1",
                        name: "Epic"
                    ],
                    [
                        id: "2",
                        name: "Story"
                    ],
                    [
                        id: "3",
                        name: "Test"
                    ]
                ]
            ]),
            status: 200
        ]

        return result << mixins
    }

    def "get issue types with invalid project key"() {
        given:
        def request = getIssueTypesRequestData()
        def response = getIssueTypesResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getIssueTypes(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Jira issue types. 'projectKey' is undefined."

        cleanup:
        stopServer(server)
    }

    def "get issue types"() {
        given:
        def request = getIssueTypesRequestData()
        def response = getIssueTypesResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getIssueTypes("DEMO")

        then:
        result == [
            values: [
                [id: "1", name: "Epic"], [id: "2", name: "Story"], [id: "3", name: "Test"]
            ]
        ]

        cleanup:
        stopServer(server)
    }

    def "get issue types with HTTP 400 failure"() {
        given:
        def request = getIssueTypesRequestData()
        def response = getIssueTypesResponseData([
            body: "Sorry, doesn't work!",
            status: 400
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.getIssueTypes("DEMO")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get Jira issue types. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }

    def "get issue types with HTTP 404 failure"() {
        given:
        def request = getIssueTypesRequestData()
        def response = getIssueTypesResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.getIssueTypes("DEMO")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get Jira issue types. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "get issue types with HTTP 500 failure"() {
        given:
        def request = getIssueTypesRequestData()
        def response = getIssueTypesResponseData([
            body: "Sorry, doesn't work!",
            status: 500
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.getIssueTypes("DEMO")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get Jira issue types. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }

    Map getProjectRequestData(Map mixins = [:]) {
        def result = [
            data: [
                projectKey: "DEMO"
            ],
            headers: [
                "Accept": "application/json"
            ],
            password: "password",
            username: "username"
        ]

        result.path = "/rest/api/2/project/${result.data.projectKey}"

        return result << mixins
    }

    Map getProjectResponseData(Map mixins = [:]) {
        def result = [
            body: JsonOutput.toJson([
                id: '12005',
                key: 'DEMO'
            ])
        ]

        return result << mixins
    }

    def "get project with invalid project key"() {
        given:
        def request = getProjectRequestData()
        def response = getProjectResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getProject(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get project from Jira. 'projectKey' is undefined."

        cleanup:
        stopServer(server)
    }

    def "get project"() {
        given:
        def request = getProjectRequestData()
        def response = getProjectResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getProject("DEMO")

        then:
        def expect = getProjectResponseData()

        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "get project with HTTP 404 failure"() {
        given:
        def request = getProjectRequestData()
        def response = getProjectResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getProject("DEMO")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get project. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "get project with HTTP 500 failure"() {
        given:
        def request = getProjectRequestData()
        def response = getProjectResponseData([
            status: 500,
            body: "Sorry, doesn't work!"
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getProject("DEMO")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get project. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }

    Map getProjectVersionsRequestData(Map mixins = [:]) {
        def result = [
            data: [
                projectKey: "DEMO"
            ],
            headers: [
                "Accept": "application/json"
            ],
            password: "password",
            username: "username"
        ]

        result.path = "/rest/api/2/project/${result.data.projectKey}/versions"

        return result << mixins
    }

    Map getProjectVersionsResponseData(Map mixins = [:]) {
        def result = [
            body: JsonOutput.toJson([
                [
                    archived: "false",
                    name: "0.1",
                    self: "https://localhost/rest/api/2/version/10937",
                    description: "Initial",
                    id: "10937",
                    projectId: "12005",
                    released: "false"
                ],
                [
                    archived: "false",
                    name: "1.0",
                    self: "https://localhost/rest/api/2/version/10938",
                    id: "10938",
                    projectId: "12005",
                    released: "false"
                ]
            ])
        ]

        return result << mixins
    }

    def "get project versions with invalid project key"() {
        given:
        def request = getProjectVersionsRequestData()
        def response = getProjectVersionsResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getProjectVersions(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get project versions from Jira. 'projectKey' is undefined."

        cleanup:
        stopServer(server)
    }

    def "get project versions"() {
        given:
        def request = getProjectVersionsRequestData()
        def response = getProjectVersionsResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getProjectVersions("DEMO")

        then:
        def expect = getProjectVersionsResponseData()

        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "get project versions with HTTP 404 failure"() {
        given:
        def request = getProjectVersionsRequestData()
        def response = getProjectVersionsResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getProjectVersions("DEMO")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get project versions. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "get project versions with HTTP 500 failure"() {
        given:
        def request = getProjectVersionsRequestData()
        def response = getProjectVersionsResponseData([
            status: 500,
            body: "Sorry, doesn't work!"
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getProjectVersions("DEMO")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get project versions. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

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

        cleanup:
        stopServer(server)
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

        cleanup:
        stopServer(server)
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


    Map updateSelectListFieldsOnIssueRequestData(Map mixins = [:]) {
        def result = [
            data: [
                issueIdOrKey: "JIRA-123",
                fields: [
                    "customfield_1": "Successful"
                ]
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
                "customfield_1": [
                    [
                        set: [
                            value: "Successful"
                        ]
                    ]
                ]
            ]
        ])

        result.path = "/rest/api/2/issue/${result.data.issueIdOrKey}"

        return result << mixins
    }

    Map updateSelectListFieldsOnIssueResponseData(Map mixins = [:]) {
        def result = [
            status: 204
        ]

        return result << mixins
    }

    Map getLabelsRequestData(Map mixins = [:]) {
        def result = [
            data: [
                issueIdOrKey: "TEST-34"
            ],
            headers: [
                "Accept": "application/json"
            ],
            password: "password",
            username: "username",
            queryParams: [
                "fields":"labels"
            ]
        ]

        result.path = "/rest/api/2/issue/${result.data.issueIdOrKey}"

        return result << mixins
    }

    Map getLabelsResponseData(Map mixins = [:]) {
        def result = [
            body: JsonOutput.toJson([
                key: "TEST-34",
                fields: [
                    labels: ["Label1", "Label2"]
                ]
            ])
        ]

        return result << mixins
    }

    def "list issue labels"() {
        given:
        def request = getLabelsRequestData()
        def response = getLabelsResponseData([status: 200])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getLabelsFromIssue(request.data.issueIdOrKey)

        then:
        noExceptionThrown()
        assert result.containsAll(new JsonSlurperClassic().parseText(response.body).fields.labels)

        cleanup:
        stopServer(server)
    }

    def "update select list fields on issue with invalid issueIdOrKey"() {
        given:
        def request = updateSelectListFieldsOnIssueRequestData()
        def response = updateSelectListFieldsOnIssueResponseData()

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.updateSelectListFieldsOnIssue(null, request.data.fields)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to update select list fields on Jira issue. 'issueIdOrKey' is undefined."

        when:
        service.updateSelectListFieldsOnIssue(" ", request.data.fields)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to update select list fields on Jira issue. 'issueIdOrKey' is undefined."

        cleanup:
        stopServer(server)
    }

    def "update select list fields on issue with invalid fields"() {
        given:
        def request = updateSelectListFieldsOnIssueRequestData()
        def response = updateSelectListFieldsOnIssueResponseData()

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.updateSelectListFieldsOnIssue(request.data.issueIdOrKey, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to update select list fields on Jira issue. 'fields' is undefined."

        when:
        service.updateSelectListFieldsOnIssue(request.data.issueIdOrKey, [:])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to update select list fields on Jira issue. 'fields' is undefined."

        cleanup:
        stopServer(server)
    }

    def "update select list fields on issue"() {
        given:
        def request = updateSelectListFieldsOnIssueRequestData()
        def response = updateSelectListFieldsOnIssueResponseData()

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.updateSelectListFieldsOnIssue(request.data.issueIdOrKey, request.data.fields)

        then:
        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "update select list fields on issue with HTTP 404 failure"() {
        given:
        def request = updateSelectListFieldsOnIssueRequestData()
        def response = updateSelectListFieldsOnIssueResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.updateSelectListFieldsOnIssue(request.data.issueIdOrKey, request.data.fields)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to update select list fields on Jira issue JIRA-123. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "update select list fields on issue with HTTP 500 failure"() {
        given:
        def request = updateSelectListFieldsOnIssueRequestData()
        def response = updateSelectListFieldsOnIssueResponseData([
            body: "Sorry, doesn't work!",
            status: 500
        ])

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.updateSelectListFieldsOnIssue(request.data.issueIdOrKey, request.data.fields)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to update select list fields on Jira issue JIRA-123. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }

    Map getComponentsStatusRequestData(Map mixins = [:]) {
        def result =  [
            data: [
                projectKey: "EDP"
            ],
            headers: [
                "Accept": "application/json"
            ],
            password: "password",
            username: "username",
            path: "/rest/platform/1.1/projects/EDP/components",
            queryParams: [
                changeId: "v1",
            ]
        ]
        return result << mixins
    }

    Map getComponentsStatusResponseData(Map mixins = [:]) {
        def result = [
            body: '''{
                "components": [
                    {
                        "branch": "release/6.0",
                        "component": "edp-golang",
                        "include": "true"
                    },
                    {
                        "branch": "master",
                        "component": "edp-spock",
                        "include": "false"
                    }
                ],
                "deployableState": "DEPLOYABLE",
                "message": "no message"
            }''',
            status: 200
        ]
        return result << mixins
    }

    def "check component mismatch deployable"() {
        given:
        def request = getComponentsStatusRequestData()
        def response = getComponentsStatusResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getComponentsStatus('EDP','v1')

        then:
        noExceptionThrown()
        assert result.deployableState == 'DEPLOYABLE'

        cleanup:
        stopServer(server)
    }

    def "check component mismatch not deployable"() {
        given:
        def request = getComponentsStatusRequestData()
        def response = getComponentsStatusResponseData([body: '''{
                "deployableState": "MISCONFIGURED",
                "message": "no message"
            }'''])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getComponentsStatus('EDP','v1')

        then:
        noExceptionThrown()
        assert result.deployableState != 'DEPLOYABLE'

        cleanup:
        stopServer(server)
    }

    def "check component mismatch error"() {
        given:
        def request = getComponentsStatusRequestData()
        def response = getComponentsStatusResponseData([status: 400])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getComponentsStatus('EDP','v1')

        then:
        def e = thrown(RuntimeException)
        e.message.startsWith("Error: unable to get component match check in url http://localhost:${server.port()}/rest/platform/1.1/projects/EDP/components?changeId=v1 Jira responded with code: '400'")

        cleanup:
        stopServer(server)
    }


    Map updateTextFieldsOnIssueRequestData(Map mixins = [:]) {
        def result = [
            data: [
                issueIdOrKey: "JIRA-123",
                fields: [
                    "customfield_1": "1.0-4711"
                ]
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
                "customfield_1": [
                    [
                        set: "1.0-4711"
                    ]
                ]
            ]
        ])

        result.path = "/rest/api/2/issue/${result.data.issueIdOrKey}"

        return result << mixins
    }

    Map updateTextFieldsOnIssueResponseData(Map mixins = [:]) {
        def result = [
            status: 204
        ]

        return result << mixins
    }

    def "update text fields on issue with invalid issueIdOrKey"() {
        given:
        def request = updateTextFieldsOnIssueRequestData()
        def response = updateTextFieldsOnIssueResponseData()

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.updateTextFieldsOnIssue(null, request.data.fields)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to update text fields on Jira issue. 'issueIdOrKey' is undefined."

        when:
        service.updateTextFieldsOnIssue(" ", request.data.fields)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to update text fields on Jira issue. 'issueIdOrKey' is undefined."

        cleanup:
        stopServer(server)
    }

    def "update text fields on issue with invalid fields"() {
        given:
        def request = updateTextFieldsOnIssueRequestData()
        def response = updateTextFieldsOnIssueResponseData()

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.updateTextFieldsOnIssue(request.data.issueIdOrKey, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to update text fields on Jira issue. 'fields' is undefined."

        when:
        service.updateTextFieldsOnIssue(request.data.issueIdOrKey, [:])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to update text fields on Jira issue. 'fields' is undefined."

        cleanup:
        stopServer(server)
    }

    def "update text fields on issue"() {
        given:
        def request = updateTextFieldsOnIssueRequestData()
        def response = updateTextFieldsOnIssueResponseData()

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.updateTextFieldsOnIssue(request.data.issueIdOrKey, request.data.fields)

        then:
        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "update text fields on issue with HTTP 404 failure"() {
        given:
        def request = updateTextFieldsOnIssueRequestData()
        def response = updateTextFieldsOnIssueResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.updateTextFieldsOnIssue(request.data.issueIdOrKey, request.data.fields)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to update text fields on Jira issue JIRA-123. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "update text fields on issue with HTTP 500 failure"() {
        given:
        def request = updateTextFieldsOnIssueRequestData()
        def response = updateTextFieldsOnIssueResponseData([
            body: "Sorry, doesn't work!",
            status: 500
        ])

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.updateTextFieldsOnIssue(request.data.issueIdOrKey, request.data.fields)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to update text fields on Jira issue JIRA-123. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }
}
