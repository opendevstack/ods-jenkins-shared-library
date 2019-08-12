package org.ods.usecase

import org.ods.service.NexusService

class SonarQubeUseCase {

    private def script
    private def NexusService nexus

    SonarQubeUseCase(def script, nexus) {
        this.script = script
        this.nexus = nexus
    }

    List<File> loadSCRRReportsFromPath(String path) {
        def result = []

        try {
            new File(path).traverse(nameFilter: ~/.*\.docx$/, type: groovy.io.FileType.FILES) { file ->
                result << file
            }
        } catch (FileNotFoundException e) {}

        return result
    }

    String uploadSCRRReportToNexus(String version, Map project, Map repo, String type, File artifact) {
        return this.nexus.storeArtifactFromFile(
            project.services.nexus.repository.name,
            "${project.id.toLowerCase()}-${version}",
            "${type}-${repo.id}-${version}.docx",
            artifact,
            "application/docx"
        )
    }  
}
