package org.ods.core.test.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.recording.SnapshotRecordResult
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils

import static com.github.tomakehurst.wiremock.core.WireMockApp.FILES_ROOT
import static com.github.tomakehurst.wiremock.core.WireMockApp.MAPPINGS_ROOT

@Slf4j
class WiremockManager {
    private static final String WIREMOCK_FILES = "test/resources/wiremock"

    private WireMockServer wireMockServer
    private boolean recording
    private String pathToFiles

    private final String serverType
    private final String defaultURL

    WiremockManager(String serverType, String defaultURL = null) {
        this.serverType = serverType
        this.defaultURL = defaultURL
    }

    WiremockManager withScenario(String scenario){
        this.pathToFiles = "${WIREMOCK_FILES}/${scenario}/${serverType}"
        return this
    }

    WiremockManager startServer(boolean recording = false, String targetURLParam = null) {
        wireMockServer = new WireMockServer(
            WireMockConfiguration
                .wireMockConfig()
                .usingFilesUnderDirectory(pathToFiles)
                .dynamicPort()
        )
        wireMockServer.start()
        log.info("startServer WireMockServer:[{}:{}] usingFilesUnderDirectory:[{}]", serverType, wireMockServer.baseUrl(), pathToFiles)

        if (recording) {
            String targetURL = getTargetURL(targetURLParam)
            cleanExistingRecords()
            log.info("startServer recording:[{}] targetURL:[{}]",recording, targetURL)
            wireMockServer.startRecording(WireMock.recordSpec().forTarget(targetURL).build())
            this.recording = recording
        }
        return this
    }

    WireMockServer mock(){
        return wireMockServer
    }

    void tearDown() {
        log.info("tearDown")

        wireMockServer.stop();
        if (this.recording) {
            try {
                SnapshotRecordResult recording = wireMockServer.stopRecording()
                log.info("record files:[{}]", recording.stubMappings)
            }catch(Exception e){
                log.error("stopRecording error", e)
                throw new RuntimeException("Error when stopRecording", e)
            }
        }
    }

    private void cleanExistingRecords() {
        wireMockServer.resetAll()
        try {
            FileUtils.cleanDirectory(new File(serverType));
        } catch (Exception ex) {
            log.warn("Exception deleting Files: " + ex);
        }
        new File("${pathToFiles}/${MAPPINGS_ROOT}").mkdirs()
        new File("${pathToFiles}/${FILES_ROOT}").mkdirs()
    }

    private String getTargetURL(String targetURLParam) {
        String targetUrl = targetURLParam?:defaultURL
        if (!targetUrl)
            throw new RuntimeException("startServer needs the 'targetUrl' when recording")

        return targetUrl
    }
}
