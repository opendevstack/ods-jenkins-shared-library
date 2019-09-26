package org.ods.usecase

import groovy.json.JsonOutput

import java.nio.file.Files

import org.ods.service.DocGenService
import org.ods.service.JiraService
import org.ods.service.NexusService
import org.ods.util.PipelineUtil

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class LeVaDocumentUseCaseSpec extends SpecHelper {

    LeVaDocumentUseCase createUseCase(PipelineSteps steps, DocGenService docGen, JiraService jira, NexusService nexus, PipelineUtil util) {
        return new LeVaDocumentUseCase(steps, docGen, jira, nexus, util)
    }

    def "create document"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jira = Mock(JiraService)
        def nexus = Mock(NexusService)
        def util = Mock(PipelineUtil)

        def logFile1 = Files.createTempFile("raw", ".log").toFile() << "Log File 1"
        def logFile2 = Files.createTempFile("raw", ".log").toFile() << "Log File 2"

        def type = "myType"
        def version = "0.1"
        def project = createProject()
        def repo = project.repositories.first()
        def data = [ a: 1, b: 2, c: 3 ]
        def rawFiles = [logFile1, logFile2]
        def jiraIssueJQLQuery = "myJQLQuery"

        def document = "PDF".bytes
        def archive = "Archive".bytes
        def jiraIssue = createJiraIssue("1", "LeVa Document", "Some very important document.", false)
        def nexusUri = new URI("http://nexus")

        when:
        def result = LeVaDocumentUseCase.createDocument(
            [steps: steps, docGen: docGen, jira: jira, nexus: nexus, util: util],
            type, version, project, repo, data, rawFiles, jiraIssueJQLQuery
        )

        then:
        1 * docGen.createDocument(type, '0.1', data) >> document

        then:
        1 * util.createZipArtifact(
            { "${type}-${repo.id}-${version}-${steps.env.BUILD_ID}.zip" },
            {
                [
                    "report.pdf": document,
                    "raw/report.json": JsonOutput.toJson(data).bytes,
                    "raw/${logFile1.name}": logFile1.bytes,
                    "raw/${logFile2.name}": logFile2.bytes
                ]
            }
        ) >> archive

        then:
        1 * nexus.storeArtifact(
            project.services.nexus.repository.name,
            { "${project.id.toLowerCase()}-${version}" },
            { "${type}-${repo.id}-${version}.zip" },
            archive,
            "application/zip"
        ) >> nexusUri

        then:
        1 * jira.getIssuesForJQLQuery(jiraIssueJQLQuery) >> [jiraIssue]

        then:
        1 * jira.appendCommentToIssue(jiraIssue.key, "A new ${type} has been generated and is available at: ${nexusUri}.")

        then:
        result == nexusUri.toString()

        cleanup:
        logFile1.delete()
        logFile2.delete()
    }

    def "create document with Jira returns != 1 issue"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jira = Mock(JiraService)
        def nexus = Mock(NexusService)
        def util = Mock(PipelineUtil)

        def logFile1 = Files.createTempFile("raw", ".log").toFile() << "Log File 1"
        def logFile2 = Files.createTempFile("raw", ".log").toFile() << "Log File 2"

        def type = "myType"
        def version = "0.1"
        def project = createProject()
        def repo = project.repositories.first()
        def data = [ a: 1, b: 2, c: 3 ]
        def rawFiles = [logFile1, logFile2]
        def jiraIssueJQLQuery = "myJQLQuery"

        def jiraIssue1 = createJiraIssue("1", "LeVa Document 1", "Some very important document.", false)
        def jiraIssue2 = createJiraIssue("2", "LeVa Document 2", "Some very important document.", false)

        when:
        LeVaDocumentUseCase.createDocument(
            [steps: steps, docGen: docGen, jira: jira, nexus: nexus, util: util],
            type, version, project, repo, data, rawFiles, jiraIssueJQLQuery
        )

        then:
        1 * jira.getIssuesForJQLQuery(jiraIssueJQLQuery) >> []

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: Jira query returned 0 issues: '${jiraIssueJQLQuery}'."

        when:
        LeVaDocumentUseCase.createDocument(
            [steps: steps, docGen: docGen, jira: jira, nexus: nexus, util: util],
            type, version, project, repo, data, rawFiles, jiraIssueJQLQuery
        )

        then:
        1 * jira.getIssuesForJQLQuery(jiraIssueJQLQuery) >> [jiraIssue1, jiraIssue2]

        then:
        e = thrown(RuntimeException)
        e.message == "Error: Jira query returned 2 issues: '${jiraIssueJQLQuery}'."

        cleanup:
        logFile1.delete()
        logFile2.delete()
    }

    def "create DTR"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def junit = new JUnitTestReportsUseCase(steps)
        def usecase = createUseCase(
            steps,
            Mock(DocGenService),
            Mock(JiraService),
            Mock(NexusService),
            Mock(PipelineUtil)
        )

        GroovyMock(LeVaDocumentUseCase, global: true)

        def xmlFile = Files.createTempFile("junit", ".xml")
        xmlFile << "<?xml version='1.0' ?>\n" + createJUnitXMLTestResults()

        def version = "0.1"
        def project = createProject()
        def repo = project.repositories.first()
        def testReportFiles = [xmlFile]
        def testReport = junit.parseTestReportFiles(testReportFiles)

        def type = "DTR"
        def jiraIssueJQLQuery = "project = ${project.id} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${type}"

        when:
        usecase.createDTR(version, project, repo, testReport, testReportFiles)

        then:
        1 * LeVaDocumentUseCase.createDocument(
            _, type, version, project, repo, _, testReportFiles, jiraIssueJQLQuery
        )

        cleanup:
        xmlFile.toFile().delete()
    }

    def "create TIR"() {
        given:
        def usecase = createUseCase(
            Spy(util.PipelineSteps),
            Mock(DocGenService),
            Mock(JiraService),
            Mock(NexusService),
            Mock(PipelineUtil)
        )

        GroovyMock(LeVaDocumentUseCase, global: true)

        def version = "0.1"
        def project = createProject()
        def repo = project.repositories.first()

        def type = "TIR"
        def jiraIssueJQLQuery = "project = ${project.id} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${type}"

        when:
        usecase.createTIR(version, project, repo)

        then:
        1 * LeVaDocumentUseCase.createDocument(
            _, type, version, project, repo, _, [], jiraIssueJQLQuery
        )
    }
}
