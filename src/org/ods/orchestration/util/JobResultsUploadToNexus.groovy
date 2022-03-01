package org.ods.orchestration.util

import net.lingala.zip4j.ZipFile
import org.ods.services.NexusService
import org.ods.util.ILogger

import java.nio.file.Files
import java.nio.file.Path

class JobResultsUploadToNexus {

    private final MROPipelineUtil util
    private final NexusService nexus
    private final ILogger logger

    JobResultsUploadToNexus(MROPipelineUtil util, NexusService nexus, ILogger logger) {
        this.util = util
        this.nexus = nexus
        this.logger = logger
    }

    String uploadTestsResults(Project.TestType testType, Project project, List<File> filesList, String repoId="") {
        if (filesList.size() <= 0) {
            logger.warn("Not found unit tests to upload to Nexus.")
            return null
        }
        String fileName = "${testType.toString()}-${project}-${repoId}.zip"
        String projectId = project.getJiraProjectKey()
        String buildNumber = project.steps.env.BUILD_NUMBER

        Path tmpZipFileFolder = Files.createTempDirectory("tmp_folder")
        File tmpZipFile = new File(fileName, tmpZipFileFolder.toFile())
        def zipFile = new ZipFile(tmpZipFile)
        zipFile.addFiles(filesList)

        String nexusRepository = NexusService.DEFAULT_NEXUS_REPOSITORY
        URI report = this.nexus.storeArtifact(
            "${nexusRepository}",
            "${projectId}/${repoId}/${buildNumber}",
            fileName,
            tmpZipFile.getBytes(),
            "application/octet-binary")
        // "text/html"

        logger.info "Tests results of type ${testType} stored in: ${report}"
        return report.toString()
    }
}
