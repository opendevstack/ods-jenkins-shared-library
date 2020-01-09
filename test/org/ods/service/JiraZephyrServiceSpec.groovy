package org.ods.service

import com.github.tomakehurst.wiremock.client.*

import groovy.json.JsonOutput

import org.apache.http.client.utils.URIBuilder

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class JiraZephyrServiceSpec extends SpecHelper {

    JiraZephyrService createService(int port, String username, String password) {
        return new JiraZephyrService("http://localhost:${port}", username, password)
    }

    Map createTestExecutionForIssueRequestData(Map mixins = [:]) {
        def result = [
            data: [
                issueId: '1234',
                projectId: '2345'
            ],
            headers: [
                "Accept": "application/json",
                "Content-Type": "application/json"
            ],
            password: "password",
            username: "username"
        ]

        result.body = JsonOutput.toJson([
            issueId: "${result.data.issueId}",
            projectId: "${result.data.projectId}"
        ])

        result.path = "/rest/zapi/latest/execution/"

        return result << mixins
    }

    Map createTestExecutionForIssueResponseData(Map mixins = [:]) {
        def result = [
            status: 200,
            body: JsonOutput.toJson([
                "13377": [
                    id: "13377",
                    orderId: "13377",
                    executionStatus: "-1",
                    comment: "",
                    htmlComment: "",
                    cycleId: -1,
                    cycleName: "Ad hoc",
                    versionId: 10001,
                    versionName: "Version2",
                    projectId: 2345,
                    createdBy: "vm_admin",
                    modifiedBy: "vm_admin",
                    assignedTo: "user1",
                    assignedToDisplay: "user1",
                    assignedToUserName: "user1",
                    assigneeType: "assignee",
                    issueId: 1234,
                    issueKey: "SAM-1234",
                    summary: "Test",
                    label: "",
                    component: "",
                    projectKey: "SAM",
                    folderId: 233,
                    folderName: "testfolder"
                ]
            ])
        ]

        return result << mixins
    }

    def "create test execution for issue with invalid issue id"() {
        given:
        def request = createTestExecutionForIssueRequestData()
        def response = createTestExecutionForIssueResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.createTestExecutionForIssue(null, "2345")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create test execution for Jira issue. 'issueId' is undefined."

        cleanup:
        stopServer(server)
    }

    def "create test execution for issue with invalid project id"() {
        given:
        def request = createTestExecutionForIssueRequestData()
        def response = createTestExecutionForIssueResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.createTestExecutionForIssue("1234", null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create test execution for Jira issue. 'projectId' is undefined."

        cleanup:
        stopServer(server)
    }

    def "create test execution for issue "() {
        given:
        def request = createTestExecutionForIssueRequestData()
        def response = createTestExecutionForIssueResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.createTestExecutionForIssue("1234", "2345")

        then:
        def expect = createTestExecutionForIssueResponseData()

        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "create test execution for issue with HTTP 404 failure"() {
        given:
        def request = createTestExecutionForIssueRequestData()
        def response = createTestExecutionForIssueResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.createTestExecutionForIssue("1234", "2345")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to create test execution for Jira issue. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "create test execution for issue with HTTP 500 failure"() {
        given:
        def request = createTestExecutionForIssueRequestData()
        def response = createTestExecutionForIssueResponseData([
            status: 500,
            body: "Sorry, doesn't work!",
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.createTestExecutionForIssue("1234", "2345")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to create test execution for Jira issue. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

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
        def result = service.getProject()

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

    Map getTestDetailsForIssueRequestData(Map mixins = [:]) {
        def result = [
            data: [
                issueId: "25140"
            ],
            headers: [
                "Accept": "application/json"
            ],
            password: "password",
            username: "username"
        ]

        result.path = "/rest/zapi/latest/teststep/${result.data.issueId}"

        return result << mixins
    }

    Map getTestDetailsForIssueResponseData(Map mixins = [:]) {
        def result = [
            body: JsonOutput.toJson([
                stepBeanCollection: [
                    [
                        data: 'Data1',
                        htmlResult: '<p>Result1</p>',
                        orderId:1,
                        customFields:[:],
                        attachmentsMap:[],
                        htmlData: '<p>Data1</p>',
                        result : 'Result1',
                        customFieldValuesMap:[:],
                        htmlStep: '<p>Step1</p>',
                        createdBy: 'user1@mail.com',
                        step: 'Step1',
                        modifiedBy: 'user1@mail.com',
                        id: 201,
                        totalStepCount:2
                    ],
                    [
                        data: 'Data2',
                        htmlResult: '<p>Result2</p>',
                        orderId:2,
                        customFields:[:],
                        attachmentsMap:[],
                        htmlData: '<p>Data2</p>',
                        result : 'Result2',
                        customFieldValuesMap:[:],
                        htmlStep: '<p>Step2</p>',
                        createdBy: 'user2@mail.com',
                        step: 'Step2',
                        modifiedBy: 'user2@mail.com',
                        id: 202,
                        totalStepCount:2
                    ]
                ]
            ])
        ]

        return result << mixins
    }

    def "get test details for issue with invalid issue id"() {
        given:
        def request = getTestDetailsForIssueRequestData()
        def response = getTestDetailsForIssueResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getTestDetailsForIssue(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get test details for Jira issue. 'issueId' is undefined."

        cleanup:
        stopServer(server)
    }

    def "get test details for issue"() {
        given:
        def request = getTestDetailsForIssueRequestData()
        def response = getTestDetailsForIssueResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getTestDetailsForIssue("25140")

        then:
        def expect = getTestDetailsForIssueResponseData()

        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "get test details for issue with HTTP 404 failure"() {
        given:
        def request = getTestDetailsForIssueRequestData()
        def response = getTestDetailsForIssueResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getTestDetailsForIssue("25140")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get test details for Jira issue. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "get test details for issue with HTTP 500 failure"() {
        given:
        def request = getTestDetailsForIssueRequestData()
        def response = getTestDetailsForIssueResponseData([
            status: 500,
            body: "Sorry, doesn't work!"
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getTestDetailsForIssue("25140")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get test details for Jira issue. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }

    Map updateExecutionForIssueRequestData(String status, Map mixins = [:]) {
        def result = [
            data: [
                executionId: "123456",
                status: status
            ],
            headers: [
                "Accept": "application/json",
                "Content-Type": "application/json"
            ],
            password: "password",
            username: "username"
        ]

        result.body = JsonOutput.toJson([
            status: "${result.data.status}"
        ])

        result.path = "/rest/zapi/latest/execution/${result.data.executionId}/execute"

        return result << mixins
    }

    Map updateExecutionForIssueResponseData(String status, Map mixins = [:]) {
        def result = [
            status: 200,
            body: JsonOutput.toJson([
                id: "123456",
                orderId: "123456",
                executionStatus: status,
                comment: "",
                htmlComment: "",
                cycleId: -1,
                cycleName: "Ad hoc",
                versionId: 10001,
                versionName: "Version2",
                projectId: 2345,
                createdBy: "vm_admin",
                modifiedBy: "vm_admin",
                assignedTo: "user1",
                assignedToDisplay: "user1",
                assignedToUserName: "user1",
                assigneeType: "assignee",
                issueId: 1234,
                issueKey: "SAM-1234",
                summary: "Test",
                label: "",
                component: "",
                projectKey: "SAM",
                folderId: 233,
                folderName: "testfolder"
            ])
        ]

        return result << mixins
    }

    def "update execution for issue - generic - with invalid execution id"() {
        given:
        def request = updateExecutionForIssueRequestData("-1")
        def response = updateExecutionForIssueResponseData("-1")

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.updateExecutionForIssue(null, "-1")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to update test execution for Jira issue. 'executionId' is undefined."

        cleanup:
        stopServer(server)
    }

    def "update execution for issue - generic - with invalid status"() {
        given:
        def request = updateExecutionForIssueRequestData("-1")
        def response = updateExecutionForIssueResponseData("-1")

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.updateExecutionForIssue("123456", null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to update test execution for Jira issue. 'status' is undefined."

        cleanup:
        stopServer(server)
    }

    def "update execution for issue - generic"() {
        given:
        def request = updateExecutionForIssueRequestData("-1")
        def response = updateExecutionForIssueResponseData("-1")

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.updateExecutionForIssue("123456", "-1")

        then:
        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "update execution for issue - generic - with HTTP 404 failure"() {
        given:
        def request = updateExecutionForIssueRequestData("-1")
        def response = updateExecutionForIssueResponseData("-1", [
            status: 404
        ])

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.updateExecutionForIssue("123456", "-1")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to update test execution for Jira issue. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "update execution for issue - generic - with HTTP 500 failure"() {
        given:
        def request = updateExecutionForIssueRequestData("-1")
        def response = updateExecutionForIssueResponseData("-1", [
            status: 500,
            body: "Sorry, doesn't work!",
        ])

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.updateExecutionForIssue("123456", "-1")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to update test execution for Jira issue. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }

    def "update execution for issue - Pass"() {
        given:
        def request = updateExecutionForIssueRequestData(JiraZephyrService.ExecutionStatus.PASS)
        def response = updateExecutionForIssueResponseData(JiraZephyrService.ExecutionStatus.PASS)

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.updateExecutionForIssuePass("123456")

        then:
        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "update execution for issue - Fail"() {
        given:
        def request = updateExecutionForIssueRequestData(JiraZephyrService.ExecutionStatus.FAIL)
        def response = updateExecutionForIssueResponseData(JiraZephyrService.ExecutionStatus.FAIL)

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.updateExecutionForIssueFail("123456")

        then:
        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "update execution for issue - WIP"() {
        given:
        def request = updateExecutionForIssueRequestData(JiraZephyrService.ExecutionStatus.WIP)
        def response = updateExecutionForIssueResponseData(JiraZephyrService.ExecutionStatus.WIP)

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.updateExecutionForIssueWip("123456")

        then:
        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "update execution for issue - Blocked"() {
        given:
        def request = updateExecutionForIssueRequestData(JiraZephyrService.ExecutionStatus.BLOCKED)
        def response = updateExecutionForIssueResponseData(JiraZephyrService.ExecutionStatus.BLOCKED)

        def server = createServer(WireMock.&put, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.updateExecutionForIssueBlocked("123456")

        then:
        noExceptionThrown()

        cleanup:
        stopServer(server)
    }
}
