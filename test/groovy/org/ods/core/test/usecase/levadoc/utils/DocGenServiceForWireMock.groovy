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
        def result = super.createDocument(projectId, buildNumber, levaDocType, data)
        log.info("Result of createDocument: ")
        log.info(prettyPrint(toJson(result)))
        return result
    }

    @NonCPS
    void createDocumentOverall(String projectId, String buildNumber, String levaDocType, Map data) {
        createDocument(projectId, buildNumber, levaDocType, data)
    }

    @Override
    void setBaseURL(URI baseURL) {
        super.setBaseURL(baseURL)
    }
}
