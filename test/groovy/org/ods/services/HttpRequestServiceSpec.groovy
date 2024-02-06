package org.ods.services

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.ods.util.AuthUtil
import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase
import static com.github.tomakehurst.wiremock.client.WireMock.*;


class HttpRequestServiceSpec extends PipelineSpockTestBase {

    def "i can make a GET request"() {
        given:
        String username = "alice"
        String password = "if-you-can-read-this-you-are-too-close"

        Map<String, String> request = ["request key": "request value"]
        Map<String, String> response = ["response key": "response value"]

        def server = new WireMockServer(WireMockConfiguration.options().dynamicPort())
        server.stubFor(get("/")
            .willReturn(ok("OK")))
        server.start()
        WireMock.configureFor(server.port())

        def service = new HttpRequestService(this, new Logger(this, false))
        when:
        def result = service.asString(HttpRequestService.HTTP_METHOD_GET,
            HttpRequestService.AUTHORIZATION_SCHEME_BASIC,
            AuthUtil.base64("${username}:${password}"), server.baseUrl())

        then:
        assert result == "OK"

    }

    def "i can make a POST request with a String body"() {
        given:

        String username = "alice"
        String password = "if-you-can-read-this-you-are-too-close"

        Map<String, String> request = ["request key": "request value"]
        Map<String, String> response = ["response key": "response value"]

        def server = new WireMockServer(WireMockConfiguration.options().dynamicPort())
        server.stubFor(post("/")
            .willReturn(ok("OK")))
        server.start()
        WireMock.configureFor(server.port())
        def service = new HttpRequestService(this, new Logger(this, false))
        def payload = [key: "value"]

        when:
        def result = service.asString(HttpRequestService.HTTP_METHOD_POST,
            HttpRequestService.AUTHORIZATION_SCHEME_BASIC,
            AuthUtil.base64("${username}:${password}"), server.baseUrl(), payload as String)

        then:
        assert result == "OK"
    }

    def "I can make a PUT request with a String body"() {
        given:

        String username = "alice"
        String password = "if-you-can-read-this-you-are-too-close"

        Map<String, String> request = ["request key": "request value"]
        Map<String, String> response = ["response key": "response value"]

        def server = new WireMockServer(WireMockConfiguration.options().dynamicPort())
        server.stubFor(put("/")
            .willReturn(ok("OK")))
        server.start()
        WireMock.configureFor(server.port())
        def service = new HttpRequestService(this, new Logger(this, false))
        def payload = [key: "value"]

        when:
        def result = service.asString(HttpRequestService.HTTP_METHOD_PUT,
            HttpRequestService.AUTHORIZATION_SCHEME_BASIC,
            AuthUtil.base64("${username}:${password}"), server.baseUrl(), payload as String)

        then:
        assert result == "OK"
    }

    def "i can make a POST request with a Map body"() {
        given:

        String username = "alice"
        String password = "if-you-can-read-this-you-are-too-close"

        Map<String, String> request = ["request key": "request value"]
        Map<String, String> response = ["response key": "response value"]

        def server = new WireMockServer(WireMockConfiguration.options().dynamicPort())
        server.stubFor(post("/")
            .willReturn(ok("OK")))
        server.start()
        WireMock.configureFor(server.port())
        def service = new HttpRequestService(this, new Logger(this, false))
        def payload = [key: "value"]

        when:
        def result = service.asString(HttpRequestService.HTTP_METHOD_POST,
            HttpRequestService.AUTHORIZATION_SCHEME_BASIC,
            AuthUtil.base64("${username}:${password}"), server.baseUrl(), payload)

        then:
        assert result == "OK"
    }

    def "I can make a PUT request with a Map body"() {
        given:

        String username = "alice"
        String password = "if-you-can-read-this-you-are-too-close"

        Map<String, String> request = ["request key": "request value"]
        Map<String, String> response = ["response key": "response value"]

        def server = new WireMockServer(WireMockConfiguration.options().dynamicPort())
        server.stubFor(put("/")
            .willReturn(ok("OK")))
        server.start()
        WireMock.configureFor(server.port())
        def service = new HttpRequestService(this, new Logger(this, false))
        def payload = [key: "value"]

        when:
        def result = service.asString(HttpRequestService.HTTP_METHOD_PUT,
            HttpRequestService.AUTHORIZATION_SCHEME_BASIC,
            AuthUtil.base64("${username}:${password}"), server.baseUrl(), payload)

        then:
        assert result == "OK"
    }
}
