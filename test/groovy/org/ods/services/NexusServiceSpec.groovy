package org.ods.services

import com.github.tomakehurst.wiremock.client.*

import org.apache.http.client.utils.URIBuilder
import org.ods.services.NexusService
import spock.lang.*

import util.*

class NexusServiceSpec extends SpecHelper {

    NexusService createService(int port, String username, String password) {
        return new NexusService("http://localhost:${port}", username, password)
    }

    def "create with invalid baseURL"() {
        when:
        new NexusService(null, "username", "password")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Nexus. 'baseURL' is undefined."

        when:
        new NexusService(" ", "username", "password")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Nexus. 'baseURL' is undefined."

        when:
        new NexusService("invalid URL", "username", "password")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Nexus. 'invalid URL' is not a valid URI."
    }

    def "create with invalid username"() {
        when:
        new NexusService("http://localhost", null, "password")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Nexus. 'username' is undefined."

        when:
        new NexusService("http://localhost", " ", "password")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Nexus. 'username' is undefined."
    }

    def "create with invalid password"() {
        when:
        new NexusService("http://localhost", "username", null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Nexus. 'password' is undefined."

        when:
        new NexusService("http://localhost", "username", " ")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to Nexus. 'password' is undefined."
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
        def service = createService(server.port(), request.username, request.password)

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

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

        when:
        service.storeArtifact(request.data.repository, request.data.directory, request.data.name, request.data.artifact, request.data.contentType)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to store artifact. Nexus could not be found at: 'http://localhost:${server.port()}'."

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

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port(), request.username, request.password)

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
  
        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)
  
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
  
        def server = createServer(WireMock.&get, request, response)
        def service = createService(server.port(), request.username, request.password)
  
        when:
        Map result = service.retrieveArtifact(request.data.repository, request.data.directory, request.data.name, "abc")
  
        then:
        result.uri == new URI("http://localhost:${server.port()}${request.path}")
  
        cleanup:
        stopServer(server)
    }

}
