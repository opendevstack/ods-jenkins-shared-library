package org.ods.orchestration.service

import com.github.tomakehurst.wiremock.client.WireMock
import groovy.json.JsonOutput
import util.SpecHelper

class DocGenServiceSpec extends SpecHelper {

    DocGenService createService(int port) {
        return new DocGenService("http://localhost:${port}")
    }

    Map createDocumentRequestData(Map mixins = [:]) {
        def projectId = 'PRJ1'
        def build = '2'
        def levaDocType = 'CSD'

        def result = [
            data   : [
                data       : [a: 1, b: 2, c: 3],
                projectId  : "PRJ1",
                build      : "2",
                levaDocType: "CSD"
            ],
            headers: [
                "Accept"      : "application/json",
                "Content-Type": "application/json"
            ],
            path   : "/levaDoc/${projectId}/${build}/${levaDocType}"
        ]

        result.body = JsonOutput.toJson(result.data.data)

        return result << mixins
    }

    Map createDocumentResponseData(Map mixins = [:]) {
        def result = [
            body   : JsonOutput.toJson([
                nexusURL: 'http://nexus-url/doc'
            ]),
            headers: [
                "Content-Type": "application/json"
            ],
            status : 200
        ]

        return result << mixins
    }

    def "instantiate with invalid baseURL"() {
        when:
        new DocGenService(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to DocGen. 'baseURL' is undefined."

        when:
        new DocGenService(" ")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to DocGen. 'baseURL' is undefined."

        when:
        new DocGenService("invalid URL")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to connect to DocGen. 'invalid URL' is not a valid URI."
    }

    def "create document"() {
        given:
        def request = createDocumentRequestData()
        def response = createDocumentResponseData()

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port())

        when:
        def result = service.createDocument(request.data.projectId, request.data.build, request.data.levaDocType, request.data.data)

        then:
        result == [nexusURL: 'http://nexus-url/doc']

        cleanup:
        stopServer(server)
    }

    def "create document with HTTP 404 failure"() {
        given:
        def request = createDocumentRequestData()
        def response = createDocumentResponseData([
            status: 404
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port())

        when:
        service.createDocument(request.data.projectId, request.data.build, request.data.levaDocType, request.data.data)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to create document '${request.data.levaDocType}'. DocGen could not be found at: 'http://localhost:${server.port()}'."

        cleanup:
        stopServer(server)
    }

    def "create document with HTTP 500 failure"() {
        given:
        def request = createDocumentRequestData()
        def response = createDocumentResponseData([
            body  : "Sorry, doesn't work!",
            status: 500
        ])

        def server = createServer(WireMock.&post, request, response)
        def service = createService(server.port())

        when:
        service.createDocument(request.data.projectId, request.data.build, request.data.levaDocType, request.data.data)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to create document '${request.data.levaDocType}'. DocGen responded with code: '${response.status}' and message: 'Sorry, doesn\'t work!'."

        cleanup:
        stopServer(server)
    }
}
