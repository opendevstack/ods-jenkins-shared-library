package org.ods.usecase

import groovy.json.JsonOutput

import java.nio.file.Files

import org.ods.service.DocGenService
import org.ods.service.NexusService
import org.ods.util.MROPipelineUtil
import org.ods.util.PDFUtil

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class DocGenUseCaseSpec extends SpecHelper {

    class DocGenUseCaseImpl extends DocGenUseCase {
        DocGenUseCaseImpl(PipelineSteps steps, MROPipelineUtil util, DocGenService docGen, NexusService nexus, PDFUtil pdf) {
            super(steps, util, docGen, nexus, pdf)
        }

        List<String> getSupportedDocuments() {}
    }

    def "create document"() {
        given:
        def buildParams = createBuildParams()

        def steps = Spy(PipelineSteps)
        steps.env.BUILD_ID = "0815"

        def util = Mock(MROPipelineUtil)
        def docGen = Mock(DocGenService)
        def nexus = Mock(NexusService)
        def usecase = Spy(new DocGenUseCaseImpl(
            steps,
            util,
            docGen,
            nexus,
            Mock(PDFUtil)
        ))

        // Test Parameters
        def logFile1 = Files.createTempFile("raw", ".log").toFile() << "Log File 1"
        def logFile2 = Files.createTempFile("raw", ".log").toFile() << "Log File 2"

        def documentType = "myDocumentType"
        def version = buildParams.version
        def project = createProject()
        def repo = project.repositories.first()
        def data = [ a: 1, b: 2, c: 3 ]
        def files = [
            "raw/${logFile1.name}": logFile1.bytes,
            "raw/${logFile2.name}": logFile2.bytes
        ]

        // Argument Constraints
        def basename = "${documentType}-${project.id}-${repo.id}-${version}-${steps.env.BUILD_ID}"

        // Stubbed Method Responses
        def document = "PDF".bytes
        def archive = "Archive".bytes
        def nexusUri = new URI("http://nexus")

        when:
        def result = usecase.createDocument(documentType, project, repo, data, files)

        then:
        1 * util.getBuildParams() >> buildParams

        then:
        1 * docGen.createDocument(documentType, "0.1", data) >> document

        then:
        1 * usecase.getDocumentBasename(documentType, buildParams.version, steps.env.BUILD_ID, project, repo)

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
            "${project.id.toLowerCase()}-${version}",
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
        def buildParams = createBuildParams()

        def steps = Spy(PipelineSteps)
        steps.env.BUILD_ID = "0815"

        def util = Mock(MROPipelineUtil)
        def docGen = Mock(DocGenService)
        def nexus = Mock(NexusService)
        def usecase = Spy(new DocGenUseCaseImpl(
            steps,
            util,
            docGen,
            nexus,
            Mock(PDFUtil)
        ))

        // Test Parameters
        def documentType = "myDocumentType"
        def version = buildParams.version
        def project = createProject()
        def repo = null
        def data = [ a: 1, b: 2, c: 3 ]

        // Argument Constraints
        def basename = "${documentType}-${project.id}-${version}-${steps.env.BUILD_ID}"

        // Stubbed Method Responses
        def document = "PDF".bytes
        def nexusUri = new URI("http://nexus")

        when:
        def result = usecase.createDocument(documentType, project, repo, data)

        then:
        1 * util.getBuildParams() >> buildParams

        then:
        1 * docGen.createDocument(*_) >> document

        then:
        1 * usecase.getDocumentBasename(documentType, buildParams.version, steps.env.BUILD_ID, project, repo)

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
        def buildParams = createBuildParams()

        def steps = Spy(PipelineSteps)
        steps.env.BUILD_ID = "0815"

        def util = Mock(MROPipelineUtil)
        def docGen = Mock(DocGenService)
        def nexus = Mock(NexusService)
        def usecase = Spy(new DocGenUseCaseImpl(
            steps,
            util,
            docGen,
            nexus,
            Mock(PDFUtil)
        ))

        // Test Parameters
        def documentType = "myDocumentType"
        def version = buildParams.version
        def project = createProject()
        def repo = project.repositories.first()
        def data = [ a: 1, b: 2, c: 3 ]
        def modifier = { document ->
            return (new String(document) + "-modified").bytes
        }

        // Argument Constraints
        def basename = "${documentType}-${project.id}-${repo.id}-${version}-${steps.env.BUILD_ID}"

        // Stubbed Method Responses
        def document = "PDF".bytes
        def nexusUri = new URI("http://nexus")

        when:
        def result = usecase.createDocument(documentType, project, repo, data, [:], modifier)

        then:
        1 * util.getBuildParams() >> buildParams

        then:
        1 * docGen.createDocument(*_) >> document

        then:
        1 * usecase.getDocumentBasename(documentType, buildParams.version, steps.env.BUILD_ID, project, repo)

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
        def buildParams = createBuildParams()

        def steps = Spy(PipelineSteps)
        steps.env.BUILD_ID = "0815"

        def util = Mock(MROPipelineUtil)
        def docGen = Mock(DocGenService)
        def nexus = Mock(NexusService)
        def pdf = Spy(new PDFUtil())
        def usecase = Spy(new DocGenUseCaseImpl(
            steps,
            util,
            docGen,
            nexus,
            pdf
        ))

        def pdfUtil = new PDFUtil()

        // Test Parameters
        def documentType = "myDocumentType"
        def version = buildParams.version
        def project = createProject()
        def repo = project.repositories.first()
        def data = [ a: 1, b: 2, c: 3 ]
        def watermarkText = "Watermark"

        // Argument Constraints
        def basename = "${documentType}-${project.id}-${repo.id}-${version}-${steps.env.BUILD_ID}"

        // Stubbed Method Responses
        def document = getResource("Test-1.pdf").bytes
        def nexusUri = new URI("http://nexus")
        def documentWithWatermark = pdfUtil.addWatermarkText(document, watermarkText)

        when:
        usecase.createDocument(documentType, project, repo, data, [:], null, null, watermarkText)

        then:
        1 * docGen.createDocument(*_) >> document
        1 * pdf.addWatermarkText(document, watermarkText)
        1 * util.getBuildParams() >> buildParams
        1 * usecase.getDocumentBasename(documentType, buildParams.version, steps.env.BUILD_ID, project, repo)
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
        def buildParams = createBuildParams()

        def steps = Spy(PipelineSteps)
        steps.env.BUILD_ID = "0815"

        def util = Mock(MROPipelineUtil)
        def docGen = Mock(DocGenService)
        def nexus = Mock(NexusService)
        def usecase = Spy(new DocGenUseCaseImpl(
            steps,
            util,
            docGen,
            nexus,
            Mock(PDFUtil)
        ))

        // Test Parameters
        def documentType = "myDocumentType"
        def version = buildParams.version
        def project = createProject()
        def repo = project.repositories.first()
        def data = [ a: 1, b: 2, c: 3 ]
        def documentTypeEmbedded = "myEmbeddedDocumentType"

        // Argument Constraints
        def basename = "${documentTypeEmbedded}-${project.id}-${repo.id}-${version}-${steps.env.BUILD_ID}"

        // Stubbed Method Responses
        def document = "PDF".bytes
        def nexusUri = new URI("http://nexus")

        when:
        def result = usecase.createDocument(documentType, project, repo, data, [:], null, documentTypeEmbedded)

        then:
        1 * util.getBuildParams() >> buildParams

        then:
        1 * docGen.createDocument(*_) >> document

        then:
        1 * usecase.getDocumentBasename(documentTypeEmbedded, buildParams.version, steps.env.BUILD_ID, project, repo)

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
        def buildParams = createBuildParams()

        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def docGen = Mock(DocGenService)
        def nexus = Mock(NexusService)
        def usecase = Spy(new DocGenUseCaseImpl(
            steps,
            util,
            docGen,
            nexus,
            Mock(PDFUtil)
        ))

        // Test Parameters
        def coverType = "myCoverType"
        def documentType = "myDocumentType"
        def metadata = [:]
        def project = createProject()

        when:
        usecase.createOverallDocument(coverType, documentType, metadata, project)

        then:
        1 * usecase.createDocument(coverType, project, null, _, [:], _, documentType, null)
        _ * util.getBuildParams() >> buildParams
    }

    def "create overall document with watermark"() {
        def buildParams = createBuildParams()

        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def docGen = Mock(DocGenService)
        def nexus = Mock(NexusService)
        def usecase = Spy(new DocGenUseCaseImpl(
            steps,
            util,
            docGen,
            nexus,
            Mock(PDFUtil)
        ))

        // Test Parameters
        def coverType = "myCoverType"
        def documentType = "myDocumentType"
        def metadata = [:]
        def project = createProject()
        def watermarkText = "Watermark"

        when:
        usecase.createOverallDocument(coverType, documentType, metadata, project, null, watermarkText)

        then:
        1 * usecase.createDocument(coverType, project, null, _, [:], _, documentType, watermarkText)
        _ * util.getBuildParams() >> buildParams
    }

    def "create overall document removes previously stored repository-level documents"() {
        def buildParams = createBuildParams()

        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def docGen = Mock(DocGenService)
        def nexus = Mock(NexusService)
        def usecase = Spy(new DocGenUseCaseImpl(
            steps,
            util,
            docGen,
            nexus,
            Mock(PDFUtil)
        ))

        // Test Parameters
        def coverType = "myCoverType"
        def documentType = "myDocumentType"
        def metadata = [:]
        def project = createProject()
        project.repositories.first().data.documents[documentType] = "myDocument".bytes

        when:
        usecase.createOverallDocument(coverType, documentType, metadata, project)

        then:
        1 * usecase.createDocument(coverType, project, null, _, [:], _, documentType, null)
        _ * util.getBuildParams() >> buildParams

        then:
        project.repositories.first().data.documents[documentType] == null
    }

    def "get document basename"() {
        def buildParams = createBuildParams()

        def steps = Spy(PipelineSteps)
        steps.env.BUILD_ID = "0815"

        def util = Mock(MROPipelineUtil)
        def docGen = Mock(DocGenService)
        def nexus = Mock(NexusService)
        def usecase = Spy(new DocGenUseCaseImpl(
            steps,
            util,
            docGen,
            nexus,
            Mock(PDFUtil)
        ))

        // Test Parameters
        def documentType = "myDocumentType"
        def version = buildParams.version
        def build = "0815"
        def project = createProject()
        def repo = null

        when:
        def result = usecase.getDocumentBasename(documentType, version, build, project, repo)

        then:
        result == "${documentType}-${project.id}-${version}-${build}"
    }

    def "get document basename with repo"() {
        def buildParams = createBuildParams()

        def steps = Spy(PipelineSteps)
        steps.env.BUILD_ID = "0815"

        def util = Mock(MROPipelineUtil)
        def docGen = Mock(DocGenService)
        def nexus = Mock(NexusService)
        def usecase = Spy(new DocGenUseCaseImpl(
            steps,
            util,
            docGen,
            nexus,
            Mock(PDFUtil)
        ))

        // Test Parameters
        def documentType = "myDocumentType"
        def version = buildParams.version
        def build = "0815"
        def project = createProject()
        def repo = project.repositories.first()

        when:
        def result = usecase.getDocumentBasename(documentType, version, build, project, repo)

        then:
        result == "${documentType}-${project.id}-${repo.id}-${version}-${build}"
    }
}
