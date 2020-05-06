package org.ods.orchestration.usecase

import groovy.json.JsonOutput

import org.ods.orchestration.service.DocGenService
import org.ods.orchestration.service.NexusService
import org.ods.orchestration.service.JenkinsService
import org.ods.orchestration.util.IPipelineSteps
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PDFUtil
import org.ods.orchestration.util.Project

@SuppressWarnings(['AbstractClassWithPublicConstructor', 'LineLength', 'ParameterCount', 'GStringAsMapKey'])
abstract class DocGenUseCase {

    protected Project project
    protected IPipelineSteps steps
    protected MROPipelineUtil util
    protected DocGenService docGen
    protected NexusService nexus
    protected PDFUtil pdf
    protected JenkinsService jenkins

    DocGenUseCase(Project project, IPipelineSteps steps, MROPipelineUtil util, DocGenService docGen, NexusService nexus, PDFUtil pdf, JenkinsService jenkins) {
        this.project = project
        this.steps = steps
        this.util = util
        this.docGen = docGen
        this.nexus = nexus
        this.pdf = pdf
        this.jenkins = jenkins
    }

    String createDocument(String documentType, Map repo, Map data, Map<String, byte[]> files = [:], Closure modifier = null, String documentTypeEmbedded = null, String watermarkText = null) {
        // Create a PDF document via the DocGen service
        def document = this.docGen.createDocument(documentType, this.getDocumentTemplatesVersion(), data)

        // Apply PDF document modifications, if provided
        if (modifier) {
            document = modifier(document)
        }

        // Apply PDF document watermark, if provided
        if (watermarkText) {
            document = this.pdf.addWatermarkText(document, watermarkText)
        }

        def basename = this.getDocumentBasename(documentTypeEmbedded ?: documentType, this.project.buildParams.version, this.steps.env.BUILD_ID, repo)

        def pdfName = "${basename}.pdf"
        // Create an archive with the document and raw data
        def artifacts = [
            "${pdfName}": document,
            "raw/${basename}.json": JsonOutput.toJson(data).getBytes()
        ]
        artifacts << files.collectEntries { path, contents ->
            [ path, contents ]
        }
        
        def doArchive = isArchivalRelevant(documentType);
        
        def archive = this.util.createZipArtifact(
            "${basename}.zip",
            artifacts,
            doArchive
        )

        // dtr / tir for single repo
        if (!doArchive) {
            this.util.createAndStashArtifact(pdfName, document)
            repo.data.documents[documentType] = pdfName
        }

        // Store the archive as an artifact in Nexus
        def uri = this.nexus.storeArtifact(
            this.project.services.nexus.repository.name,
            "${this.project.key.toLowerCase()}-${this.project.buildParams.version}",
            "${basename}.zip",
            archive,
            "application/zip"
        )

        def message = "Document ${documentType}"
        if (repo) {
            message += " for ${repo.id}"
        }
        message += " uploaded @ ${uri.toString()}"
        this.steps.echo message
        return uri.toString()
    }

    String createOverallDocument(String coverType, String documentType, Map metadata,Closure visitor = null, String watermarkText = null) {
        def documents = []
        def sections = []

        this.project.repositories.each { repo ->
            def documentName = repo.data.documents[documentType]

            if (documentName) {
                def path = "${this.steps.env.WORKSPACE}/reports/${repo.id}"
                jenkins.unstashFilesIntoPath(documentName, path, documentType)
                documents << new File("${path}/${documentName}").readBytes()

                sections << [
                    heading: "${documentType} for component: ${repo.id} (merged)"
                ]
            }
        }

        def data = [
            metadata: metadata,
            data: [
                sections: sections
            ]
        ]

        // Apply any data transformations, if provided
        if (visitor) {
            visitor(data.data)
        }

        // Create a cover page and merge all documents into one
        def modifier = { document ->
            documents.add(0, document)
            return this.pdf.merge(documents)
        }

        def result = this.createDocument(coverType, null, data, [:], modifier, documentType, watermarkText)

        // Clean up previously stored documents
        this.project.repositories.each { repo ->
            repo.data.documents.remove(documentType)
        }

        return result
    }

    String getDocumentBasename(String documentType, String version, String build, Map repo = null) {
        def result = this.project.key
        if (repo) {
            result += "-${repo.id}"
        }

        return "${documentType}-${result}-${version}-${build}".toString()
    }

    abstract String getDocumentTemplatesVersion()

    abstract List<String> getSupportedDocuments()
    
    abstract boolean isArchivalRelevant (String documentType)
}
