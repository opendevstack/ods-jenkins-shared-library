package org.ods.orchestration.service

@Grab(group = "com.konghq", module = "unirest-java", version = "2.4.03", classifier = "standalone")

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
    Map createDocument(String projectId, String buildNumber, String levaDocType, Map data) {
        System.println("hola")
        def response = Unirest.post("${this.baseURL}/levaDoc/{projectId}/{build}/{levaDocType}")
            .routeParam("projectId", projectId)
            .routeParam("build", buildNumber)
            .routeParam("levaDocType", levaDocType)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson(data))
            .asString()

        response.ifFailure {
            def message = "Error: unable to create document '${levaDocType}'. " +
                "DocGen responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to create document '${levaDocType}'. " +
                    "DocGen could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(response.getBody())
    }

}
