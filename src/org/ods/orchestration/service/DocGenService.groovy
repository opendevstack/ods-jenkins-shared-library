package org.ods.orchestration.service

@Grab(group = "com.konghq", module = "unirest-java", version = "2.4.03", classifier = "standalone")

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import kong.unirest.HttpResponse
import kong.unirest.Unirest
import org.apache.http.client.utils.URIBuilder
import org.ods.orchestration.mapper.LEVADocResponseMapper
import org.ods.orchestration.util.DocumentHistoryEntry

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
    List<DocumentHistoryEntry> createDocument(String projectId, String buildNumber, String levaDocType, Map data) {
        Object response = doRequest(getCreateDocumentUrl(), projectId, buildNumber, levaDocType, data)
        return processCreateDocumentResponseBody(response.getBody() as String)
    }

    @NonCPS
    void createDocumentOverall(String projectId, String buildNumber, String levaDocType, Map data) {
        doRequest(getCreateDocumentOverallUrl(), projectId, buildNumber, levaDocType, data)
    }

    @NonCPS
    protected String getCreateDocumentUrl() {
        return "${this.baseURL}/levaDoc/{projectId}/{build}/{levaDocType}"
    }

    @NonCPS
    protected String getCreateDocumentOverallUrl() {
        return "${this.baseURL}/levaDoc/{projectId}/{build}/overall/{levaDocType}"
    }

    @NonCPS
    protected List<DocumentHistoryEntry> processCreateDocumentResponseBody(String responseBody) {
        Object parsedBody = new JsonSlurperClassic().parseText(responseBody)
        return LEVADocResponseMapper.parse(parsedBody)
    }

    @NonCPS
    protected Object doRequest(String url, String projectId, String buildNumber, String levaDocType, Map data) {
        def request = Unirest.post(url)
            .routeParam("projectId", projectId)
            .routeParam("build", buildNumber)
            .routeParam("levaDocType", levaDocType)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson(data))
        def response = request.asString()
        response.ifFailure {
            checkError(levaDocType, response)
        }
        return response
    }

    @NonCPS
    protected void checkError(String levaDocType, HttpResponse<String> response) {
        String message = "Error: unable to create document '${levaDocType}'. " +
            "DocGen responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."
        if (response.getStatus() == 404) {
            message = "Error: unable to create document '${levaDocType}'. " +
                "DocGen could not be found at: '${this.baseURL}'."
        }
        throw new RuntimeException(message)
    }

}
