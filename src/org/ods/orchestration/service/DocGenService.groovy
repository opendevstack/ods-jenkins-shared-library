package org.ods.orchestration.service

@Grab(group="com.konghq", module="unirest-java", version="2.4.03", classifier="standalone")

import com.cloudbees.groovy.cps.NonCPS

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

import kong.unirest.Unirest

import org.apache.http.client.utils.URIBuilder

class DocGenService {

    URI baseURL

    DocGenService(String baseURL) {
        if (!baseURL?.trim()) {
            throw new IllegalArgumentException("Error: unable to connect to DocGen. 'baseURL' is undefined.")
        }

        try {
            this.baseURL = new URIBuilder(baseURL).build()
        } catch (e) {
            throw new IllegalArgumentException(
                "Error: unable to connect to DocGen. '${baseURL}' is not a valid URI."
            ).initCause(e)
        }
    }

    @NonCPS
    int healthCheck() {
        def response = Unirest.get("${this.baseURL}/health").asEmpty()
        return response.getStatus()
    }

    @NonCPS
    byte[] createDocument(String type, String version, Map data) {
        def response = Unirest.post("${this.baseURL}/document")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson([
                metadata: [
                    type: type,
                    version: version
                ],
                data: data,
            ]))
            .asString()

        response.ifFailure {
            def message = "Error: unable to create document '${type} (v${version})'. " +
                "DocGen responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to create document '${type} (v${version})'. " +
                    "DocGen could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        def result = new JsonSlurperClassic().parseText(response.getBody())
        return decodeBase64(result.data)
    }

    @NonCPS
    private static byte[] decodeBase64(String base64String) {
        return Base64.decoder.decode(base64String)
    }

}
