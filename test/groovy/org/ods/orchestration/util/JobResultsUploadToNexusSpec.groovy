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
        List<File> list = new ArrayList()
        when:
        def result = jobResultsUploadToNexus.uploadUnitTestsResults(project, list)
        then:
        1 * logger.warn("Not found unit tests to upload to Nexus.")
        0 * nexus.storeArtifact(*_)
        result == null
    }

    def "upload of some files"() {
        given:
        def uri = new URI("http://lalala")
        Path tmpFilePath1 = Files.createTempFile("file_1", ".txt")
        Path tmpFilePath2 = Files.createTempFile("file_2", ".txt")
        Path tmpFilePath3 = Files.createTempFile("file_3", ".txt")

        def list = [tmpFilePath1.toFile(), tmpFilePath2.toFile(), tmpFilePath3.toFile()]

        // nexus.storeArtifact()
        when:
        def result = jobResultsUploadToNexus.uploadUnitTestsResults(project, list)
        then:
        0 * logger.warn("Not found unit tests to upload to Nexus.")
        1 * nexus.storeArtifact(*_) >> uri
        result == uri.toString()
    }
}
