package org.ods.orchestration.util

import net.lingala.zip4j.ZipFile
import org.ods.services.NexusService

import java.nio.file.Files

class JobResultsUploadToNexus {

    private final MROPipelineUtil util
    private final NexusService nexus

    JobResultsUploadToNexus(MROPipelineUtil util, NexusService nexus) {
        this.util = util
        this.nexus = nexus
    }

    void uploadUnitTestsResults(Project project, List<File> filesList) {

        String fileName = "jenkinsJobUnitTests"
        String projectId = project.getJiraProjectKey()
        String buildNumber = project.steps.env.BUILD_NUMBER

        File tmpZipFile = Files.createTempFile(fileName, ".zip")
        def zipFile = new ZipFile(tmpZipFile)
        zipFile.addFiles(filesList)
        byte[] zipArtifact = util.createZipArtifact(fileName + ".zip", files, true)

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
