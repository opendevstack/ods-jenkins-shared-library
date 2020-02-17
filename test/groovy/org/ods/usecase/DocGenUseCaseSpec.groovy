package org.ods.usecase

import groovy.json.JsonOutput

import java.nio.file.Files

import org.ods.service.DocGenService
import org.ods.service.NexusService
import org.ods.util.IPipelineSteps
import org.ods.util.MROPipelineUtil
import org.ods.util.PDFUtil
import org.ods.util.Project

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class DocGenUseCaseSpec extends SpecHelper {

    class DocGenUseCaseImpl extends DocGenUseCase {
        DocGenUseCaseImpl(Project project, PipelineSteps steps, MROPipelineUtil util, DocGenService docGen, NexusService nexus, PDFUtil pdf) {
            super(project, steps, util, docGen, nexus, pdf)
        }

        List<String> getSupportedDocuments() {}
    }

    DocGenService docGen
    NexusService nexus
    PDFUtil pdf
    Project project
    IPipelineSteps steps
    DocGenUseCase usecase
    MROPipelineUtil util

    def setup() {
        steps = Spy(PipelineSteps)
        steps.env.BUILD_ID = "0815"

        project = createProject()
        util = Mock(MROPipelineUtil)
        docGen = Mock(DocGenService)
        nexus = Mock(NexusService)
        pdf = Mock(PDFUtil)
        usecase = Spy(new DocGenUseCaseImpl(project, steps, util, docGen, nexus, pdf))
    }

    def "create document"() {
        given:
        // Test Parameters
        def logFile1 = Files.createTempFile("raw", ".log").toFile() << "Log File 1"
        def logFile2 = Files.createTempFile("raw", ".log").toFile() << "Log File 2"

        def documentType = "myDocumentType"
        def version = project.buildParams.version
        def repo = project.repositories.first()
        def data = [ a: 1, b: 2, c: 3 ]
        def files = [
            "raw/${logFile1.name}": logFile1.bytes,
            "raw/${logFile2.name}": logFile2.bytes
        ]

        // Argument Constraints
        def basename = "${documentType}-${project.key}-${repo.id}-${version}-${steps.env.BUILD_ID}"

        // Stubbed Method Responses
        def document = "PDF".bytes
        def archive = "Archive".bytes
        def nexusUri = new URI("http://nexus")

        when:
        def result = usecase.createDocument(documentType, repo, data, files)

        then:
        1 * docGen.createDocument(documentType, "0.1", data) >> document

        then:
        1 * usecase.getDocumentBasename(documentType, version, steps.env.BUILD_ID,repo)

        then:
        1 * util.createZipArtifact(
            "${basename}.zip",
            [
                "${basename}.pdf": document,
                "raw/${basename}.json": JsonOutput.toJson(data).bytes,
                "raw/${logFile1.name}": logFile1.bytes,
                "raw/${logFile2.name}": logFile2.bytes
            ]
        ) >> archive

        then:
        1 * nexus.storeArtifact(
            project.services.nexus.repository.name,
            "${project.key.toLowerCase()}-${version}",
            "${basename}.zip",
            archive,
            "application/zip"
        ) >> nexusUri

        then:
        result == nexusUri.toString()

        cleanup:
        logFile1.delete()
        logFile2.delete()
    }

    def "create document without repo"() {
        given:
        // Test Parameters
        def documentType = "myDocumentType"
        def version = project.buildParams.version
        def repo = null
        def data = [ a: 1, b: 2, c: 3 ]

        // Argument Constraints
        def basename = "${documentType}-${project.key}-${version}-${steps.env.BUILD_ID}"

        // Stubbed Method Responses
        def document = "PDF".bytes
        def nexusUri = new URI("http://nexus")

        when:
        def result = usecase.createDocument(documentType, repo, data)

        then:
        1 * docGen.createDocument(*_) >> document

        then:
        1 * usecase.getDocumentBasename(documentType, version, steps.env.BUILD_ID, repo)

        then:
        1 * util.createZipArtifact(
            "${basename}.zip",
            [
                "${basename}.pdf": document,
                "raw/${basename}.json": JsonOutput.toJson(data).bytes
            ]
        ) >> new byte[0]

        then:
        1 * nexus.storeArtifact(_, _, "${basename}.zip", *_) >> nexusUri
    }

    def "create document with modifier"() {
        given:
        // Test Parameters
        def documentType = "myDocumentType"
        def version = project.buildParams.version
        def repo = project.repositories.first()
        def data = [ a: 1, b: 2, c: 3 ]
        def modifier = { document ->
            return (new String(document) + "-modified").bytes
        }

        // Argument Constraints
        def basename = "${documentType}-${project.key}-${repo.id}-${version}-${steps.env.BUILD_ID}"

        // Stubbed Method Responses
        def document = "PDF".bytes
        def nexusUri = new URI("http://nexus")

        when:
        def result = usecase.createDocument(documentType, repo, data, [:], modifier)

        then:
        1 * docGen.createDocument(*_) >> document

        then:
        1 * usecase.getDocumentBasename(documentType, version, steps.env.BUILD_ID, repo)

        then:
        1 * util.createZipArtifact(
            "${basename}.zip",
            [
                "${basename}.pdf": "PDF-modified".bytes,
                "raw/${basename}.json": JsonOutput.toJson(data).bytes
            ]
        ) >> new byte[0]

        then:
        1 * nexus.storeArtifact(*_) >> nexusUri
    }

    def "create document with watermark"() {
        given:
        // Test Parameters
        def documentType = "myDocumentType"
        def version = project.buildParams.version
        def repo = project.repositories.first()
        def data = [ a: 1, b: 2, c: 3 ]
        def watermarkText = "Watermark"

        // Argument Constraints
        def basename = "${documentType}-${project.key}-${repo.id}-${version}-${steps.env.BUILD_ID}"

        // Stubbed Method Responses
        def document = getResource("Test-1.pdf").bytes
        def nexusUri = new URI("http://nexus")
        def documentWithWatermark = pdf.addWatermarkText(document, watermarkText)

        when:
        usecase.createDocument(documentType, repo, data, [:], null, null, watermarkText)

        then:
        1 * docGen.createDocument(*_) >> document
        1 * pdf.addWatermarkText(document, watermarkText)
        1 * usecase.getDocumentBasename(documentType, version, steps.env.BUILD_ID, repo)
        1 * util.createZipArtifact(
            "${basename}.zip",
            [
                "${basename}.pdf": documentWithWatermark,
                "raw/${basename}.json": JsonOutput.toJson(data).bytes
            ]
        ) >> new byte[0]
        1 * nexus.storeArtifact(*_) >> nexusUri
    }

    def "create document with documentTypeEmbedded"() {
        given:
        // Test Parameters
        def documentType = "myDocumentType"
        def version = project.buildParams.version
        def repo = project.repositories.first()
        def data = [ a: 1, b: 2, c: 3 ]
        def documentTypeEmbedded = "myEmbeddedDocumentType"

        // Argument Constraints
        def basename = "${documentTypeEmbedded}-${project.key}-${repo.id}-${version}-${steps.env.BUILD_ID}"

        // Stubbed Method Responses
        def document = "PDF".bytes
        def nexusUri = new URI("http://nexus")

        when:
        def result = usecase.createDocument(documentType, repo, data, [:], null, documentTypeEmbedded)

        then:
        1 * docGen.createDocument(*_) >> document

        then:
        1 * usecase.getDocumentBasename(documentTypeEmbedded, version, steps.env.BUILD_ID, repo)

        then:
        1 * util.createZipArtifact(
            "${basename}.zip",
            [
                "${basename}.pdf": document,
                "raw/${basename}.json": JsonOutput.toJson(data).bytes
            ]
        ) >> new byte[0]

        then:
        1 * nexus.storeArtifact(_, _, "${basename}.zip", *_) >> nexusUri
    }

    def "create overall document"() {
        given:
        // Test Parameters
        def coverType = "myCoverType"
        def documentType = "myDocumentType"
        def metadata = [:]

        when:
        usecase.createOverallDocument(coverType, documentType, metadata)

        then:
        1 * usecase.createDocument(coverType, null, _, [:], _, documentType, null)
    }

    def "create overall document with watermark"() {
        given:
        // Test Parameters
        def coverType = "myCoverType"
        def documentType = "myDocumentType"
        def metadata = [:]
        def watermarkText = "Watermark"

        when:
        usecase.createOverallDocument(coverType, documentType, metadata, null, watermarkText)

        then:
        1 * usecase.createDocument(coverType, null, _, [:], _, documentType, watermarkText)
    }

    def "create overall document removes previously stored repository-level documents"() {
        given:
        // Test Parameters
        def coverType = "myCoverType"
        def documentType = "myDocumentType"
        project.repositories.first().data.documents[documentType] = "myDocument".bytes
        def metadata = [:]

        when:
        usecase.createOverallDocument(coverType, documentType, metadata)

        then:
        1 * usecase.createDocument(coverType, null, _, [:], _, documentType, null)

        then:
        project.repositories.first().data.documents[documentType] == null
    }

    def "get document basename"() {
        given:
        // Test Parameters
        def documentType = "myDocumentType"
        def version = project.buildParams.version
        def build = "0815"
        def repo = null

        when:
        def result = usecase.getDocumentBasename(documentType, version, build, repo)

        then:
        result == "${documentType}-${project.key}-${version}-${build}"
    }

    def "get document basename with repo"() {
        given:
        // Test Parameters
        def documentType = "myDocumentType"
        def version = project.buildParams.version
        def build = "0815"
        def repo = project.repositories.first()

        when:
        def result = usecase.getDocumentBasename(documentType, version, build, repo)

        then:
        result == "${documentType}-${project.key}-${repo.id}-${version}-${build}"
    }
}
