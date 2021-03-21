package util.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import com.github.tomakehurst.wiremock.recording.SnapshotRecordResult
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils

import static com.github.tomakehurst.wiremock.core.WireMockApp.MAPPINGS_ROOT
import static com.github.tomakehurst.wiremock.core.WireMockApp.FILES_ROOT

@Slf4j
class WiremockFacade {
    private final WireMockServer wireMockServer
    private final String defaultURL
    private String pathToFiles
    private boolean recording

    WiremockFacade (String pathToRootFile, String defaultURL = null) {
        this.pathToFiles = pathToRootFile
        this.defaultURL = defaultURL
        this.wireMockServer = new WireMockServer(
            WireMockConfiguration
                .wireMockConfig()
                .usingFilesUnderDirectory(pathToRootFile)
                .dynamicPort()
                .extensions(new ResponseTemplateTransformer(false))
        )
    }

    WiremockFacade withScenario(String scenario){
        this.pathToFiles = "${pathToFiles}/${scenario}"
        return this
    }

    WiremockFacade startServer(boolean recording = false, String targetURLParam = null) {
        wireMockServer.start()
        if (recording) {
            String targetURL = getTargetURL(targetURLParam)
            cleanExistingRecords()
            log.info("startServer recording:[{}] targetURL:[{}]",recording, targetURL)
            wireMockServer.startRecording(WireMock.recordSpec().forTarget(targetURL).build())
            this.recording = recording
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
                throw new RuntimeException("Error when stopRecording", e)
            }
        }
    }

    private void cleanExistingRecords() {
        wireMockServer.resetAll()
        try {
            FileUtils.cleanDirectory(new File(pathToFiles));
        } catch (IOException ex) {
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
