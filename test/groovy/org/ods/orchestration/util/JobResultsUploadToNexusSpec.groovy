package org.ods.orchestration.util

import groovy.util.logging.Slf4j
import org.ods.core.test.LoggerStub
import org.ods.services.NexusService
import org.ods.util.ILogger
import util.SpecHelper

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

        jobResultsUploadToNexus = Spy(new JobResultsUploadToNexus(util, nexus, logger))
    }

    def "empty list of files to upload test"() {
        given:
        def uri = new URI("http://lalala")
        Path tmpFolder = Files.createTempDirectory("testEmptyFolder")
        String tmpFolderPath = tmpFolder.toFile().getAbsolutePath()
        String repoId = "shared-lib-tests"
        String testType = "unit"
        String projectId = project.getJiraProjectKey().toLowerCase()
        String buildNumber = "666"
        String nexusRepoPath = "${projectId}/${repoId}/${buildNumber}"
        String fileName = "${testType}-${projectId}-${repoId}.zip"
        when:
        // jobResultsUploadToNexus.uploadTestsResults(String testType, Project project, def testReportsUnstashPath, String repoId="")
        def result = jobResultsUploadToNexus.uploadTestsResults(testType, project, tmpFolderPath, buildNumber, repoId)
        then:
        0 * logger.warn("Not found unit tests to upload to Nexus.")
        1 * nexus.storeArtifact(NexusService.DEFAULT_NEXUS_REPOSITORY, nexusRepoPath, fileName, _, "application/octet-binary") >> uri
        result == uri.toString()

        cleanup:
        // Files.deleteIfExists(tmpFolder)
        tmpFolder.toFile().deleteDir()
    }

    def "upload of some files"() {
        given:
        def uri = new URI("http://lalala")
        Path tmpFolder = Files.createTempDirectory("test")
        String tmpFolderPath = tmpFolder.toFile().getAbsolutePath()
        String repoId = ""
        String testType = "acceptance"
        String projectId = project.getJiraProjectKey()
        String buildNumber = "666"
        String nexusRepoPath = "${projectId}/${repoId}/${buildNumber}"
        String fileName = "${testType}-${projectId}-${repoId}.zip"

        Files.createTempFile(tmpFolder, "file_1", ".txt") << "Welcome "
        Files.createTempFile(tmpFolder, "file_2", ".txt") << "to the test"
        Files.createTempFile(tmpFolder, "file_3", ".txt") << "contents."

        // nexus.storeArtifact()
        when:
        def result = jobResultsUploadToNexus.uploadTestsResults(testType, project, tmpFolderPath, buildNumber)
        then:
        0 * logger.warn("Not found unit tests to upload to Nexus.")
        1 * nexus.storeArtifact(NexusService.DEFAULT_NEXUS_REPOSITORY, nexusRepoPath, fileName, _, "application/octet-binary") >> uri
        result == uri.toString()

        cleanup:
        tmpFolder.toFile().deleteDir()
    }

    def "upload to real nexus server"() {
        given:
        String nexusUrl = System.properties["nexus.url"]
        String nexusUsername = System.properties["nexus.username"]
        String nexusPassword = System.properties["nexus.password"]
        nexus = Spy(new NexusService(nexusUrl, nexusUsername, nexusPassword))
        jobResultsUploadToNexus = Spy(new JobResultsUploadToNexus(util, nexus, logger))

        def uri = new URI("http://lalala")
        Path tmpFolder = Files.createTempDirectory("testEmptyFolder")
        String tmpFolderPath = tmpFolder.toFile().getAbsolutePath()
        String repoId = "ordgp-releasemanager"
        String buildId = "666"

        String projectId = project.getJiraProjectKey()
        def jiraServices = project.getServices()
        jiraServices.jira.project = "ordgp"
        projectId = project.getJiraProjectKey()

        String fileName = "${testType}-${projectId}-${repoId}.zip".toLowerCase()
        String nexusDirectory = "${projectId}/${repoId}/${buildId}".toLowerCase()

        when:
        def result = jobResultsUploadToNexus.uploadTestsResults(testType, project, tmpFolderPath, buildId, repoId)
        then:
        0 * logger.warn("Not found unit tests to upload to Nexus.")
        1 * nexus.storeArtifact(NexusService.DEFAULT_NEXUS_REPOSITORY, nexusDirectory, fileName, _, "application/octet-binary")
        log.info("URL: " + result)
        result.endsWith("/repository/leva-documentation/" + nexusDirectory + "/" + fileName)
        // Example: /repository/leva-documentation/ordgp/ordgp-releasemanager/666/unit-ordgp-ordgp-releasemanager.zip
        // == uri.toString()

        cleanup:
        // Files.deleteIfExists(tmpFolder)
        tmpFolder.toFile().deleteDir()

        where:
        testType << [ Project.TestType.UNIT, Project.TestType.ACCEPTANCE, Project.TestType.INSTALLATION, Project.TestType.INTEGRATION ]



    }
}
