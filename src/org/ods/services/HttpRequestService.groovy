package org.ods.services

import com.cloudbees.groovy.cps.NonCPS
import kong.unirest.Unirest
import org.ods.util.ILogger

class HttpRequestService {

    public static final String AUTHORIZATION_SCHEME_BASIC = "Basic"
    public static final String AUTHORIZATION_SCHEME_BEARER = "Bearer"

    public static final String HTTP_METHOD_GET = "GET"
    public static final String HTTP_METHOD_POST = "POST"
    public static final String HTTP_METHOD_PUT = "PUT"

    private final def script

    private final ILogger logger

    HttpRequestService(def script, ILogger logger) {
        this.script = script
        this.logger = logger
    }

    @NonCPS
    String asString(String httpMethod, String authScheme, String authParameter, String url) {
        def response = Unirest.request(httpMethod, url)
            .header("Authorization", "${authScheme} ${authParameter}")
            .asString()

        response.ifFailure {
            def message = "Could not send HTTP request ${response.getStatus()} ${response.getStatusText()} to ${url}"
            logger.error(message)
            throw new RuntimeException(message)
        }
        return response.body
    }

    @NonCPS
    String asString(String httpMethod, String authScheme, String authParameter, String url, def body) {
        def response = Unirest.request(httpMethod, url)
            .header("Authorization", "${authScheme} ${authParameter}")
            .body(body)
            .asString()

        response.ifFailure {
            def message = "Could not send HTTP request ${response.getStatus()} ${response.getStatusText()} to ${url}"
            logger.error(message)
            throw new RuntimeException(message)
        }
        return response.body
    }

}
