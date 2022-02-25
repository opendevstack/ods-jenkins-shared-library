package org.ods.orchestration.util

import net.lingala.zip4j.ZipFile
import org.ods.services.NexusService
import org.ods.util.ILogger

import java.nio.file.Files

class JobResultsUploadToNexus {

    private final MROPipelineUtil util
    private final NexusService nexus
    private final ILogger logger

    JobResultsUploadToNexus(MROPipelineUtil util, NexusService nexus, ILogger logger) {
        this.util = util
        this.nexus = nexus
        this.logger = logger
    }

    void uploadUnitTestsResults(Project project, List<File> filesList) {

        String fileName = "jenkinsJobUnitTests"
        String projectId = project.getJiraProjectKey()
        String buildNumber = project.steps.env.BUILD_NUMBER

        File tmpZipFile = Files.createTempFile(fileName, ".zip")
        def zipFile = new ZipFile(tmpZipFile)
        zipFile.addFiles(filesList)
        byte[] zipArtifact = util.createZipArtifact(fileName + ".zip", filesList, true)

        String nexusRepository = NexusService.DEFAULT_NEXUS_REPOSITORY
        URI report = this.nexus.storeArtifact(
            "${nexusRepository}",
            "${projectId}/${buildNumber}",
            "${fileName}.zip",
            tmpZipFile.getBytes(), "application/octet-binary")
        // "text/html"

        logger.info "Unit tests results stored in: ${report}"
    }
}
