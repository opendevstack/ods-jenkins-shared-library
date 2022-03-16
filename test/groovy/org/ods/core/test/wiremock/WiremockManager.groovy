
package org.ods.core.test.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.recording.SnapshotRecordResult
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
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

    WiremockManager(String serverType, String defaultURL) {
        this.serverType = serverType
        this.defaultURL = defaultURL
    }

    WiremockManager withScenario(String scenario){
        this.pathToFiles = "${WIREMOCK_FILES}/${scenario}/${serverType}"
        return this
    }

    WiremockManager startServer(boolean recording) {
        if (recording){
            cleanExistingRecords()
        }

        wireMockServer = new WireMockServer(
            WireMockConfiguration
                .wireMockConfig()
                .usingFilesUnderDirectory(pathToFiles)
                .dynamicPort()
                .enableBrowserProxying(true)
        )
        wireMockServer.start()
        log.info("WireMockServer: [{}:{}]", serverType, wireMockServer.baseUrl())
        log.info("WireMockServer: [{}] targetURL:[{}]", serverType, defaultURL)
        log.info("WireMockServer: [{}] pathToFiles:[{}]", serverType, pathToFiles)

        setUpRecordMode(recording, wireMockServer, defaultURL)
        return this
    }

    WireMockServer server(){
        return wireMockServer
    }

    void tearDown() {
        log.info("tearDown")

        wireMockServer.stop();
        if (recording) {
            try {
                SnapshotRecordResult recording = wireMockServer.stopRecording()
                log.info("record files:[{}]", recording.stubMappings?.size())
                cleanWiremockDatafiles()
            }catch(Exception e){
                log.error("stopRecording error", e)
                throw new RuntimeException("Error when stopRecording", e)
            }
        }
    }

    private void cleanExistingRecords() {
        try {
            log.info("Clean directory $pathToFiles");
            FileUtils.cleanDirectory(new File(pathToFiles));
        } catch (Exception ex) {
            log.warn("Exception deleting Files: " + ex);
        }
        new File("${pathToFiles}/${MAPPINGS_ROOT}").mkdirs()
        new File("${pathToFiles}/${FILES_ROOT}").mkdirs()
    }

    private cleanWiremockDatafiles() {
        log.info("cleanWiremock date_created field")
        Map replaceAllMap = prepareReplaceMap()
        new File("${pathToFiles}/${MAPPINGS_ROOT}").eachFileRecurse() {
            replaceFileInText(it, replaceAllMap)
            updateDateCreated(it)
            log.debug("File " + it.getAbsolutePath() + " contents: ")
            log.debug(it.text)
        }
        new File("${pathToFiles}/${FILES_ROOT}").eachFileRecurse() {replaceFileInText(it, replaceAllMap)}
    }

    private Map prepareReplaceMap() {
        List domainUsers = ["bitbucket.username", "nexus.username", "jira.username"]
        Map replaceAllMap = [:]
        domainUsers.each {
            replaceAllMap[(System.properties[it])] =  "dummyUser"
        }
        replaceAllMap["boehringer-ingelheim"] = "domain"

        (System.properties['wiremock.textToReplace'] as String).tokenize(',').each {
            List value = it.tokenize(':')
            replaceAllMap[value[0]] =  value[1]
        }
        return replaceAllMap.findAll { it != null && it.key != null}
    }

    private void replaceFileInText(File file, Map replaceAllMap) {
        replaceAllMap.each {
            if (file.text.contains(it.key))
                file.text = file.text.replace(it.key, it.value)
        }
    }

    private void updateDateCreated(File file) {
        JsonBuilder jsonBuilderFromFile = getJsonFromText(file.text)
        String equalToJsonField = jsonBuilderFromFile.content?.request?.bodyPatterns?.equalToJson
        if (!equalToJsonField || !equalToJsonField.contains("date_created") || equalToJsonField.contains("json-unit.any-string"))
            return

        JsonBuilder jsonBuilderField = getJsonFromText(equalToJsonField)

        jsonBuilderField.content[0].data.metadata.date_created = "\${json-unit.any-string}"
        if (jsonBuilderField.content[0].data.data.repo){
            jsonBuilderField.content[0].data.data.repo?.data?.tests?.unit?.testReportFiles[0]?.with {
                freeSpace = "\${json-unit.any-number}"
                canonicalPath = "\${json-unit.any-string}"
                usableSpace = "\${json-unit.any-number}"
                totalSpace = "\${json-unit.any-number}"
                path = "\${json-unit.any-string}"
                absolutePath = "\${json-unit.any-string}"
                parent = "\${json-unit.any-string}"
            }
            jsonBuilderField.content[0].data.data?.testFiles?.each { testFile ->
                testFile.path = "\${json-unit.any-string}"

            }
        }

        jsonBuilderFromFile.content.request.bodyPatterns[0].equalToJson = jsonBuilderField.content[0]

        file.text = JsonOutput.prettyPrint(jsonBuilderFromFile.toString())
    }

    private JsonBuilder getJsonFromText(String text) {
        def slurped = new JsonSlurper().parseText(text)
        def builder = new JsonBuilder(slurped)
        return builder
    }

    private void setUpRecordMode(boolean recording, WireMockServer wireMockServer, String targetURL) {
        if (recording) {
            log.info("WireMockServer:[{}] - RECORD MODE!!!!")
            wireMockServer.resetAll()
            wireMockServer.startRecording(WireMock.recordSpec().forTarget(targetURL).build())
            this.recording = recording
        }
    }
}
