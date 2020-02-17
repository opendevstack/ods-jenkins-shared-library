package org.ods.usecase

import groovy.json.JsonOutput

import java.nio.file.Files

import org.ods.service.DocGenService
import org.ods.service.JenkinsService
import org.ods.service.JiraService
import org.ods.service.LeVADocumentChaptersFileService
import org.ods.service.NexusService
import org.ods.service.OpenShiftService
import org.ods.usecase.DocGenUseCase
import org.ods.usecase.JiraUseCase
import org.ods.usecase.SonarQubeUseCase
import org.ods.util.MROPipelineUtil
import org.ods.util.PDFUtil
import org.ods.util.Project

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class LeVADocumentUseCaseSpec extends SpecHelper {

    Project project
    PipelineSteps steps
    MROPipelineUtil util
    DocGenService docGen
    JenkinsService jenkins
    JiraUseCase jiraUseCase
    LeVADocumentChaptersFileService levaFiles
    NexusService nexus
    OpenShiftService os
    PDFUtil pdf
    SonarQubeUseCase sq
    LeVADocumentUseCase usecase

    def setup() {
        project = Spy(createProject())
        steps = Spy(PipelineSteps)
        util = Mock(MROPipelineUtil)
        docGen = Mock(DocGenService)
        jenkins = Mock(JenkinsService)
        jiraUseCase = Mock(JiraUseCase)
        levaFiles = Mock(LeVADocumentChaptersFileService)
        nexus = Mock(NexusService)
        os = Mock(OpenShiftService)
        pdf = Mock(PDFUtil)
        sq = Mock(SonarQubeUseCase)
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))
    }

    def "compute test discrepancies"() {
        given:
        def name = "myTests"

        when:
        def testIssues = createJiraTestIssues().each {
            it.test.isMissing = false
            it.test.isSuccess = true
        }

        def result = usecase.computeTestDiscrepancies(name, testIssues)

        then:
        result.discrepancies == "No discrepancies found."
        result.conclusion.summary == "Complete success, no discrepancies"
        result.conclusion.statement == "It is determined that all steps of the ${name} have been successfully executed and signature of this report verifies that the tests have been performed according to the plan. No discrepancies occurred."

        when:
        testIssues = createJiraTestIssues().each {
            it.test.isMissing = true
            it.test.isSuccess = false
        }

        result = usecase.computeTestDiscrepancies(name, testIssues)

        then:
        result.discrepancies == "The following minor discrepancies were found during testing: ${testIssues.collect { it.key }.join(", ")}."
        result.conclusion.summary == "Success - minor discrepancies found"
        result.conclusion.statement == "Some discrepancies were found as tests were not executed, this may be per design."

        when:
        testIssues = createJiraTestIssues().each {
            it.test.isMissing = false
            it.test.isSuccess = false
        }

        result = usecase.computeTestDiscrepancies(name, testIssues)

        then:
        result.discrepancies == "The following major discrepancies were found during testing: ${testIssues.collect { it.key }.join(", ")}."
        result.conclusion.summary == "No success - major discrepancies found"
        result.conclusion.statement == "Some discrepancies occured as tests did fail. It is not recommended to continue!"

        when:
        testIssues = createJiraTestIssues()
        testIssues[0..1].each {
            it.test.isMissing = true
            it.test.isSuccess = false
        }
        testIssues[2..4].each {
            it.test.isMissing = false
            it.test.isSuccess = false
        }

        result = usecase.computeTestDiscrepancies(name, testIssues)

        then:
        result.discrepancies == "The following major discrepancies were found during testing: ${testIssues.collect { it.key }.join(", ")}."
        result.conclusion.summary == "No success - major discrepancies found"
        result.conclusion.statement == "Some discrepancies occured as tests did fail. It is not recommended to continue!"
    }

    def "create CS"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.CS as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createCS()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)

        then:
        1 * project.getSystemRequirementsTypeInterfaces()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create DSD"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Limit the project to a single repository that is name-mapped to the project's first component
        def component = project.components.first()
        def repository = project.repositories.first()
        repository.id = component.name
        project.repositories = [repository]

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.DSD as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createDSD()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)

        then:
        1 * usecase.computeComponentMetadata(documentType)
        1 * project.getTechnicalSpecifications()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create DTP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.DTP as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createDTP()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType)

        then:
        1 * project.getAutomatedTestsTypeUnit()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create DTP without Jira"() {
        given:
        project.services.jira = null

        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.DTP as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createDTP()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType)
        1 * levaFiles.getDocumentChapterData(documentType) >> chapterData
        0 * usecase.getWatermarkText(documentType)

        then:
        1 * project.getAutomatedTestsTypeUnit()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create DTR"() {
        given:
        // Test Parameters
        def xmlFile = Files.createTempFile("junit", ".xml").toFile()
        xmlFile << "<?xml version='1.0' ?>\n" + createJUnitXMLTestResults()

        def repo = project.repositories.first()
        def testIssues = project.getAutomatedTestsTypeUnit()
        def testReportFiles = [xmlFile]
        def testResults = new JUnitTestReportsUseCase(project, steps, util).parseTestReportFiles(testReportFiles)
        def data = [
            tests: [
                unit: [
                    testReportFiles: testReportFiles,
                    testResults: testResults
                ]
            ]
        ]

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.DTR as String
        def files = [ "raw/${xmlFile.name}": xmlFile.bytes ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]

        when:
        usecase.createDTR(repo, data)

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType)

        then:
        1 * project.getAutomatedTestsTypeUnit("Technology-${repo.id}")
        1 * jiraUseCase.matchTestIssuesAgainstTestResults(testIssues, testResults, _, _)
        1 * usecase.computeTestDiscrepancies("Development Tests", testIssues)
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], repo)
        1 * usecase.createDocument(documentType, repo, _, files, _, null, _)

        cleanup:
        xmlFile.delete()
    }

    def "create DTR without Jira"() {
        given:
        project.services.jira = null

        // Test Parameters
        def xmlFile = Files.createTempFile("junit", ".xml").toFile()
        xmlFile << "<?xml version='1.0' ?>\n" + createJUnitXMLTestResults()

        def repo = project.repositories.first()
        def testIssues = project.getAutomatedTestsTypeUnit()
        def testReportFiles = [xmlFile]
        def testResults = new JUnitTestReportsUseCase(project, steps, util).parseTestReportFiles(testReportFiles)
        def data = [
            tests: [
                unit: [
                    testReportFiles: testReportFiles,
                    testResults: testResults
                ]
            ]
        ]

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.DTR as String
        def files = [ "raw/${xmlFile.name}": xmlFile.bytes ]

        // Stubbed Method Responses
        def buildParams = createProjectBuildEnvironment(env)
        def chapterData = ["sec1": "myContent"]

        when:
        usecase.createDTR(repo, data)

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType)
        1 * levaFiles.getDocumentChapterData(documentType) >> chapterData
        0 * usecase.getWatermarkText(documentType)

        then:
        1 * project.getAutomatedTestsTypeUnit("Technology-${repo.id}") >> testIssues
        1 * jiraUseCase.matchTestIssuesAgainstTestResults(testIssues, testResults, _, _)
        1 * usecase.computeTestDiscrepancies("Development Tests", testIssues)
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], repo)
        1 * usecase.createDocument(documentType, repo, _, files, _, null, _)
    }

    def "create FTP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.FTP as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createFTP()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)

        then:
        1 * project.getAutomatedTestsTypeAcceptance()
        1 * project.getAutomatedTestsTypeIntegration()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.getWatermarkText(documentType)
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

def "create FTR"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Test Parameters
        def xmlFile = Files.createTempFile("junit", ".xml").toFile()
        xmlFile << "<?xml version='1.0' ?>\n" + createJUnitXMLTestResults()

        def repo = project.repositories.first()
        def acceptanceTestIssues = project.getAutomatedTestsTypeAcceptance()
        def integrationTestIssues = project.getAutomatedTestsTypeIntegration()
        def testReportFiles = [xmlFile]
        def testResults = new JUnitTestReportsUseCase(project, steps, util).parseTestReportFiles(testReportFiles)
        def data = [
            tests: [
                acceptance: [
                    testReportFiles: testReportFiles,
                    testResults: testResults
                ],
                integration: [
                    testReportFiles: testReportFiles,
                    testResults: testResults
                ]
            ]
        ]

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.FTR as String
        def files = [ "raw/${xmlFile.name}": xmlFile.bytes ]
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createFTR(null, data)

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)

        then:
        1 * project.getAutomatedTestsTypeAcceptance() >> acceptanceTestIssues
        1 * project.getAutomatedTestsTypeIntegration() >> integrationTestIssues
        1 * jiraUseCase.matchTestIssuesAgainstTestResults(acceptanceTestIssues, testResults, _, _)
        1 * jiraUseCase.matchTestIssuesAgainstTestResults(integrationTestIssues, testResults, _, _)
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.getWatermarkText(documentType)
        1 * usecase.createDocument(documentType, null, _, files, null, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]

        cleanup:
        xmlFile.delete()
    }

    def "create FS"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.FS as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]
        def documentIssue = createJiraDocumentIssues().first()

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"

        when:
        usecase.createFS()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.getWatermarkText(documentType)
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create IVP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.IVP as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}_Q" ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createIVP()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)

        then:
        1 * project.getAutomatedTestsTypeInstallation()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.getWatermarkText(documentType)
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create IVR"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Test Parameters
        def xmlFile = Files.createTempFile("junit", ".xml").toFile()
        xmlFile << "<?xml version='1.0' ?>\n" + createJUnitXMLTestResults()

        def repo = project.repositories.first()
        def testIssues = project.getAutomatedTestsTypeInstallation()
        def testReportFiles = [xmlFile]
        def testResults = new JUnitTestReportsUseCase(project, steps, util).parseTestReportFiles(testReportFiles)
        def data = [
            tests: [
                installation: [
                    testReportFiles: testReportFiles,
                    testResults: testResults
                ]
            ]
        ]

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.IVR as String
        def files = [ "raw/${xmlFile.name}": xmlFile.bytes ]
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createIVR(null, data)

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType)

        then:
        1 * project.getAutomatedTestsTypeInstallation() >> testIssues
        1 * jiraUseCase.matchTestIssuesAgainstTestResults(testIssues, testResults, _, _)
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.createDocument(documentType, null, _, files, null, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]

        cleanup:
        xmlFile.delete()
    }

    def "create SCP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.SCP as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createSCP()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType)

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create SCP without Jira"() {
        given:
        project.services.jira = null

        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.SCP as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createSCP()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType)
        1 * levaFiles.getDocumentChapterData(documentType) >> chapterData
        0 * usecase.getWatermarkText(documentType)

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create SCR"() {
        given:
        steps.env.BUILD_ID = "0815"

        // Test Parameters
        def repo = project.repositories.first()

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.SCR as String
        def sqReportsPath = "sonarqube/${repo.id}"
        def sqReportsStashName = "scrr-report-${repo.id}-${steps.env.BUILD_ID}"
        def files = [ "${usecase.getDocumentBasename("SCRR", project.buildParams.version, steps.env.BUILD_ID, repo)}.docx": getResource("Test.docx").bytes ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def sqReportFiles = [ getResource("Test.docx") ]

        when:
        usecase.createSCR(repo)

        then:
        1 * jenkins.unstashFilesIntoPath(sqReportsStashName, "${steps.env.WORKSPACE}/${sqReportsPath}", "SonarQube Report") >> true
        1 * sq.loadReportsFromPath("${steps.env.WORKSPACE}/${sqReportsPath}") >> sqReportFiles

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType)

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], repo)
        1 * usecase.createDocument(documentType, repo, _, files, _, null, _)
    }

    def "create SCR without Jira"() {
        given:
        steps.env.BUILD_ID = "0815"
        project.services.jira = null

        // Test Parameters
        def repo = project.repositories.first()

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.SCR as String
        def sqReportsPath = "sonarqube/${repo.id}"
        def sqReportsStashName = "scrr-report-${repo.id}-${steps.env.BUILD_ID}"
        def files = [ "${usecase.getDocumentBasename("SCRR", project.buildParams.version, steps.env.BUILD_ID, repo)}.docx": getResource("Test.docx").bytes ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def sqReportFiles = [ getResource("Test.docx") ]

        when:
        usecase.createSCR(repo)

        then:
        1 * jenkins.unstashFilesIntoPath(sqReportsStashName, "${steps.env.WORKSPACE}/${sqReportsPath}", "SonarQube Report") >> true
        1 * sq.loadReportsFromPath("${steps.env.WORKSPACE}/${sqReportsPath}") >> sqReportFiles

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType)
        1 * levaFiles.getDocumentChapterData(documentType) >> chapterData
        0 * usecase.getWatermarkText(documentType)

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], repo)
        1 * usecase.createDocument(documentType, repo, _, files, _, null, _)
    }

    def "create SDS"() {
        given:
        // Test Parameters
        def repo = project.repositories.first()

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.SDS as String

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]

        when:
        usecase.createSDS(repo)

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], repo)
        1 * usecase.getWatermarkText(documentType)
        1 * usecase.createDocument(documentType, repo, _, [:], _, null, _)
    }

    def "create TIP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.TIP as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}_Q" ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createTIP()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType)

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create TIP without Jira"() {
        given:
        project.services.jira = null

        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.TIP as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}_Q" ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createTIP()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType)
        1 * levaFiles.getDocumentChapterData(documentType) >> chapterData
        0 * usecase.getWatermarkText(documentType)

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create TIR"() {
        given:
        // Test Parameters
        def repo = project.repositories.first()

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.TIR as String

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]

        when:
        usecase.createTIR(repo)

        then:
        1 * os.getPodDataForComponent(repo.id) >> createOpenShiftPodDataForComponent()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType)

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], repo)
        1 * usecase.createDocument(documentType, repo, _, [:], _, null, _)
    }

    def "create TIR without Jira"() {
        given:
        project.services.jira = null

        // Test Parameters
        def repo = project.repositories.first()

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.TIR as String

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]

        when:
        usecase.createTIR(repo)

        then:
        1 * os.getPodDataForComponent(repo.id) >> createOpenShiftPodDataForComponent()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType)

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], repo)
        1 * usecase.createDocument(documentType, repo, _, [:], _, null, _)
    }

    def "create URS"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.URS as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createURS()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.getWatermarkText(documentType)
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create overall DTR"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.DTR as String
        def documentTypeName = LeVADocumentUseCase.DocumentType.OVERALL_DTR as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]

        // Stubbed Method Responses
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createOverallDTR()

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentTypeName])
        1 * usecase.createOverallDocument("Overall-Cover", documentType, _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentTypeName]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create overall SCR"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.SCR as String
        def documentTypeName = LeVADocumentUseCase.DocumentType.OVERALL_SCR as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]

        // Stubbed Method Responses
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createOverallSCR()

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentTypeName])
        1 * usecase.createOverallDocument("Overall-Cover", documentType, _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentTypeName]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create overall SDS"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.SDS as String
        def documentTypeName = LeVADocumentUseCase.DocumentType.OVERALL_SDS as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]

        // Stubbed Method Responses
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createOverallSDS()

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentTypeName])
        1 * usecase.createOverallDocument("Overall-Cover", documentType, _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentTypeName]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create overall TIR"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.TIR as String
        def documentTypeName = LeVADocumentUseCase.DocumentType.OVERALL_TIR as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]

        // Stubbed Method Responses
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createOverallTIR()

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentTypeName])
        1 * usecase.createOverallDocument("Overall-TIR-Cover", documentType, _, _, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentTypeName]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "get supported documents"() {
        when:
        def result = usecase.getSupportedDocuments()

        then:
        result.size() == 20

        then:
        result.contains("CS")
        result.contains("DSD")
        result.contains("DTP")
        result.contains("DTR")
        result.contains("FS")
        result.contains("FTP")
        result.contains("FTR")
        result.contains("IVP")
        result.contains("IVR")
        result.contains("SCP")
        result.contains("SCR")
        result.contains("SDS")
        result.contains("TIP")
        result.contains("TIR")
        result.contains("URS")
        result.contains("OVERALL_DTR")
        result.contains("OVERALL_IVR")
        result.contains("OVERALL_SCR")
        result.contains("OVERALL_SDS")
        result.contains("OVERALL_TIR")
    }

    def "notify LeVA document issue in DEV"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        def documentType = "myType"
        def message = "myMessage"

        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.notifyJiraTrackingIssue(documentType, message)

        then:
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
        1 * jiraUseCase.jira.appendCommentToIssue(documentIssue.key, message)
    }

    def "notify LeVA document issue with query returning != 1 issue"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        def documentType = "myType"
        def message = "myMessage"

        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]
        def documentIssues = createJiraDocumentIssues()

        when:
        usecase.notifyJiraTrackingIssue(documentType, message)

        then:
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [] // don't care

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: Jira query returned 0 issues: '${jqlQuery}'."

        when:
        usecase.notifyJiraTrackingIssue(documentType, message)

        then:
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> documentIssues

        then:
        e = thrown(RuntimeException)
        e.message == "Error: Jira query returned 3 issues: '${jqlQuery}'."
    }

    def "docs with watermark text in DEV"() {
        given:
        project.buildParams.targetEnvironment = "dev"

        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        when:
        def result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.CS as String)

        then:
        result == "Developer Preview"

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.DSD as String)

        then:
        result == "Developer Preview"

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.DTP as String)

        then:
        result == null

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.DTR as String)

        then:
        result == "Developer Preview"

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.FS as String)

        then:
        result == "Developer Preview"

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.FTP as String)

        then:
        result == null

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.FTR as String)

        then:
        result == "Developer Preview"

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.IVP as String)

        then:
        result == null

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.IVR as String)

        then:
        result == "Developer Preview"

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.SCP as String)

        then:
        result == "Developer Preview"

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.SCR as String)

        then:
        result == "Developer Preview"

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.SDS as String)

        then:
        result == "Developer Preview"

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.TIP as String)

        then:
        result == null

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.TIR as String)

        then:
        result == "Developer Preview"

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.URS as String)

        then:
        result == "Developer Preview"

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.OVERALL_DTR as String)

        then:
        result == "Developer Preview"

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.OVERALL_IVR as String)

        then:
        result == "Developer Preview"

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.OVERALL_SCR as String)

        then:
        result == "Developer Preview"

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.OVERALL_SDS as String)

        then:
        result == "Developer Preview"

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.OVERALL_TIR as String)

        then:
        result == "Developer Preview"
    }
}
