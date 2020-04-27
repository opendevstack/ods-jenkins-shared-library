package org.ods.orchestration.usecase

import groovy.json.JsonOutput

import org.ods.orchestration.service.DocGenService
import org.ods.orchestration.service.NexusService
import org.ods.orchestration.util.IPipelineSteps
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PDFUtil
import org.ods.orchestration.util.Project

abstract class DocGenUseCase {

    protected Project project
    protected IPipelineSteps steps
    protected MROPipelineUtil util
    protected DocGenService docGen
    protected NexusService nexus
    protected PDFUtil pdf

    DocGenUseCase(Project project, IPipelineSteps steps, MROPipelineUtil util, DocGenService docGen, NexusService nexus, PDFUtil pdf) {
        this.project = project
        this.steps = steps
        this.util = util
        this.docGen = docGen
        this.nexus = nexus
        this.pdf = pdf
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

        // Create an archive with the document and raw data
        def archive = this.util.createZipArtifact(
            "${basename}.zip",
            [
                "${basename}.pdf": document,
                "raw/${basename}.json": JsonOutput.toJson(data).getBytes()
            ] << files.collectEntries { path, contents ->
                [ path, contents ]
            }, isArchivalRelevant(documentType)
        )

        // Store the archive as an artifact in Nexus
        def uri = this.nexus.storeArtifact(
            this.project.services.nexus.repository.name,
            "${this.project.key.toLowerCase()}-${this.project.buildParams.version}",
            "${basename}.zip",
            archive,
            "application/zip"
        )

        def message = "Document ${documentType}"
        if (repo) message += " for ${repo.id}"
        message += " uploaded @ ${uri.toString()}"
        this.steps.echo message
        return uri.toString()
    }

    String createOverallDocument(String coverType, String documentType, Map metadata,Closure visitor = null, String watermarkText = null) {
        def documents = []
        def sections = []

        this.project.repositories.each { repo ->
            def document = repo.data.documents[documentType]
            if (document) {
                documents << document

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
