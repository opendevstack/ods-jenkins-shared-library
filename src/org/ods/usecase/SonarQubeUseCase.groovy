package org.ods.usecase

import org.ods.service.NexusService
import org.ods.util.IPipelineSteps

class SonarQubeUseCase {

    private NexusService nexus
    private IPipelineSteps steps

    SonarQubeUseCase(IPipelineSteps steps, nexus) {
        this.steps = steps
        this.nexus = nexus
    }

    List<File> loadReportsFromPath(String path) {
        def result = []

        try {
            new File(path).traverse(nameFilter: ~/.*\.docx$/, type: groovy.io.FileType.FILES) { file ->
                result << file
            }
        } catch (FileNotFoundException e) {}

        return result
    }

    String uploadReportToNexus(String version, Map project, Map repo, String type, File artifact) {
        return this.nexus.storeArtifactFromFile(
            project.services.nexus.repository.name,
            "${project.id.toLowerCase()}-${version}",
            "${type}-${repo.id}-${version}.docx",
            artifact,
            "application/docx"
        )
    }  
}
