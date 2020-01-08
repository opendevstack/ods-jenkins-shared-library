package org.ods.usecase

import groovy.json.JsonOutput

import org.ods.service.DocGenService
import org.ods.service.NexusService
import org.ods.util.IPipelineSteps
import org.ods.util.MROPipelineUtil
import org.ods.util.PDFUtil

abstract class DocGenUseCase {

    protected IPipelineSteps steps
    protected MROPipelineUtil util
    protected DocGenService docGen
    protected NexusService nexus
    protected PDFUtil pdf

    DocGenUseCase(IPipelineSteps steps, MROPipelineUtil util, DocGenService docGen, NexusService nexus, PDFUtil pdf) {
        this.steps = steps
        this.util = util
        this.docGen = docGen
        this.nexus = nexus
        this.pdf = pdf
    }

    String createDocument(String documentType, Map project, Map repo, Map data, Map<String, byte[]> files = [:], Closure modifier = null, String documentTypeEmbedded = null) {
        def buildParams = this.util.getBuildParams()

        // Create a PDF document via the DocGen service
        def document = this.docGen.createDocument(documentType, '0.1', data)

        // Apply PDF document modifications, if provided
        if (modifier) {
            document = modifier(document)
        }

        def basename = this.getDocumentBasename(documentTypeEmbedded ?: documentType, buildParams.version, this.steps.env.BUILD_ID, project, repo)

        // Create an archive with the document and raw data
        def archive = this.util.createZipArtifact(
            "${basename}.zip",
            [
                "${basename}.pdf": document,
                "raw/${basename}.json": JsonOutput.toJson(data).getBytes()
            ] << files.collectEntries { path, contents ->
                [ path, contents ]
            }
        )

        // Store the archive as an artifact in Nexus
        def uri = this.nexus.storeArtifact(
            project.services.nexus.repository.name,
            "${project.id.toLowerCase()}-${buildParams.version}",
            "${basename}.zip",
            archive,
            "application/zip"
        )

        return uri.toString()
    }

    String createOverallDocument(String coverType, String documentType, Map metadata, Map project, Closure visitor = null) {
        def documents = []
        def sections = []

        project.repositories.each { repo ->
            def document = repo.data.documents[documentType]
            if (document) {
                documents << document
            }

            sections << [
                heading: repo.id
            ]
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

        def result = this.createDocument(coverType, project, null, data, [:], modifier, documentType)

        // Clean up previously stored documents
        project.repositories.each { repo ->
            repo.data.documents.remove(documentType)
        }

        return result
    }

    String getDocumentBasename(String documentType, String version, String build, Map project, Map repo = null) {
        def result = project.id
        if (repo) {
            result += "-${repo.id}"
        }

        return "${documentType}-${result}-${version}-${build}".toString()
    }

    abstract List<String> getSupportedDocuments()
}
