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

    String uploadUnitTestsResults(Project project, List<File> filesList) {
        String fileName = "jenkinsJobUnitTests"
        String projectId = project.getJiraProjectKey()
        String buildNumber = project.steps.env.BUILD_NUMBER
        String reportFile = "${fileName}.zip"

        File tmpZipFile = Files.createTempFile(reportFile)
        def zipFile = new ZipFile(tmpZipFile)
        zipFile.addFiles(filesList)
        util.createZipArtifact(reportFile, filesList, true)

        String nexusRepository = NexusService.DEFAULT_NEXUS_REPOSITORY
        URI report = this.nexus.storeArtifact(
            "${nexusRepository}",
            "${projectId}/${buildNumber}",
            reportFile,
            tmpZipFile.getBytes(), "application/octet-binary")

        logger.info "Tests results stored in: ${report}"
        return report.toString()
    }
}
