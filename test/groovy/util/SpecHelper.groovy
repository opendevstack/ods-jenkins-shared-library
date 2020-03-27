package util

import com.github.tomakehurst.wiremock.*
import com.github.tomakehurst.wiremock.client.*
import com.github.tomakehurst.wiremock.core.*

import java.nio.file.Paths

import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables

import spock.lang.*

class SpecHelper extends Specification {
    @Rule
    EnvironmentVariables env = new EnvironmentVariables()

    WireMockServer createServer(def b, Map request, Map response) {
        def server = startWireMockServer()

        def builder = b(WireMock.urlPathMatching(request.path))

        // Request
        if (request.username && request.password) {
            builder.withBasicAuth(request.username, request.password)
        }

        request.queryParams.each { name, value ->
            builder.withQueryParam(name, WireMock.equalTo(value))
        }

        request.headers.each { name, value ->
            builder.withHeader(name, WireMock.equalTo(value))
        }

        if (request.multipartRequestBody) {
            request.multipartRequestBody.each { name, value ->
                def multipart = WireMock.aMultipart().withName(name)

                def matcher = WireMock.&equalTo
                // Check if the body is of type byte array so we can apply an appropriate matcher
                if (value.getClass().isArray() && value.getClass().getComponentType().getTypeName() == "byte") {
                    matcher = WireMock.&binaryEqualTo
                }

                multipart.withBody(matcher(value))
                builder.withMultipartRequestBody(multipart)
            }
        } else if (request.body) {
            builder.withRequestBody(WireMock.equalTo(request.body))
        }

        // Response Mapping
        def responseBuilder = WireMock.aResponse()

        response.headers.each { name, value ->
            responseBuilder.withHeader(name, value)
        }

        if (response.body) {
            responseBuilder.withBody(response.body)
        }

        if (response.status) {
            responseBuilder.withStatus(response.status)
        }

        builder.willReturn(responseBuilder)
        server.stubFor(builder)

        return server
    }

    String decodeBase64(String value) {
        return new String(value.decodeBase64())
    }

    String encodeBase64(byte[] bytes) {
        return bytes.encodeBase64().toString()
    }

    WireMockServer startWireMockServer() {
        def server = new WireMockServer(
            WireMockConfiguration.options().dynamicPort()
        )

        server.start()
        WireMock.configureFor(server.port())

        return server
    }

    void stopServer(WireMockServer server) {
        server.stop()
    }
}
