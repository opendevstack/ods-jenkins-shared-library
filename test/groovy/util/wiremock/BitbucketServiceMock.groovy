package util.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.recording.SnapshotRecordResult
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils

import static com.github.tomakehurst.wiremock.core.WireMockApp.MAPPINGS_ROOT
import static com.github.tomakehurst.wiremock.core.WireMockApp.FILES_ROOT

@Slf4j
class BitbucketServiceMock {
    static final String WIREMOCK_FILES = "test/resources/wiremock/bitbucket"

    boolean recording
    WireMockServer wireMockServer
    String pathToFiles

    BitbucketServiceMock setUp(String scenario){
        pathToFiles = "${WIREMOCK_FILES}/${scenario}"

        wireMockServer = new WireMockServer(
                WireMockConfiguration.wireMockConfig().usingFilesUnderDirectory(pathToFiles).dynamicPort()
        );

        return this
    }

    BitbucketServiceMock startServer(boolean recording = false, String targetUrl = null) {
        log.info("startServer recording:[{}] targetUrl:[{}]",recording, targetUrl)

        this.recording = recording
        wireMockServer.start()
        if (isRecording()) {
            if (!targetUrl)
                throw new RuntimeException("startServer needs the 'targetUrl' when recording")
            cleanExistingRecords()
            wireMockServer.startRecording(WireMock.recordSpec().forTarget(targetUrl).build())
        }
        return this
    }

    void tearDown() {
        log.info("tearDown")

        wireMockServer.stop();
        if (isRecording()) {
            try {
                SnapshotRecordResult recording = wireMockServer.stopRecording()
                log.info("record files:[{}]", recording.stubMappings)
            }catch(Exception e){
                log.error("stopRecording error", e)
            }
        }
    }

    private void cleanExistingRecords() {
        wireMockServer.resetAll()
        try {
            FileUtils.cleanDirectory(new File(pathToFiles));
        } catch (IOException ex) {
            log.error("Exception deleting Files: " + ex);
        }
        new File("${pathToFiles}/${MAPPINGS_ROOT}").mkdirs()
        new File("${pathToFiles}/${FILES_ROOT}").mkdirs()
    }
}
