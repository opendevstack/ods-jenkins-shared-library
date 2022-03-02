package org.ods.orchestration.util

import groovy.util.logging.Slf4j
import org.ods.core.test.LoggerStub
import org.ods.services.GitService
import org.ods.services.NexusService
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import util.SpecHelper

import java.lang.reflect.Array
import java.nio.file.Files
import java.nio.file.Path

import static util.FixtureHelper.createProject

@Slf4j
class JobResultsUploadToNexusSpec extends SpecHelper {

    Project project
    NexusService nexus
    MROPipelineUtil util
    ILogger logger
    JobResultsUploadToNexus jobResultsUploadToNexus

    def setup() {
        logger = Spy(new LoggerStub(log))
        //logger = Mock(Logger)

        project = createProject()
        // nexus = Stub(NexusService)
        nexus = Mock(NexusService)
        util = Mock(MROPipelineUtil)

        jobResultsUploadToNexus = Spy( new JobResultsUploadToNexus(util, nexus, logger))
    }

    def "empty list of files to upload test"() {
        given:
        def uri = new URI("http://lalala")
        Path tmpFolder = Files.createTempDirectory("test")
        String tmpFolderPath = tmpFolder.toFile().getAbsolutePath()
        List<File> list = new ArrayList()
        when:
        // jobResultsUploadToNexus.uploadTestsResults(String testType, Project project, def testReportsUnstashPath, String repoId="")
        def result = jobResultsUploadToNexus.uploadTestsResults("unit", project, tmpFolderPath, "someRepo")
        then:
        0 * logger.warn("Not found unit tests to upload to Nexus.")
        1 * nexus.storeArtifact(*_) >> uri
        result == uri.toString()
    }

    def "upload of some files"() {
        given:
        def uri = new URI("http://lalala")
        Path tmpFolder = Files.createTempDirectory("test")
        String tmpFolderPath = tmpFolder.toFile().getAbsolutePath()
        Files.createTempFile(tmpFolder, "file_1", ".txt") << "Welcome "
        Files.createTempFile(tmpFolder, "file_2", ".txt") << "to the test"
        Files.createTempFile(tmpFolder, "file_3", ".txt") << "contents."

        // nexus.storeArtifact()
        when:
        def result = jobResultsUploadToNexus.uploadTestsResults("acceptance", project, tmpFolderPath)
        then:
        0 * logger.warn("Not found unit tests to upload to Nexus.")
        1 * nexus.storeArtifact(*_) >> uri
        result == uri.toString()
    }
}
