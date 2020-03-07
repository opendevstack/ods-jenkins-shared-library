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
            it.isMissing = false
            it.isSuccess = true
        }

        def result = usecase.computeTestDiscrepancies(name, testIssues)

        then:
        result.discrepancies == "No discrepancies found."
        result.conclusion.summary == "Complete success, no discrepancies"
        result.conclusion.statement == "It is determined that all steps of the ${name} have been successfully executed and signature of this report verifies that the tests have been performed according to the plan. No discrepancies occurred."

        when:
        testIssues = createJiraTestIssues().each {
            it.isMissing = true
            it.isSuccess = false
        }

        result = usecase.computeTestDiscrepancies(name, testIssues)

        then:
        result.discrepancies == "The following minor discrepancies were found during testing: ${testIssues.collect { it.key }.join(", ")}."
        result.conclusion.summary == "Success - minor discrepancies found"
        result.conclusion.statement == "Some discrepancies were found as tests were not executed, this may be per design."

        when:
        testIssues = createJiraTestIssues().each {
            it.isMissing = false
            it.isSuccess = false
        }

        result = usecase.computeTestDiscrepancies(name, testIssues)

        then:
        result.discrepancies == "The following major discrepancies were found during testing: ${testIssues.collect { it.key }.join(", ")}."
        result.conclusion.summary == "No success - major discrepancies found"
        result.conclusion.statement == "Some discrepancies occured as tests did fail. It is not recommended to continue!"

        when:
        testIssues = createJiraTestIssues()
        testIssues[0..1].each {
            it.isMissing = true
            it.isSuccess = false
        }
        testIssues[2..4].each {
            it.isMissing = false
            it.isSuccess = false
        }

        result = usecase.computeTestDiscrepancies(name, testIssues)

        then:
        result.discrepancies == "The following major discrepancies were found during testing: ${testIssues.collect { it.key }.join(", ")}."
        result.conclusion.summary == "No success - major discrepancies found"
        result.conclusion.statement == "Some discrepancies occured as tests did fail. It is not recommended to continue!"
    }

    def "create CSD"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.CSD as String
        def jqlQuery = [jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}"]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createCSD()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)

        then:
        1 * project.getSystemRequirements()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create DIL"(){
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.DIL as String

        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}_Q" ]
        def documentIssue = createJiraDocumentIssues().first()
        def uri = "http://nexus"

        when:
        usecase.createDIL()

        then:
        1 * usecase.getWatermarkText(documentType)

        then:
        1 * project.getBugs()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create DTP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Test Parameters
        def repo = project.repositories.first()

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.DTP as String
        def jqlQuery = [jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}"]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createDTP(repo)

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType)

        then:
        1 * project.getAutomatedTestsTypeUnit()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], repo)
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create DTP without Jira"() {
        given:
        project.services.jira = null

        jiraUseCase = Spy(new JiraUseCase(project, steps, util, null))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Test Parameters
        def repo = project.repositories.first()

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.DTP as String
        def jqlQuery = [jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}"]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createDTP(repo)

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType)
        1 * levaFiles.getDocumentChapterData(documentType) >> chapterData
        0 * usecase.getWatermarkText(documentType)

        then:
        1 * project.getAutomatedTestsTypeUnit()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], repo)
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
    }

    def "create DTR"() {
        given:
        // Test Parameters
        def xmlFile = Files.createTempFile("junit", ".xml").toFile()
        xmlFile << "<?xml version='1.0' ?>\n" + createJUnitXMLTestResults()

        def repo = project.repositories.first()
        repo.id = "demo-app-carts"

        def testIssues = project.getAutomatedTestsTypeUnit("Technology-${repo.id}")
        def testReportFiles = [xmlFile]
        def testResults = new JUnitTestReportsUseCase(project, steps).parseTestReportFiles(testReportFiles)
        def data = [
            tests: [
                unit: [
                    testReportFiles: testReportFiles,
                    testResults    : testResults
                ]
            ]
        ]

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.DTR as String
        def files = ["raw/${xmlFile.name}": xmlFile.bytes]

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
        repo.id = "demo-app-carts"

        def testIssues = project.getAutomatedTestsTypeUnit("Technology-${repo.id}")
        def testReportFiles = [xmlFile]
        def testResults = new JUnitTestReportsUseCase(project, steps).parseTestReportFiles(testReportFiles)
        def data = [
            tests: [
                unit: [
                    testReportFiles: testReportFiles,
                    testResults    : testResults
                ]
            ]
        ]

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.DTR as String
        def files = ["raw/${xmlFile.name}": xmlFile.bytes]

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

    def "create CFTP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.CFTP as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createCFTP()

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
        def testResults = new JUnitTestReportsUseCase(project, steps).parseTestReportFiles(testReportFiles)
        def data = [
            tests: [
                acceptance : [
                    testReportFiles: testReportFiles,
                    testResults    : testResults
                ],
                integration: [
                    testReportFiles: testReportFiles,
                    testResults    : testResults
                ]
            ]
        ]

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.CFTR as String
        def files = [ "raw/${xmlFile.name}": xmlFile.bytes ]
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createCFTR(null, data)

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

    def "create IVP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.IVP as String
        def jqlQuery = [jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}_Q"]

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
        xmlFile << "<?xml version='1.0' ?>\n" + createSockShopJUnitXmlTestResults()

        def repo = project.repositories.first()
        def testIssues = project.getAutomatedTestsTypeInstallation()
        def testReportFiles = [xmlFile]
        def testResults = new JUnitTestReportsUseCase(project, steps).parseTestReportFiles(testReportFiles)
        def data = [
            tests: [
                installation: [
                    testReportFiles: testReportFiles,
                    testResults    : testResults
                ]
            ]
        ]

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.IVR as String
        def files = ["raw/${xmlFile.name}": xmlFile.bytes]
        def jqlQuery = [jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}"]

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

    def "create SSDS"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))
        steps.env.BUILD_ID = "0815"

        // Test Parameters
        def repo = project.repositories.first()

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.SSDS as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]
        def sqReportsPath = "sonarqube/${repo.id}"
        def sqReportsStashName = "scrr-report-${repo.id}-${steps.env.BUILD_ID}"

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()
        def sqReportFiles = [ getResource("Test.docx") ]
        def requirement = [ key: "REQ-1", name: "This is the req 1", gampTopic: "roles" ]
        def techSpec = [ key: "TS-1", softwareDesignSpec: "This is the software design spec for TS-1", name: "techSpec 1"]
        def compMetadata = [
            "demo-app-front-end": [
                key: "Front-key",
                componentName: "demo-app-front-end",
                componentId: "front",
                componentType: "ODS Component",
                odsRepoType: "ods",
                description: "Example description",
                nameOfSoftware: "Stock Shop frontend",
                references: "N/A",
                supplier: "N/A",
                version: "0.1",
                requirements: [ requirement ],
                techSpecs: [ techSpec ]
            ]
        ]
        when:
        usecase.createSSDS()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)

        then:
		1 * usecase.computeComponentMetadata(documentType) >> compMetadata
		1 * project.getTechnicalSpecifications()
        jenkins.unstashFilesIntoPath(_, _, "SonarQube Report") >> true
        sq.loadReportsFromPath(_) >> sqReportFiles

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], null)
        1 * usecase.getWatermarkText(documentType)
        1 * usecase.createDocument(documentType, null, _, _, _, null, _) >> uri
		1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]

    }

    def "create RA"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.RA as String
        def jqlQuery = [ jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}" ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()

        when:
        usecase.createRA()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)

        then:
		2 * project.getRisks()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], null)
        1 * usecase.getWatermarkText(documentType)
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
		1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) >> [documentIssue]
    }

    def "create TIP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.TIP as String
        def jqlQuery = [jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}_Q"]

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

        jiraUseCase = Spy(new JiraUseCase(project, steps, util, null))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.TIP as String
        def jqlQuery = [jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}_Q"]

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

    def "create overall DTR"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.DTR as String
        def documentTypeName = LeVADocumentUseCase.DocumentType.OVERALL_DTR as String
        def jqlQuery = [jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}"]

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

    def "create overall TIR"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.TIR as String
        def documentTypeName = LeVADocumentUseCase.DocumentType.OVERALL_TIR as String
        def jqlQuery = [jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}"]

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
        result.size() == 15

        then:
        result.contains("CSD")
        result.contains("DIL")
        result.contains("DTP")
        result.contains("DTR")
        result.contains("CFTP")
        result.contains("CFTR")
        result.contains("IVP")
        result.contains("IVR")
        result.contains("SSDS")
        result.contains("RA")
        result.contains("TIP")
        result.contains("TIR")
        result.contains("OVERALL_DTR")
        result.contains("OVERALL_IVR")
        result.contains("OVERALL_TIR")
    }

    def "notify LeVA document issue in DEV"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, levaFiles, nexus, os, pdf, sq))

        def documentType = "myType"
        def message = "myMessage"

        def jqlQuery = [jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}"]
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

        def jqlQuery = [jql: "project = ${project.key} AND issuetype = 'LeVA Documentation' AND labels = LeVA_Doc:${documentType}"]
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
        def result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.CSD as String)

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
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.CFTP as String)

        then:
        result == null

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.CFTR as String)

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
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.SSDS as String)

        then:
        result == null

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.RA as String)

        then:
        result == null

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.TIP as String)

        then:
        result == null

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.TIR as String)

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
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.OVERALL_TIR as String)

        then:
        result == "Developer Preview"
    }
}
