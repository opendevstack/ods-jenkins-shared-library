package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import org.ods.services.NexusService
import org.ods.util.ILogger

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class JobResultsUploadToNexus {

    private final MROPipelineUtil util
    private final NexusService nexus

    JobResultsUploadToNexus(MROPipelineUtil util, NexusService nexus) {
        this.util = util
        this.nexus = nexus
    }

    @NonCPS
    String uploadTestsResults(String testType,
                              Project project,
                              URI testReportsUnstashPath,
                              String buildId,
                              String repoId = "") {

        String projectId = project.getJiraProjectKey()
        String fileName = "${testType}-${projectId}-${repoId}.zip".toLowerCase()
        String nexusDirectory = "${projectId}/${repoId}/${buildId}".toLowerCase()

        Path tmpZipFileFolder = Files.createTempDirectory("tmp_folder")
        Path filePath = Paths.get(tmpZipFileFolder.toString(), fileName)
        Path folderToAdd = Paths.get(testReportsUnstashPath)

        def zipFile = new ZipFile(filePath.toString())
        ZipParameters zipParameters = new ZipParameters()
        zipParameters.setIncludeRootFolder(false)
        zipFile.addFolder(folderToAdd.toFile(), zipParameters)

        String nexusRepository = NexusService.DEFAULT_NEXUS_REPOSITORY
        URI report = this.nexus.storeArtifact(
            "${nexusRepository}",
            nexusDirectory,
            fileName,
            filePath.getBytes(),
            "application/octet-binary")

        return report.toString()
    }
}
