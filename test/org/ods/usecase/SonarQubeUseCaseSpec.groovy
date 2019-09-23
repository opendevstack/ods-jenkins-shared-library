package org.ods.usecase

import java.nio.file.Files

import org.ods.parser.JUnitParser
import org.ods.service.NexusService

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class SonarQubeUseCaseSpec extends SpecHelper {

    SonarQubeUseCase createUseCase(PipelineSteps steps, NexusService nexus) {
        return new SonarQubeUseCase(steps, nexus)
    }

    def "load SCRR reports from path"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def nexus = Mock(NexusService)
        def usecase = createUseCase(steps, nexus)

        def scrrFiles = Files.createTempDirectory("scrr-reports-")
        def scrrFile1 = Files.createTempFile(scrrFiles, "scrr", ".docx") << "SCRR Report 1"
        def scrrFile2 = Files.createTempFile(scrrFiles, "scrr", ".docx") << "SCRR Report 2"

        when:
        def result = usecase.loadSCRRReportsFromPath(scrrFiles.toString())

        then:
        result.size() == 2
        result.collect { it.text }.sort() == ["SCRR Report 1", "SCRR Report 2"]

        cleanup:
        scrrFiles.toFile().deleteDir()
    }

    def "load SCRR reports from path with empty path"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def nexus = Mock(NexusService)
        def usecase = createUseCase(steps, nexus)

        def scrrFiles = Files.createTempDirectory("scrr-reports-")

        when:
        def result = usecase.loadSCRRReportsFromPath(scrrFiles.toString())

        then:
        result.isEmpty()

        cleanup:
        scrrFiles.toFile().deleteDir()
    }

    def "upload SCRR reports to Nexus"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def nexus = Mock(NexusService)
        def usecase = createUseCase(steps, nexus)

        def version = "0.1"
        def project = createProject()
        def repo = project.repositories.first()
        def type = "myType"
        def artifact = Files.createTempFile("scrr", ".docx").toFile()

        when:
        def result = usecase.uploadSCRRReportToNexus(version, project, repo, type, artifact)

        then:
        1 * nexus.storeArtifactFromFile(
            project.services.nexus.repository.name,
            { "${project.id.toLowerCase()}-${version}" },
            { "${type}-${repo.id}-${version}.docx" },
            artifact,
            "application/docx"
        )

        cleanup:
        artifact.delete()
    }
}
