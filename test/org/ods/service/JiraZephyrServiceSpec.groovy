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
                projectId: '2345',
                cycleId: '889'
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
            projectId: "${result.data.projectId}",
            cycleId: "${result.data.cycleId}",
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
                    cycleId: "889",
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
        def result = service.createTestExecutionForIssue(null, "2345", "889")

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
        def result = service.createTestExecutionForIssue("1234", null, "889")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create test execution for Jira issue. 'projectId' is undefined."

        cleanup:
        stopServer(server)
    }

    def "create test execution for issue with invalid cycle id"() {
        given:
        def request = createTestExecutionForIssueRequestData()
        def response = createTestExecutionForIssueResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.createTestExecutionForIssue("1234", "2345", null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create test execution for Jira issue. 'cycleId' is undefined."

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
        def result = service.createTestExecutionForIssue("1234", "2345", "889")

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
        def result = service.createTestExecutionForIssue("1234", "2345", "889")

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
        def result = service.createTestExecutionForIssue("1234", "2345", "889")

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
            body: "Sorry, doesn't work!"
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

    Map createTestCycleRequestData(Map mixins = [:]) {
        def result = [
            data: [
                projectId: "123456",
                versionId: "2234",
                name: "Test",
                build: "myBuild",
                environment: "myEnv",
            ],
            headers: [
                "Accept": "application/json",
                "Content-Type": "application/json"
            ],
            password: "password",
            username: "username"
        ]

        result.body = JsonOutput.toJson([
            projectId: "${result.data.projectId}",
            versionId: "${result.data.versionId}",
            name: "${result.data.name}",
            build: "${result.data.build}",
            environment: "${result.data.environment}"
        ])

        result.path = "/rest/zapi/latest/cycle/"

        return result << mixins
    }

   Map createTestCycleResponseData(Map mixins = [:]) {
        def result = [
            status: 200,
            body: JsonOutput.toJson([
                id: "5",
                responseMessage: "Cycle Test created successfully."
            ])
        ]

        return result << mixins
    }

    def "create test cycle with invalid issue id"() {
        given:
        def request = createTestCycleRequestData()
        def response = createTestCycleResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.createTestCycle(null, "-1", "Test", "myBuild", "myEnv")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create test cycle for Jira issues. 'projectId' is undefined."

        cleanup:
        stopServer(server)
    }

    def "create test cycle with invalid version Id"() {
        given:
        def request = createTestCycleRequestData()
        def response = createTestCycleResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.createTestCycle("123456", null, "Test", "myBuild", "myEnv")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create test cycle for Jira issues. 'versionId' is undefined."

        cleanup:
        stopServer(server)
    }

    def "create test cycle with invalid name"() {
        given:
        def request = createTestCycleRequestData()
        def response = createTestCycleResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.createTestCycle("123456", "2234", null, "myBuild", "myEnv")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create test cycle for Jira issues. 'name' is undefined."

        cleanup:
        stopServer(server)
    }

    def "create test cycle with invalid build"() {
        given:
        def request = createTestCycleRequestData()
        def response = createTestCycleResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.createTestCycle("123456", "2234", "Test", null, "myEnv")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create test cycle for Jira issues. 'build' is undefined."

        cleanup:
        stopServer(server)
    }

    def "create test cycle with invalid environment"() {
        given:
        def request = createTestCycleRequestData()
        def response = createTestCycleResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.createTestCycle("123456", "2234", "Test", "myBuild", null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create test cycle for Jira issues. 'environment' is undefined."

        cleanup:
        stopServer(server)
    }

    def "create test cycle"() {
        given:
        def request = createTestCycleRequestData()
        def response = createTestCycleResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.createTestCycle("123456", "2234", "Test", "myBuild", "myEnv")

        then:
        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "create test cycle with HTTP 404 failure"() {
        given:
        def request = createTestCycleRequestData()
        def response = createTestCycleResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.createTestCycle("123456", "2234", "Test", "myBuild", "myEnv")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to create test cycle for Jira issues. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "create test cycle with HTTP 500 failure"() {
        given:
        def request = createTestCycleRequestData()
        def response = createTestCycleResponseData([
            status: 500,
            body: "Sorry, doesn't work!"
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.createTestCycle("123456", "2234", "Test", "myBuild", "myEnv")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to create test cycle for Jira issues. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

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

    Map getProjectCyclesRequestData(Map mixins = [:]) {
        def result = [
            headers: [
                "Accept": "application/json"
            ],
            password: "password",
            username: "username",
            queryParameters : [
                projectId : "123",
                versionId : "456"
            ]
        ]

        result.path = "/rest/zapi/latest/cycle"

        return result << mixins
    }

    Map getProjectCyclesResponseData(Map mixins = [:]) {
        def result = [
           body: JsonOutput.toJson([
                recordsCount: "2",
                "-1": [
                        versionName: "0.1", 
                        projectKey: "DEMO", 
                        versionId:"456", 
                        name: "Ad hoc"
                    ], 
                "7": [
                        versionName: "0.1", 
                        projectKey: "DEMO", 
                        versionId: "456", 
                        name: "Cycle name"
                    ]
            ])
        ]

        return result << mixins
    }

    def "get project cycles with invalid project id"() {
        given:
        def request = getProjectCyclesRequestData()
        def response = getProjectCyclesResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getProjectCycles(null, "456")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get project cycles from Jira. 'projectId' is undefined."

        cleanup:
        stopServer(server)
    }

    def "get project cycles with invalid version id"() {
        given:
        def request = getProjectCyclesRequestData()
        def response = getProjectCyclesResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getProjectCycles("123", null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get project cycles from Jira. 'versionId' is undefined."

        cleanup:
        stopServer(server)
    }

    def "get project cycles"() {
        given:
        def request = getProjectCyclesRequestData()
        def response = getProjectCyclesResponseData()

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getProjectCycles("123", "456")

        then:
        def expect = getProjectVersionsResponseData()

        noExceptionThrown()

        cleanup:
        stopServer(server)
    }

    def "get project cycles with HTTP 404 failure"() {
        given:
        def request = getProjectCyclesRequestData()
        def response = getProjectCyclesResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getProjectCycles("123", "456")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get project cycles. Jira could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "get project cycles with HTTP 500 failure"() {
        given:
        def request = getProjectCyclesRequestData()
        def response = getProjectCyclesResponseData([
            status: 500,
            body: "Sorry, doesn't work!"
        ])

        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        def result = service.getProjectCycles("123", "456")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get project cycles. Jira responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }
}
