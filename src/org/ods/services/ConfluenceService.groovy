package org.ods.services

@Grab(group="com.konghq", module="unirest-java", version="2.4.03", classifier="standalone")

import com.cloudbees.groovy.cps.NonCPS
import kong.unirest.GetRequest
import kong.unirest.Unirest
import org.ods.util.ILogger

@SuppressWarnings(['IfStatementBraces', 'LineLength'])
class ConfluenceService {
    private final ILogger logger

    private final URI baseURL

    private final String username
    private final String password

    ConfluenceService(URI baseURL, String username, String password, ILogger logger) {
        this.baseURL = baseURL
        this.username = username
        this.password = password
        this.logger = logger
    }

    @NonCPS
    String getPage(URI pageURI, String contentType = 'text/html') {
        def response = Unirest.get(pageURI.toString())
            .header('Accept', contentType)
            .basicAuth(this.username, this.password)
            .asString()

        response.ifFailure {
            if (response.status == 404) {
                logger.debug("${pageURI} not found.")
                return null
            }

            def message = "Error: unable to get Confluence page at URL ${pageURI}." +
                ' Confluence responded with code: ' +
                "'${response.getStatus()}' and message: '${response.getBody()}'."

            throw new RuntimeException(message)
        }

        return response.body
    }

    @NonCPS
    String getBase64(URI uri, String contentType = '*/*') {
        def response = Unirest.get(uri.toString())
            .header('Accept', contentType)
            .basicAuth(this.username, this.password)
            .asBytes()

        response.ifFailure {
            if (response.status == 404) {
                logger.debug("${uri} not found.")
                return null
            }

            def message = "Error: unable to get Confluence URL ${uri}." +
                ' Confluence responded with code: ' +
                "'${response.getStatus()}' and message: '${response.getBody()}'."

            throw new RuntimeException(message)
        }
        def bytes = response.body
        return Base64.getEncoder().encodeToString(bytes)
    }

}
