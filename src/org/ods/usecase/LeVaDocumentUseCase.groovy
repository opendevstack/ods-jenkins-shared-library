package org.ods.usecase

import com.cloudbees.groovy.cps.NonCPS

import groovy.json.JsonOutput

import java.net.URI
import java.time.LocalDateTime

import org.apache.commons.io.FilenameUtils
import org.ods.service.DocGenService
import org.ods.service.JenkinsService
import org.ods.service.JiraService
import org.ods.service.LeVaDocumentChaptersFileService
import org.ods.service.NexusService
import org.ods.service.OpenShiftService
import org.ods.util.IPipelineSteps
import org.ods.util.MROPipelineUtil
import org.ods.util.PDFUtil

class LeVaDocumentUseCase {

    class DocumentTypes {
        static final String DTP = "DTP"
        static final String DTR = "DTR"
        static final String SCP = "SCP"
        static final String SCR = "SCR"
        static final String TIP = "TIP"
        static final String TIR = "TIR"

        static final String OVERALL_COVER = "Overall-Cover"
        static final String OVERALL_TIR_COVER = "Overall-TIR-Cover"
    }

    private static Map DOCUMENT_TYPE_NAMES = [
        (DocumentTypes.DTP): "Software Development Testing Plan",
        (DocumentTypes.DTR): "Software Development Testing Report",
        (DocumentTypes.SCP): "Software Development (Coding and Code Review) Plan",
        (DocumentTypes.SCR): "Software Development (Coding and Code Review) Report",
        (DocumentTypes.TIP): "Technical Installation Plan",
        (DocumentTypes.TIR): "Technical Installation Report"
    ]

    private IPipelineSteps steps
    private MROPipelineUtil util
    private DocGenService docGen
    private JenkinsService jenkins
    private JiraUseCase jira
    private LeVaDocumentChaptersFileService levaFiles
    private NexusService nexus
    private OpenShiftService os
    private PDFUtil pdf

    LeVaDocumentUseCase(IPipelineSteps steps, MROPipelineUtil util, DocGenService docGen, JenkinsService jenkins, JiraUseCase jira, LeVaDocumentChaptersFileService levaFiles, NexusService nexus, OpenShiftService os, PDFUtil pdf) {
        this.steps = steps
        this.util = util
        this.docGen = docGen
        this.jenkins = jenkins
        this.jira = jira
        this.levaFiles = levaFiles
        this.nexus = nexus
        this.os = os
        this.pdf = pdf
    }

    private static String computeDocumentFileBaseName(String type, IPipelineSteps steps, Map buildParams, Map project, Map repo = null) {
        def result = project.id

        if (repo) {
            result += "-${repo.id}"
        }

        return "${type}-${result}-${buildParams.version}-${steps.env.BUILD_ID}"
    }

    private Map computeDTRDiscrepancies(List jiraTestIssues) {
        def result = [
            discrepancies: "No discrepancies found.",
            conclusion: [
                summary: "Complete success, no discrepancies",
                statement: "It is determined that all steps of the Development Tests have been successfully executed and signature of this report verifies that the tests have been performed according to the plan. No discrepancies occurred."
            ]
        ]

        def failed = []
        def missing = []

        jiraTestIssues.each { issue ->
            if (!issue.isSuccess && !issue.isMissing) {
                failed << issue.key
            }

            if (!issue.isSuccess && issue.isMissing) {
                missing << issue.key
            }
        }

        if (failed.isEmpty() && !missing.isEmpty()) {
            result.discrepancies = "The following minor discrepancies were found during testing: ${missing.join(", ")}."

            result.conclusion = [
               summary: "Success - minor discrepancies found",
               statement: "Some discrepancies were found as tests were not executed, this may be per design."
            ]
        } else if (!failed.isEmpty()) {
            if (!missing.isEmpty()) {
                failed.addAll(missing)
                failed.sort()
            }

            result.discrepancies = "The following major discrepancies were found during testing: ${(failed).join(", ")}."

            result.conclusion = [
               summary: "No success - major discrepancies found",
               statement: "Some discrepancies occured as tests did fail. It is not recommended to continue!"
            ]
        }

        return result
    }

    private static String createDocument(Map deps, String type, Map project, Map repo, Map data, Map<String, byte[]> files = [:], Closure modifier = null, String typeName = null) {
        def buildParams = deps.util.getBuildParams()

        // Create a PDF document via the DocGen service
        def document = deps.docGen.createDocument(type, '0.1', data)

        // Apply any PDF document modifications, if provided
        if (modifier) {
            document = modifier(document)
        }

        if (deps.util.isTriggeredByChangeManagementProcess()) {
            if (buildParams.targetEnvironment == MROPipelineUtil.PipelineEnvs.DEV) {
                document = deps.pdf.addWatermarkText(document, "Developer Preview")
            }
        }

        def baseName = computeDocumentFileBaseName(typeName ?: type, deps.steps, buildParams, project, repo)

        // Create an archive with the document and raw data
        def archive = deps.util.createZipArtifact(
            "${baseName}.zip",
            [
                "${baseName}.pdf": document,
                "raw/${baseName}.json": JsonOutput.toJson(data).getBytes()
            ] << files.collectEntries { path, contents ->
                [ path, contents ]
            }
        )

        // Store the archive as an artifact in Nexus
        def uri = deps.nexus.storeArtifact(
            project.services.nexus.repository.name,
            "${project.id.toLowerCase()}-${buildParams.version}",
            "${baseName}.zip",
            archive,
            "application/zip"
        )

        deps.jira.notifyLeVaDocumentTrackingIssue(project.id, typeName ?: type, "A new ${DOCUMENT_TYPE_NAMES[type]} has been generated and is available at: ${uri}.")

        return uri.toString()
    }

    private String createOverallDocument(String coverType, String documentType, Map project, Closure visitor = null) {
        def documents = []
        def sections = []

        project.repositories.each { repo ->
            documents << repo.data.documents[documentType]
            sections << [
                heading: repo.id
            ]
        }

        def data = [
            metadata: this.getDocumentMetadata(DOCUMENT_TYPE_NAMES[documentType], project),
            data: [
                sections: sections
            ]
        ]

        if (visitor) {
            visitor(data.data)
        }

        def modifier = { document ->
            documents.add(0, document)
            return this.pdf.merge(documents)
        }

        def deps = [steps: this.steps, docGen: this.docGen, jira: this.jira, nexus: this.nexus, pdf: this.pdf, util: this.util]
        return createDocument(deps, coverType, project, null, data, [:], modifier, documentType)
    }

    String createDTP(Map project) {
        def documentType = DocumentTypes.DTP

        def sections = this.jira.getDocumentChapterData(project.id, documentType)
        if (!sections) {
            sections = this.levaFiles.getDocumentChapterData(documentType)
        }

        def data = [
            metadata: this.getDocumentMetadata(DOCUMENT_TYPE_NAMES[documentType], project),
            data: [
                project: project,
                sections: sections,
                tests: this.jira.getAutomatedTestIssues(project.id).collectEntries { issue ->
                    [
                        issue.key,
                        [
                            key: issue.key,
                            description: issue.fields.description ?: "",
                            isRelatedTo: issue.isRelatedTo ? issue.isRelatedTo.first().key : "N/A"
                        ]
                    ]
                }
            ]
        ]

        def modifier = { document ->
            project.data.documents[documentType] = document
            return document
        }

        return createDocument(
            [steps: this.steps, docGen: this.docGen, jira: this.jira, nexus: this.nexus, pdf: this.pdf, util: this.util],
            documentType, project, null, data, [:], modifier, null
        )
    }

    String createDTR(Map project, Map repo, Map testResults, List<File> testReportFiles) {
        def documentType = DocumentTypes.DTR

        def sections = this.jira.getDocumentChapterData(project.id, documentType)
        if (!sections) {
            sections = this.levaFiles.getDocumentChapterData(documentType)
        }

        def jiraTestIssues = this.jira.getAutomatedTestIssues(project.id, "Technology_${repo.id}")

        def matchedHandler = { result ->
            result.each { issue, testcase ->
                issue.isSuccess = !(testcase.error || testcase.failure || testcase.skipped)
                issue.isMissing = false
            }
        }

        def unmatchedHandler = { result ->
            result.each { issue ->
                issue.isSuccess = false
                issue.isMissing = true
            }
        }

        this.jira.matchJiraTestIssuesAgainstTestResults(jiraTestIssues, testResults, matchedHandler, unmatchedHandler)

        def discrepancies = this.computeDTRDiscrepancies(jiraTestIssues)

        def data = [
            metadata: this.getDocumentMetadata(DOCUMENT_TYPE_NAMES[documentType], project, repo),
            data: [
                repo: repo,
                sections: sections,
                tests: jiraTestIssues.collectEntries { issue ->
                    [
                        issue.key,
                        [
                            key: issue.key,
                            description: issue.fields.description ?: "",
                            isRelatedTo: issue.isRelatedTo ? issue.isRelatedTo.first().key : "N/A",
                            success: issue.isSuccess ? "Y" : "N",
                            remarks: issue.isMissing ? "not executed" : ""
                        ]
                    ]
                },
                testfiles: testReportFiles.collect { file ->
                    [ name: file.getName(), path: file.getPath() ]
                },
                testsuites: testResults,
                discrepancies: discrepancies.discrepancies,
                conclusion: [
                    summary: discrepancies.conclusion.summary,
                    statement : discrepancies.conclusion.statement
                ]
            ]
        ]

        def files = testReportFiles.collectEntries { file ->
            [ "raw/${file.getName()}", file.getBytes() ]
        }

        def modifier = { document ->
            repo.data.documents[documentType] = document
            return document
        }

        return createDocument(
            [steps: this.steps, docGen: this.docGen, jira: this.jira, nexus: this.nexus, pdf: this.pdf, util: this.util],
            documentType, project, repo, data, files, modifier, null
        )
    }

    String createOverallDTR(Map project) {
        return createOverallDocument(DocumentTypes.OVERALL_COVER, DocumentTypes.DTR, project)
    }

    String createSCP(Map project) {
        def documentType = DocumentTypes.SCP

        def sections = this.jira.getDocumentChapterData(project.id, documentType)
        if (!sections) {
            sections = this.levaFiles.getDocumentChapterData(documentType)
        }

        def data = [
            metadata: this.getDocumentMetadata(DOCUMENT_TYPE_NAMES[documentType], project),
            data: [
                project: project,
                sections: sections
            ]
        ]

        def modifier = { document ->
            project.data.documents[documentType] = document
            return document
        }

        return createDocument(
            [steps: this.steps, docGen: this.docGen, jira: this.jira, nexus: this.nexus, pdf: this.pdf, util: this.util],
            documentType, project, null, data, [:], modifier, null
        )
    }

    String createSCR(Map project, Map repo, File sonarQubeWordDoc = null) {
        def documentType = DocumentTypes.SCR

        def sections = this.jira.getDocumentChapterData(project.id, documentType)
        if (!sections) {
            sections = this.levaFiles.getDocumentChapterData(documentType)
        }

        def data = [
            metadata: this.getDocumentMetadata(DOCUMENT_TYPE_NAMES[documentType], project, repo),
            data: [
                sections: sections
            ]
        ]

        def files = [:]
        if (sonarQubeWordDoc) {
            /*
            // TODO: conversion of a SonarQube report results in an ambiguous NPE.
            // Research did not reveal any meaningful results. Further, Apache POI
            // depends on Commons Compress, but unfortunately Jenkins puts an older
            // version onto the classpath which results in an error. Therefore, iff
            // the NPE can be fixed, this code would need to run outside of Jenkins,
            // such as the DocGen service.

            def sonarQubePDFDoc = this.pdf.convertFromWordDoc(sonarQubeWordDoc)
            modifier = { document ->
                // Merge the current document with the SonarQube report
                return this.pdf.merge([ document, sonarQubePDFDoc ])
            }

            // As our plan B below, we instead add the SonarQube report into the
            // SCR's .zip archive.
            */
            def name = computeDocumentFileBaseName("SCRR", this.steps, this.util.getBuildParams(), project, repo)
            files << [ "${name}.${FilenameUtils.getExtension(sonarQubeWordDoc.getName())}": sonarQubeWordDoc.getBytes() ]
        }

        def modifier = { document ->
            repo.data.documents[documentType] = document
            return document
        }

        return createDocument(
            [steps: this.steps, docGen: this.docGen, jira: this.jira, nexus: this.nexus, pdf: this.pdf, util: this.util],
            documentType, project, repo, data, files, modifier, null
        )
    }

    String createOverallSCR(Map project) {
        return createOverallDocument(DocumentTypes.OVERALL_COVER, DocumentTypes.SCR, project)
    }

    String createTIP(Map project) {
        def documentType = DocumentTypes.TIP

        def sections = this.jira.getDocumentChapterData(project.id, documentType)
        if (!sections) {
            sections = this.levaFiles.getDocumentChapterData(documentType)
        }

        def data = [
            metadata: this.getDocumentMetadata(DOCUMENT_TYPE_NAMES[documentType], project),
            data: [
                project: project,
                repos: project.repositories,
                sections: sections
            ]
        ]

        def modifier = { document ->
            project.data.documents[documentType] = document
            return document
        }

        return createDocument(
            [steps: this.steps, docGen: this.docGen, jira: this.jira, nexus: this.nexus, pdf: this.pdf, util: this.util],
            documentType, project, null, data, [:], modifier, null
        )
    }

    String createTIR(Map project, Map repo) {
        def documentType = DocumentTypes.TIR

        def pods = this.os.getPodDataForComponent(repo.id)

        def sections = this.jira.getDocumentChapterData(project.id, documentType)
        if (!sections) {
            sections = this.levaFiles.getDocumentChapterData(documentType)
        }

        def data = [
            metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType], project, repo),
            openShiftData: [
                ocpBuildId           : repo?.data.odsBuildArtifacts?."OCP Build Id" ?: "N/A",
                ocpDockerImage       : repo?.data.odsBuildArtifacts?."OCP Docker image" ?: "N/A",
                ocpDeploymentId      : repo?.data.odsBuildArtifacts?."OCP Deployment Id" ?: "N/A",
                podName              : pods?.items[0]?.metadata?.name ?: "N/A",
                podNamespace         : pods?.items[0]?.metadata?.namespace ?: "N/A",
                podCreationTimestamp : pods?.items[0]?.metadata?.creationTimestamp ?: "N/A",
                podEnvironment       : pods?.items[0]?.metadata?.labels?.env ?: "N/A",
                podNode              : pods?.items[0]?.spec?.nodeName ?: "N/A",
                podIp                : pods?.items[0]?.status?.podIP ?: "N/A",
                podStatus            : pods?.items[0]?.status?.phase ?: "N/A"
            ],
            data: [
                project: project,
                repo: repo,
                sections: sections
            ]
        ]

        def modifier = { document ->
            repo.data.documents[documentType] = document
            return document
        }

        return createDocument(
            [steps: this.steps, docGen: this.docGen, jira: this.jira, nexus: this.nexus, pdf: this.pdf, util: this.util],
            documentType, project, repo, data, [:], modifier, null
        )
    }

    String createOverallTIR(Map project) {
        return createOverallDocument(DocumentTypes.OVERALL_TIR_COVER, DocumentTypes.TIR, project) { data ->
            // Append another section for the Jenkins build log
            data.sections << [
                heading: "Jenkins Build Log"
            ]

            // Add Jenkins build log data
            data.jenkinsData = [
                log: this.jenkins.getCurrentBuildLogAsText()
            ]
        }
    }

    private Map getDocumentMetadata(String type, Map project, Map repo = null) {
        def name = project.name
        if (repo) {
            name += ": ${repo.id}"
        }

        return [
            id: "N/A",
            name: name,
            description: project.description,
            type: type,
            version: this.steps.env.RELEASE_PARAM_VERSION,
            date_created: LocalDateTime.now().toString(),
            buildParameter: this.util.getBuildParams(),
            git: repo ? repo.data.git : project.data.git,
            jenkins: [
                buildNumber: this.steps.env.BUILD_NUMBER,
                buildUrl: this.steps.env.BUILD_URL,
                jobName: this.steps.env.JOB_NAME
            ]
        ]
    }
}
