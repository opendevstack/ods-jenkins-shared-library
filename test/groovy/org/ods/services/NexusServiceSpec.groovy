package org.ods.services


import com.github.tomakehurst.wiremock.client.WireMock
import org.apache.http.client.utils.URIBuilder
import org.ods.util.IPipelineSteps
import util.SpecHelper

class NexusServiceSpec extends SpecHelper {

    NexusService createService(int port, String credentialsId) {
        IPipelineSteps steps = Stub(IPipelineSteps)
        steps.env >> [USERNAME: "username", PASSWORD: "password"]
        steps.withCredentials(_,_) >> { List credentialsList, Closure c -> c.call() }
        return new NexusService("http://localhost:${port}", steps, credentialsId)
    }

    def "create with invalid baseURL"() {
        given:
        IPipelineSteps steps = Stub(IPipelineSteps)
        when:
        new NexusService(null, steps, "foo-cd-cd-user-with-password")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Nexus. 'baseURL' is undefined."

        when:
        new NexusService(" ", steps, "foo-cd-cd-user-with-password")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Nexus. 'baseURL' is undefined."

        when:
        new NexusService("invalid URL", steps, "foo-cd-cd-user-with-password")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Nexus. 'invalid URL' is not a valid URI."
    }

    def "create with invalid steps"() {
        when:
        new NexusService("http://localhost",  null, "foo-cd-cd-user-with-password")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Nexus. 'steps' is null."
    }

    def "create with invalid credentialsId"() {
        given:
        IPipelineSteps steps = Stub(IPipelineSteps)
        when:
        new NexusService("http://localhost", steps, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Nexus. 'credentialsId' is undefined"
    }


    Map storeArtifactRequestData(Map mixins = [:]) {
        def result = [
            data: [
                artifact: [0] as byte[],
                contentType: "application/octet-stream",
                directory: "myDirectory",
                name: "myName",
                repository: "myRepository",
            ],
            password: "password",
            path: "/service/rest/v1/components",
            username: "username"
        ]

        result.multipartRequestBody = [
            "raw.directory": result.data.directory,
            "raw.asset1": result.data.artifact,
            "raw.asset1.filename": result.data.name
        ]

        result.queryParams = [
            "repository": result.data.repository
        ]

        return result << mixins
    }

    Map getArtifactRequestData(Map mixins = [:]) {
        def result = [
            data: [
                directory: "myDirectory",
                name: "myName",
                repository: "myRepository",
            ],
            password: "password",
            path: "/repository/myRepository/myDirectory/myName",
            username: "username"
        ]
        return result << mixins
    }

    Map storeArtifactResponseData(Map mixins = [:]) {
        def result = [
            status: 204
        ]

        return result << mixins
    }

    def "store artifact"() {
        given:
        def request = storeArtifactRequestData()
        def response = storeArtifactResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), "foo-cd-cd-user-with-password")

        when:
        def result = service.storeArtifact(request.data.repository, request.data.directory, request.data.name, request.data.artifact, request.data.contentType)

        then:
        result == new URIBuilder("http://localhost:${server.port()}/repository/${request.data.repository}/${request.data.directory}/${request.data.name}").build()

        cleanup:
        stopServer(server)
    }

    def "store artifact with HTTP 404 failure"() {
        given:
        def request = storeArtifactRequestData()
        def response = storeArtifactResponseData([
            status: 404
        ])
        IPipelineSteps steps = Stub(IPipelineSteps)
        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), "foo-cd-cd-user-with-password")

        when:
        service.storeArtifact(request.data.repository, request.data.directory, request.data.name, request.data.artifact, request.data.contentType)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to store artifact. Nexus could not be found at: 'http://localhost:${server.port()}' with repo: ${request.data.repository}."

        cleanup:
        stopServer(server)
    }

    def "store artifact with HTTP 500 failure"() {
        given:
        def request = storeArtifactRequestData()
        def response = storeArtifactResponseData([
            body: "Sorry, doesn't work!",
            status: 500
        ])
        IPipelineSteps steps = Stub(IPipelineSteps)
        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), "foo-cd-cd-user-with-password")

        when:
        service.storeArtifact(request.data.repository, request.data.directory, request.data.name, request.data.artifact, request.data.contentType)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to store artifact. Nexus responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }

    def "retrieve artifact with HTTP 404 failure"() {
        given:
        def request = getArtifactRequestData()
        def response = storeArtifactResponseData([
            status: 404
        ])
        IPipelineSteps steps = Stub(IPipelineSteps)
        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), "foo-cd-cd-user-with-password")

        when:
        service.retrieveArtifact(request.data.repository, request.data.directory, request.data.name, "abc")

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to get artifact. Nexus could not be found at: 'http://localhost:${server.port()}${request.path}'."

        cleanup:
        stopServer(server)
    }

    def "retrieve artifact working"() {
        given:
        def request = getArtifactRequestData()
        def response = storeArtifactResponseData([
            status: 200
        ])
        IPipelineSteps steps = Stub(IPipelineSteps)
        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), "foo-cd-cd-user-with-password")

        when:
        Map result = service.retrieveArtifact(request.data.repository, request.data.directory, request.data.name, "abc")

        then:
        result.uri == new URI("http://localhost:${server.port()}${request.path}")

        cleanup:
        stopServer(server)
    }

    def "uploadJenkinsLogsToNexus should throw exception if text is empty"() {
        given:
        def service = new NexusService("http://localhost:8081", Stub(IPipelineSteps), "credId")

        when:
        service.uploadJenkinsLogsToNexus("", "repo", "dir", "name")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "The parameter 'text' must not be empty."
    }

    def "uploadJenkinsLogsToNexus should throw exception if repoName is empty"() {
        given:
        def service = new NexusService("http://localhost:8081", Stub(IPipelineSteps), "credId")

        when:
        service.uploadJenkinsLogsToNexus("texto", "", "dir", "name")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "The parameter 'repoName' must not be empty."
    }

    def "uploadJenkinsLogsToNexus should throw exception if directory is empty"() {
        given:
        def service = new NexusService("http://localhost:8081", Stub(IPipelineSteps), "credId")

        when:
        service.uploadJenkinsLogsToNexus("texto", "repo", "", "name")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "The parameter 'directory' must not be empty."
    }

    def "uploadJenkinsLogsToNexus should throw exception if text is null"() {
        given:
        def service = new NexusService("http://localhost:8081", Stub(IPipelineSteps), "credId")

        when:
        service.uploadJenkinsLogsToNexus(null, "repo", "dir", "name")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "The parameter 'text' must not be empty."
    }

    def "uploadJenkinsLogsToNexus should throw exception if text is whitespace only"() {
        given:
        def service = new NexusService("http://localhost:8081", Stub(IPipelineSteps), "credId")

        when:
        service.uploadJenkinsLogsToNexus("   ", "repo", "dir", "name")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "The parameter 'text' must not be empty."
    }

    def "uploadJenkinsLogsToNexus should throw exception if repoName is null"() {
        given:
        def service = new NexusService("http://localhost:8081", Stub(IPipelineSteps), "credId")

        when:
        service.uploadJenkinsLogsToNexus("texto", null, "dir", "name")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "The parameter 'repoName' must not be empty."
    }

    def "uploadJenkinsLogsToNexus should throw exception if directory is null"() {
        given:
        def service = new NexusService("http://localhost:8081", Stub(IPipelineSteps), "credId")

        when:
        service.uploadJenkinsLogsToNexus("texto", "repo", null, "name")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "The parameter 'directory' must not be empty."
    }

    def "uploadJenkinsLogsToNexus should successfully upload logs"() {
        given:
        def logContent = "test log content"
        def request = storeArtifactRequestData()
        request.data.artifact = logContent.bytes
        request.data.contentType = "application/text"
        request.data.directory = "logs"
        request.data.name = "test.log"
        request.data.repository = "test-repo"
        request.multipartRequestBody = [
            "raw.directory": "logs",
            "raw.asset1": logContent.bytes,
            "raw.asset1.filename": "test.log"
        ]
        request.queryParams = [
            "repository": "test-repo"
        ]
        
        def response = storeArtifactResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), "foo-cd-cd-user-with-password")

        when:
        def result = service.uploadJenkinsLogsToNexus(logContent, "test-repo", "logs", "test.log")

        then:
        result == new URIBuilder("http://localhost:${server.port()}/repository/test-repo/logs/test.log").build()

        cleanup:
        stopServer(server)
    }

    def "uploadTestReportToNexus should throw exception if name is null"() {
        given:
        def service = new NexusService("http://localhost:8081", Stub(IPipelineSteps), "credId")
        def mockFile = Stub(File)
        mockFile.exists() >> true

        when:
        service.uploadTestReportToNexus(null, mockFile, "repo", "dir")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to upload test report. 'name' is undefined."
    }

    def "uploadTestReportToNexus should throw exception if name is empty"() {
        given:
        def service = new NexusService("http://localhost:8081", Stub(IPipelineSteps), "credId")
        def mockFile = Stub(File)
        mockFile.exists() >> true

        when:
        service.uploadTestReportToNexus("", mockFile, "repo", "dir")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to upload test report. 'name' is undefined."
    }

    def "uploadTestReportToNexus should throw exception if file is null"() {
        given:
        def service = new NexusService("http://localhost:8081", Stub(IPipelineSteps), "credId")

        when:
        service.uploadTestReportToNexus("test.zip", null, "repo", "dir")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to upload test report. 'file' is undefined."
    }

    def "uploadTestReportToNexus should throw exception if file does not exist"() {
        given:
        def service = new NexusService("http://localhost:8081", Stub(IPipelineSteps), "credId")
        def mockFile = Stub(File)
        mockFile.exists() >> false

        when:
        service.uploadTestReportToNexus("test.zip", mockFile, "repo", "dir")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to upload test report. 'file' is undefined."
    }

    def "uploadTestReportToNexus should successfully upload test report"() {
        given:
        def testContent = "test zip content".bytes
        def request = storeArtifactRequestData()
        request.data.artifact = testContent
        request.data.contentType = "application/zip"
        request.data.directory = "reports"
        request.data.name = "test-report.zip"
        request.data.repository = "test-repo"
        request.multipartRequestBody = [
            "raw.directory": "reports",
            "raw.asset1": testContent,
            "raw.asset1.filename": "test-report.zip"
        ]
        request.queryParams = [
            "repository": "test-repo"
        ]
        
        def response = storeArtifactResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), "foo-cd-cd-user-with-password")

        // Create a real temporary file for the test
        def tempFile = File.createTempFile("test-report", ".zip")
        tempFile.bytes = testContent
        tempFile.deleteOnExit()

        when:
        service.uploadTestReportToNexus("test-report.zip", tempFile, "test-repo", "reports")

        then:
        noExceptionThrown()

        cleanup:
        stopServer(server)
        tempFile.delete()
    }
}
