package org.ods.orchestration.usecase

import java.nio.file.Files

import org.ods.orchestration.parser.*
import org.ods.orchestration.service.*
import org.ods.orchestration.util.*
import org.ods.services.NexusService
import spock.lang.*
import org.ods.util.IPipelineSteps

import static util.FixtureHelper.*

import util.*

class SonarQubeUseCaseSpec extends SpecHelper {

    NexusService nexus
    Project project
    IPipelineSteps steps
    SonarQubeUseCase usecase

    def setup() {
        project = createProject()
        steps = Spy(util.PipelineSteps)
        nexus = Mock(NexusService)
        usecase = new SonarQubeUseCase(project, steps, nexus)
    }

    def "load reports from path"() {
        given:
        def sqFiles = Files.createTempDirectory("sq-reports-")
        def sqFile1 = Files.createTempFile(sqFiles, "sq", ".md") << "SQ Report 1"
        def sqFile2 = Files.createTempFile(sqFiles, "sq", ".md") << "SQ Report 2"

        when:
        def result = usecase.loadReportsFromPath(sqFiles.toString())

        then:
        result.size() == 2
        result.collect { it.text }.sort() == ["SQ Report 1", "SQ Report 2"]

        cleanup:
        sqFiles.toFile().deleteDir()
    }

    def "load SQ reports from path with empty path"() {
        given:
        def sqFiles = Files.createTempDirectory("sq-reports-")

        when:
        def result = usecase.loadReportsFromPath(sqFiles.toString())

        then:
        result.isEmpty()

        cleanup:
        sqFiles.toFile().deleteDir()
    }

    def "upload SQ reports to Nexus"() {
        given:
        def version = "0.1"
        def repo = project.repositories.first()
        def type = "myType"
        def artifact = Files.createTempFile("sq", ".md").toFile()

        when:
        def result = usecase.uploadReportToNexus(version, repo, type, artifact)

        then:
        1 * nexus.storeArtifactFromFile(
            project.services.nexus.repository.name,
            { "${project.key.toLowerCase()}-${version}" },
            { "${type}-${repo.id}-${version}.md" },
            artifact,
            "application/text"
        )

        cleanup:
        artifact.delete()
    }
}
