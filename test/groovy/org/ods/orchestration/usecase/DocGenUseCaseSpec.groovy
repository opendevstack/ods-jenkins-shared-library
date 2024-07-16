package org.ods.orchestration.usecase

import groovy.json.JsonOutput

import java.nio.file.Files

import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.orchestration.service.*
import org.ods.orchestration.util.*
import org.ods.util.IPipelineSteps

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class DocGenUseCaseSpec extends SpecHelper {

    class DocGenUseCaseImpl extends DocGenUseCase {
        DocGenUseCaseImpl(Project project, IPipelineSteps steps, MROPipelineUtil util, DocGenService docGen, NexusService nexus, PDFUtil pdf, JenkinsService jenkins) {
            super(project, steps, util, docGen, nexus, pdf, jenkins)
        }

        List<String> getSupportedDocuments() {}

        String getDocumentTemplatesVersion() {
            return "0.1"
        }

        Map getFiletypeForDocumentType (String documentType) {
            return [storage: 'zip', content: 'pdf']
        }

        boolean shouldCreateArtifact (String documentType, Map repo) {
            return true
        }
    }

    DocGenService docGen
    NexusService nexus
    PDFUtil pdf
    Project project
    IPipelineSteps steps
    DocGenUseCase usecase
    MROPipelineUtil util
    JenkinsService jenkins

    def setup() {
        steps = Spy(util.PipelineSteps)
        steps.env.BUILD_ID = "0815"

        project = createProject()
        util = Mock(MROPipelineUtil)
        docGen = Mock(DocGenService)
        nexus = Mock(NexusService)
        pdf = Mock(PDFUtil)
        jenkins = Mock(JenkinsService)
        usecase = Spy(new DocGenUseCaseImpl(project, steps, util, docGen, nexus, pdf, jenkins))
        docGen.healthCheck() >> 200
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
        5 * docGen.healthCheck() >>> [201, 300, 400, 500, 200]

        then:
        1 * usecase.getDocumentBasename(documentType, version, steps.env.BUILD_ID,repo)

        then:
        0 * util.createAndStashArtifact(*_)

        then:
        1 * util.createZipArtifact(
            "${basename}.zip",
            [
                "${basename}.pdf": document,
                "raw/${basename}.json": JsonOutput.toJson(data).bytes,
                "raw/${logFile1.name}": logFile1.bytes,
                "raw/${logFile2.name}": logFile2.bytes
            ], true
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

    def "create document when docgen not ready"() {
        when:
        usecase.createDocument(null, null, null)

        then:
        5 * docGen.healthCheck() >>> [201, 300, 400, 100, 500]
        ServiceNotReadyException e = thrown()
        e.getStatus() == 500
    }

    def "create document and stash"() {
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
        1 * usecase.shouldCreateArtifact(documentType, repo) >> false

        then:
        1 * util.createZipArtifact(
            "${basename}.zip",
            [
                "${basename}.pdf": document,
                "raw/${basename}.json": JsonOutput.toJson(data).bytes,
                "raw/${logFile1.name}": logFile1.bytes,
                "raw/${logFile2.name}": logFile2.bytes
            ], false
        ) >> archive

        then:
        1 * util.createAndStashArtifact(*_)

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
            ], true
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
            ], true
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
        def document = new FixtureHelper().getResource("Test-1.pdf").bytes
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
            ], true
        ) >> new byte[0]
        1 * nexus.storeArtifact(*_) >> nexusUri
    }

    def "create document with templateName"() {
        given:
        // Test Parameters
        def documentType = "myDocumentType"
        def version = project.buildParams.version
        def repo = project.repositories.first()
        def data = [ a: 1, b: 2, c: 3 ]
        def templateName = "myTemplateName"

        // Argument Constraints
        def basename = "${documentType}-${project.key}-${repo.id}-${version}-${steps.env.BUILD_ID}"

        // Stubbed Method Responses
        def document = "PDF".bytes
        def nexusUri = new URI("http://nexus")

        when:
        def result = usecase.createDocument(documentType, repo, data, [:], null, templateName)

        then:
        1 * docGen.createDocument(templateName, _, _) >> document

        then:
        1 * usecase.getDocumentBasename(documentType, version, steps.env.BUILD_ID, repo)

        then:
        1 * util.createZipArtifact(
            "${basename}.zip",
            [
                "${basename}.pdf": document,
                "raw/${basename}.json": JsonOutput.toJson(data).bytes
            ], true
        ) >> new byte[0]

        then:
        1 * nexus.storeArtifact(_, _, "${basename}.zip", *_) >> nexusUri
    }

    def "create overall document"() {
        given:
        // Test Parameters
        def templateName = "myTemplateName"
        def documentType = "myDocumentType"
        def metadata = [:]

        when:
        usecase.createOverallDocument(templateName, documentType, metadata)

        then:
        1 * usecase.createDocument(documentType, null, _, [:], _, templateName, null)
    }

    def "create overall document with watermark"() {
        given:
        // Test Parameters
        def templateName = "myTemplateName"
        def documentType = "myDocumentType"
        def metadata = [:]
        def watermarkText = "Watermark"

        when:
        usecase.createOverallDocument(templateName, documentType, metadata, null, watermarkText)

        then:
        1 * usecase.createDocument(documentType, null, _, [:], _, templateName, watermarkText)
    }

    def "create overall document removes previously stored repository-level documents"() {
        given:
        // Test Parameters
        def templateName = "myTemplateName"
        def documentType = "myDocumentType"
        def docName = "myDocument.pdf"
        project.repositories.first().data.documents[documentType] = docName

        def path = "${this.steps.env.WORKSPACE}/reports/${project.repositories.first().id}"
        new File ("${path}").mkdirs()
        new File ("${path}/${docName}").write("test")

        def metadata = [:]

        when:
        usecase.createOverallDocument(templateName, documentType, metadata)

        then:
        1 * usecase.createDocument(documentType, null, _, [:], _, templateName, null)

        then:
        0 * util.createAndStashArtifact(*_)

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

    def "get document basename with repo -missing data"() {
        given:
        // Test Parameters
        def documentType = "myDocumentType"
        def version = project.buildParams.version
        def build = "0815"
        def repo = project.repositories.first()

        when:
        def result = usecase.resurrectAndStashDocument(documentType, repo)

        then:
        result.found == false
    }

    def "resurrect doc from repo - missing build data"() {
        given:
        // Test Parameters
        def documentType = "myDocumentType"
        def version = project.buildParams.version
        def build = "0815"
        def repo = project.repositories.first()

        repo.data.openshift = [:]
        when:
        def result = usecase.resurrectAndStashDocument(documentType, repo)

        then:
        result.found == false
    }

}
