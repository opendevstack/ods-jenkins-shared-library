package org.ods.core.test.usecase.levadoc.utils

import com.cloudbees.groovy.cps.NonCPS
import groovy.util.logging.Slf4j
import kong.unirest.HttpResponse
import org.ods.orchestration.service.DocGenService
import org.ods.orchestration.util.DocumentHistoryEntry

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

@Slf4j
class DocGenServiceForWireMock extends DocGenService {

    DocGenServiceForWireMock(String baseURL) {
        super(baseURL)
    }

    @NonCPS
    List<DocumentHistoryEntry> createDocument(String projectId, String buildNumber, String levaDocType, Map data) {
        Object response = doRequest(getCreateDocumentUrl(), projectId, buildNumber, levaDocType, data)
        String responseBody = response.getBody() as String
        log.info("Result of createDocument: ")
        log.info(prettyPrint(toJson(responseBody)))
        return processCreateDocumentResponseBody(responseBody)
    }

    @Override
    void setBaseURL(URI baseURL) {
        super.setBaseURL(baseURL)
    }
}
