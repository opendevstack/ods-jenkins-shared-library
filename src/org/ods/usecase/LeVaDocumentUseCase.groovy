package org.ods.usecase

import com.cloudbees.groovy.cps.NonCPS

import groovy.json.JsonOutput

import java.time.LocalDateTime

import org.ods.service.DocGenService
import org.ods.service.JiraService
import org.ods.service.NexusService
import org.ods.util.IPipelineSteps
import org.ods.util.PipelineUtil

class LeVaDocumentUseCase {

    private static final String DOCUMENT_TYPE_DTR = "DTR"
    private static final String DOCUMENT_TYPE_TIR = "TIR"

    private static Map DOCUMENT_TYPE_NAMES = [
        DOCUMENT_TYPE_DTR: "Development Test Report",
        DOCUMENT_TYPE_TIR: "Technical Installation Report"
    ]

    private DocGenService docGen
    private JiraUseCase jira
    private NexusService nexus
    private IPipelineSteps steps
    private PipelineUtil util

    LeVaDocumentUseCase(IPipelineSteps steps, DocGenService docGen, JiraUseCase jira, NexusService nexus, PipelineUtil util) {
        this.steps = steps
        this.docGen = docGen
        this.jira = jira
        this.nexus = nexus
        this.util = util
    }

    private static String createDocument(Map deps, String type, String version, Map project, Map repo, Map data, List<File> rawFiles) {
        // Create a PDF document via the DocGen service
        def document = deps.docGen.createDocument(type, '0.1', data)

        // Create an archive with the document and raw data
        def archive = deps.util.createZipArtifact(
            "${type}-${repo.id}-${version}-${deps.steps.env.BUILD_ID}.zip",
            [
                "report.pdf": document,
                "raw/report.json": JsonOutput.toJson(data).getBytes()
            ] << rawFiles.collectEntries { file ->
                [ "raw/${file.getName()}", file.getBytes() ]
            }
        )

        // Store the archive as an artifact in Nexus
        def uri = deps.nexus.storeArtifact(
            project.services.nexus.repository.name,
            "${project.id.toLowerCase()}-${version}",
            "${type}-${repo.id}-${version}.zip",
            archive,
            "application/zip"
        )

        deps.jira.notifyLeVaDocumentIssue(project.id, type, "A new ${DOCUMENT_TYPE_NAMES[type]} has been generated and is available at: ${uri}.")

        return uri.toString()
    }

    String createDTR(String version, Map project, Map repo, Map testResults, List testReportFiles) {
        def documentType = DOCUMENT_TYPE_DTR

        def data = [
            metadata: [
                name: "${project.name}: ${repo.id}",
                description: project.description,
                version: version,
                date_created: LocalDateTime.now().toString(),
                type: DOCUMENT_TYPE_NAMES[documentType]
            ],
            data: [
                testsuites: testResults
            ]
        ]

        return createDocument(
            [steps: this.steps, docGen: this.docGen, jira: this.jira, nexus: this.nexus, util: this.util],
            documentType, version, project, repo, data, testReportFiles
        )
    }

    String createTIR(String version, Map project, Map repo) {
        def documentType = DOCUMENT_TYPE_TIR

        def data = [
            metadata: [
                name: "${project.name}: ${repo.id}",
                description: project.description,
                version: version,
                date_created: LocalDateTime.now().toString(),
                type: DOCUMENT_TYPE_NAMES[documentType]
            ],
            data: [:]
        ]

        return createDocument(
            [steps: this.steps, docGen: this.docGen, jira: this.jira, nexus: this.nexus, util: this.util],
            documentType, version, project, repo, data, []
        )
    }
}
