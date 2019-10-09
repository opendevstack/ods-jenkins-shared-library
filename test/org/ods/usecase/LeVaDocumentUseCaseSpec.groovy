package org.ods.usecase

import groovy.json.JsonOutput

import java.nio.file.Files

import org.ods.service.DocGenService
import org.ods.service.JenkinsService
import org.ods.service.LeVaDocumentChaptersFileService
import org.ods.service.NexusService
import org.ods.service.OpenShiftService
import org.ods.usecase.JiraUseCase
import org.ods.util.MROPipelineUtil
import org.ods.util.PDFUtil

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class LeVaDocumentUseCaseSpec extends SpecHelper {

    LeVaDocumentUseCase createUseCase(PipelineSteps steps, MROPipelineUtil util, DocGenService docGen, JenkinsService jenkins, JiraUseCase jira, LeVaDocumentChaptersFileService levaFiles, NexusService nexus, OpenShiftService os, PDFUtil pdf) {
        return new LeVaDocumentUseCase(steps, util, docGen, jenkins, jira, levaFiles, nexus, os, pdf)
    }

    def "compute DTR discrepancies"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def usecase = createUseCase(
            steps,
            Mock(MROPipelineUtil),
            Mock(DocGenService),
            Mock(JenkinsService),
            Mock(JiraUseCase),
            Mock(LeVaDocumentChaptersFileService),
            Mock(NexusService),
            Mock(OpenShiftService),
            Mock(PDFUtil)
        )

        when:
        def testIssues = createJiraTestIssues().each {
            it.isMissing = false
            it.isSuccess = true
        }

        def result = usecase.computeDTRDiscrepancies(testIssues)

        then:
        result.discrepancies == "No discrepancies found."
        result.conclusion.summary == "Complete success, no discrepancies"
        result.conclusion.statement == "It is determined that all steps of the Development Tests have been successfully executed and signature of this report verifies that the tests have been performed according to the plan. No discrepancies occurred."

        when:
        testIssues = createJiraTestIssues().each {
            it.isMissing = true
            it.isSuccess = false
        }

        result = usecase.computeDTRDiscrepancies(testIssues)

        then:
        result.discrepancies == "The following minor discrepancies were found during testing: ${testIssues.collect { it.key }.join(", ")}."
        result.conclusion.summary == "Success - minor discrepancies found"
        result.conclusion.statement == "Some discrepancies were found as tests were not executed, this may be per design."

        when:
        testIssues = createJiraTestIssues().each {
            it.isMissing = false
            it.isSuccess = false
        }

        result = usecase.computeDTRDiscrepancies(testIssues)

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

        result = usecase.computeDTRDiscrepancies(testIssues)

        then:
        result.discrepancies == "The following major discrepancies were found during testing: ${testIssues.collect { it.key }.join(", ")}."
        result.conclusion.summary == "No success - major discrepancies found"
        result.conclusion.statement == "Some discrepancies occured as tests did fail. It is not recommended to continue!"
    }

    def "compute document file base name"() {
        given:
        def steps = Spy(util.PipelineSteps)
        steps.env.BUILD_ID = "0815"

        def usecase = createUseCase(
            steps,
            Mock(MROPipelineUtil),
            Mock(DocGenService),
            Mock(JenkinsService),
            Mock(JiraUseCase),
            Mock(LeVaDocumentChaptersFileService),
            Mock(NexusService),
            Mock(OpenShiftService),
            Mock(PDFUtil)
        )

        def type = "myType"
        def buildParams = createBuildEnvironment(env)
        def project = createProject()
        def repo = project.repositories.first()

        when:
        def result = usecase.computeDocumentFileBaseName(type, steps, buildParams, project, repo)

        then:
        result == "${type}-${project.id}-${repo.id}-${buildParams.version}-${steps.env.BUILD_ID}"
    }

    def "compute document file base name without a repo"() {
        given:
        def steps = Spy(util.PipelineSteps)
        steps.env.BUILD_ID = "0815"

        def usecase = createUseCase(
            steps,
            Mock(MROPipelineUtil),
            Mock(DocGenService),
            Mock(JenkinsService),
            Mock(JiraUseCase),
            Mock(LeVaDocumentChaptersFileService),
            Mock(NexusService),
            Mock(OpenShiftService),
            Mock(PDFUtil)
        )

        def type = "myType"
        def buildParams = createBuildEnvironment(env)
        def project = createProject()
        def repo = null

        when:
        def result = usecase.computeDocumentFileBaseName(type, steps, buildParams, project, repo)

        then:
        result == "${type}-${project.id}-${buildParams.version}-${steps.env.BUILD_ID}"
    }

    def "create document"() {
        given:
        def buildParams = createBuildParams()

        def steps = Spy(util.PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def docGen = Mock(DocGenService)
        def jira = Mock(JiraUseCase)
        def levaFiles = Mock(LeVaDocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def pdf = Mock(PDFUtil)

        def logFile1 = Files.createTempFile("raw", ".log").toFile() << "Log File 1"
        def logFile2 = Files.createTempFile("raw", ".log").toFile() << "Log File 2"

        def type = "myType"
        def project = createProject()
        def repo = project.repositories.first()
        def data = [ a: 1, b: 2, c: 3 ]
        def files = [
            "raw/${logFile1.name}": logFile1.bytes,
            "raw/${logFile2.name}": logFile2.bytes
        ]

        def version = buildParams.version
        def document = "PDF".bytes
        def archive = "Archive".bytes
        def nexusUri = new URI("http://nexus")

        def baseName = LeVaDocumentUseCase.computeDocumentFileBaseName(type, steps, buildParams, project, repo)

        LeVaDocumentUseCase.DOCUMENT_TYPE_NAMES = [
            "myType": "My Document"
        ]

        when:
        def result = LeVaDocumentUseCase.createDocument(
            [steps: steps, util: util, docGen: docGen, jira: jira, levaFiles: levaFiles, nexus: nexus, pdf: pdf],
            type, project, repo, data, files, null
        )

        then:
        1 * util.getBuildParams() >> buildParams

        then:
        1 * docGen.createDocument(type, "0.1", data) >> document

        then:
        1 * util.createZipArtifact(
            "${baseName}.zip",
            [
                "${baseName}.pdf": document,
                "raw/${baseName}.json": JsonOutput.toJson(data).bytes,
                "raw/${logFile1.name}": logFile1.bytes,
                "raw/${logFile2.name}": logFile2.bytes
            ]
        ) >> archive

        then:
        1 * nexus.storeArtifact(
            project.services.nexus.repository.name,
            "${project.id.toLowerCase()}-${version}",
            "${baseName}.zip",
            archive,
            "application/zip"
        ) >> nexusUri

        then:
        1 * jira.notifyLeVaDocumentTrackingIssue(project.id, type, "A new ${LeVaDocumentUseCase.DOCUMENT_TYPE_NAMES[type]} has been generated and is available at: ${nexusUri}.")

        then:
        result == nexusUri.toString()

        cleanup:
        logFile1.delete()
        logFile2.delete()
    }

    def "create document adds watermark in dev environment when pipeline is triggered by a change management process"() {
        given:
        def buildParams = createBuildParams()
        buildParams.targetEnvironment = MROPipelineUtil.PipelineEnvs.DEV

        def steps = Spy(util.PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def docGen = Mock(DocGenService)
        def jira = Mock(JiraUseCase)
        def levaFiles = Mock(LeVaDocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def pdf = Mock(PDFUtil)

        def logFile1 = Files.createTempFile("raw", ".log").toFile() << "Log File 1"
        def logFile2 = Files.createTempFile("raw", ".log").toFile() << "Log File 2"

        def type = "myType"
        def project = createProject()
        def repo = project.repositories.first()
        def data = [ a: 1, b: 2, c: 3 ]
        def files = [
            "raw/${logFile1.name}": logFile1.bytes,
            "raw/${logFile2.name}": logFile1.bytes
        ]

        def document = "PDF".bytes

        LeVaDocumentUseCase.DOCUMENT_TYPE_NAMES = [
            "myType": "My Document"
        ]

        when:
        def result = LeVaDocumentUseCase.createDocument(
            [steps: steps, util: util, docGen: docGen, jira: jira, levaFiles: levaFiles, nexus: nexus, pdf: pdf],
            type, project, repo, data, files, null
        )

        then:
        1 * util.getBuildParams() >> buildParams

        then:
        1 * docGen.createDocument(type, "0.1", data) >> document

        then:
        1 * util.isTriggeredByChangeManagementProcess() >> true

        then:
        1 * pdf.addWatermarkText(document, "Developer Preview")

        cleanup:
        logFile1.delete()
        logFile2.delete()
    }

    def "create document without a repo"() {
        given:
        def buildParams = createBuildParams()

        def steps = Spy(util.PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def docGen = Mock(DocGenService)
        def jira = Mock(JiraUseCase)
        def levaFiles = Mock(LeVaDocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def pdf = Mock(PDFUtil)

        def type = "myType"
        def project = createProject()
        def repo = null

        def version = buildParams.version
        def nexusUri = new URI("http://nexus")

        LeVaDocumentUseCase.DOCUMENT_TYPE_NAMES = [
            "myType": "My Document"
        ]

        when:
        def result = LeVaDocumentUseCase.createDocument(
            [steps: steps, util: util, docGen: docGen, jira: jira, levaFiles: levaFiles, nexus: nexus, pdf: pdf],
            type, project, repo, [:], [:], null
        )

        then:
        _ * util.getBuildParams() >> buildParams

        then:
        1 * util.createZipArtifact({ "${type}-${project.id}-${version}-${steps.env.BUILD_ID}.zip" }, _) >> new byte[0]

        then:
        1 * nexus.storeArtifact(_, _, { "${type}-${project.id}-${version}.zip" }, *_) >> nexusUri
    }

    def "create DTP"() {
        given:
        createBuildEnvironment(env)

        def jira = Mock(JiraUseCase)
        def levaFiles = Mock(LeVaDocumentChaptersFileService)
        def usecase = createUseCase(
            Spy(util.PipelineSteps),
            Mock(MROPipelineUtil),
            Mock(DocGenService),
            Mock(JenkinsService),
            jira,
            levaFiles,
            Mock(NexusService),
            Mock(OpenShiftService),
            Mock(PDFUtil)
        )

        GroovyMock(LeVaDocumentUseCase, global: true)

        def project = createProject()

        def type = LeVaDocumentUseCase.DocumentTypes.DTP

        when:
        usecase.createDTP(project)

        then:
        1 * jira.getDocumentChapterData(project.id, type) >> ["sec1": "myContent"]
        0 * levaFiles.getDocumentChapterData(type)

        then:
        1 * jira.getAutomatedTestIssues(project.id) >> []

        then:
        1 * LeVaDocumentUseCase.createDocument(_, type, project, null, _, [:], _, null)
    }

    def "create DTP without Jira"() {
        given:
        createBuildEnvironment(env)

        def jira = Mock(JiraUseCase)
        def levaFiles = Mock(LeVaDocumentChaptersFileService)
        def usecase = createUseCase(
            Spy(util.PipelineSteps),
            Mock(MROPipelineUtil),
            Mock(DocGenService),
            Mock(JenkinsService),
            jira,
            levaFiles,
            Mock(NexusService),
            Mock(OpenShiftService),
            Mock(PDFUtil)
        )

        GroovyMock(LeVaDocumentUseCase, global: true)

        def project = createProject()
        project.services.jira = null

        def type = LeVaDocumentUseCase.DocumentTypes.DTP

        when:
        usecase.createDTP(project)

        then:
        1 * jira.getAutomatedTestIssues(project.id) >> []
        1 * levaFiles.getDocumentChapterData(type)
    }

    def "create DTR"() {
        given:
        createBuildEnvironment(env)

        def steps = Spy(util.PipelineSteps)
        def jira = Mock(JiraUseCase)
        def junit = new JUnitTestReportsUseCase(steps)
        def levaFiles = Mock(LeVaDocumentChaptersFileService)
        def usecase = createUseCase(
            steps,
            Mock(MROPipelineUtil),
            Mock(DocGenService),
            Mock(JenkinsService),
            jira,
            levaFiles,
            Mock(NexusService),
            Mock(OpenShiftService),
            Mock(PDFUtil)
        )

        GroovyMock(LeVaDocumentUseCase, global: true)

        def xmlFile = Files.createTempFile("junit", ".xml").toFile()
        xmlFile << "<?xml version='1.0' ?>\n" + createJUnitXMLTestResults()

        def project = createProject()
        def repo = project.repositories.first()
        def testReportFiles = [xmlFile]
        def testResults = junit.parseTestReportFiles(testReportFiles)

        def testIssues = createJiraTestIssues()
        def type = LeVaDocumentUseCase.DocumentTypes.DTR
        def files = [ "raw/${xmlFile.name}": xmlFile.bytes ]
        def document = "myDocument".bytes

        when:
        // TODO: should we turn jira into a Spy to test an issue's success state?
        usecase.createDTR(project, repo, testResults, testReportFiles)

        then:
        1 * jira.getDocumentChapterData(project.id, type) >> ["sec1": "myContent"]
        0 * levaFiles.getDocumentChapterData(type)

        then:
        1 * jira.getAutomatedTestIssues(project.id, "Technology_${repo.id}") >> testIssues

        then:
        1 * jira.matchJiraTestIssuesAgainstTestResults(testIssues, testResults, _, _)

        then:
        1 * LeVaDocumentUseCase.createDocument(_, type, project, repo, _, files, _, null) >> document

        cleanup:
        xmlFile.delete()
    }

    def "create DTR without Jira"() {
        given:
        createBuildEnvironment(env)

        def steps = Spy(util.PipelineSteps)
        def jira = Mock(JiraUseCase)
        def junit = new JUnitTestReportsUseCase(steps)
        def levaFiles = Mock(LeVaDocumentChaptersFileService)
        def usecase = createUseCase(
            steps,
            Mock(MROPipelineUtil),
            Mock(DocGenService),
            Mock(JenkinsService),
            jira,
            levaFiles,
            Mock(NexusService),
            Mock(OpenShiftService),
            Mock(PDFUtil)
        )

        GroovyMock(LeVaDocumentUseCase, global: true)

        def xmlFile = Files.createTempFile("junit", ".xml").toFile()
        xmlFile << "<?xml version='1.0' ?>\n" + createJUnitXMLTestResults()

        def project = createProject()
        project.services.jira = null
        def repo = project.repositories.first()
        def testReportFiles = [xmlFile]
        def testResults = junit.parseTestReportFiles(testReportFiles)

        def type = LeVaDocumentUseCase.DocumentTypes.DTR

        when:
        // TODO: should we turn jira into a Spy to test an issue's success state?
        usecase.createDTR(project, repo, testResults, testReportFiles)

        then:
        1 * jira.getDocumentChapterData(project.id, type) >> [:]
        1 * levaFiles.getDocumentChapterData(type)

        then:
        1 * jira.getAutomatedTestIssues(project.id, "Technology_${repo.id}") >> []
    }

    def "create SCP"() {
        given:
        createBuildEnvironment(env)

        def jira = Mock(JiraUseCase)
        def levaFiles = Mock(LeVaDocumentChaptersFileService)
        def usecase = createUseCase(
            Spy(util.PipelineSteps),
            Mock(MROPipelineUtil),
            Mock(DocGenService),
            Mock(JenkinsService),
            jira,
            levaFiles,
            Mock(NexusService),
            Mock(OpenShiftService),
            Mock(PDFUtil)
        )

        GroovyMock(LeVaDocumentUseCase, global: true)

        def project = createProject()

        def type = LeVaDocumentUseCase.DocumentTypes.SCP

        when:
        usecase.createSCP(project)

        then:
        1 * jira.getDocumentChapterData(project.id, type) >> ["sec1": "myContent"]
        0 * levaFiles.getDocumentChapterData(type)

        then:
        1 * LeVaDocumentUseCase.createDocument(_, type, project, null, _, [:], _, null)
    }

    def "create SCP without Jira"() {
        given:
        createBuildEnvironment(env)

        def levaFiles = Mock(LeVaDocumentChaptersFileService)
        def jira = Mock(JiraUseCase)
        def usecase = createUseCase(
            Spy(util.PipelineSteps),
            Mock(MROPipelineUtil),
            Mock(DocGenService),
            Mock(JenkinsService),
            jira,
            levaFiles,
            Mock(NexusService),
            Mock(OpenShiftService),
            Mock(PDFUtil)
        )

        GroovyMock(LeVaDocumentUseCase, global: true)

        def project = createProject()
        project.services.jira = null

        def type = LeVaDocumentUseCase.DocumentTypes.SCP

        when:
        usecase.createSCP(project)

        then:
        1 * jira.getDocumentChapterData(project.id, type) >> [:]
        1 * levaFiles.getDocumentChapterData(type)
    }

    def "create SCR"() {
        given:
        def buildParams = createBuildEnvironment(env)

        def steps = Spy(util.PipelineSteps)
        def jira = Mock(JiraUseCase)
        def levaFiles = Mock(LeVaDocumentChaptersFileService)
        def pdf = Mock(PDFUtil)
        def usecase = createUseCase(
            steps,
            Mock(MROPipelineUtil),
            Mock(DocGenService),
            Mock(JenkinsService),
            jira,
            levaFiles,
            Mock(NexusService),
            Mock(OpenShiftService),
            pdf
        )

        GroovyMock(LeVaDocumentUseCase, global: true)

        def project = createProject()
        def repo = project.repositories.first()
        def sqReportFile = getResource("Test.docx")

        def type = LeVaDocumentUseCase.DocumentTypes.SCR
        def files = [ "${LeVaDocumentUseCase.computeDocumentFileBaseName(type, steps, buildParams, project, repo)}.docx": sqReportFile.bytes ]

        when:
        usecase.createSCR(project, repo, sqReportFile)

        then:
        1 * jira.getDocumentChapterData(project.id, type) >> ["sec1": "myContent"]
        0 * levaFiles.getDocumentChapterData(type)

        then:
        1 * LeVaDocumentUseCase.createDocument(_, type, project, repo, _, files, _, null)
    }

    def "create SCR without Jira"() {
        given:
        createBuildEnvironment(env)

        def jira = Mock(JiraUseCase)
        def levaFiles = Mock(LeVaDocumentChaptersFileService)
        def pdf = Mock(PDFUtil)
        def usecase = createUseCase(
            Spy(util.PipelineSteps),
            Mock(MROPipelineUtil),
            Mock(DocGenService),
            Mock(JenkinsService),
            jira,
            levaFiles,
            Mock(NexusService),
            Mock(OpenShiftService),
            pdf
        )

        GroovyMock(LeVaDocumentUseCase, global: true)

        def project = createProject()
        def repo = project.repositories.first()
        def sqReportFile = getResource("Test.docx")

        def type = LeVaDocumentUseCase.DocumentTypes.SCR

        when:
        usecase.createSCR(project, repo, sqReportFile)

        then:
        1 * jira.getDocumentChapterData(project.id, type) >> [:]
        1 * levaFiles.getDocumentChapterData(type)
    }

    def "create TIP"() {
        given:
        def buildParams = createBuildEnvironment(env)

        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraUseCase)
        def levaFiles = Mock(LeVaDocumentChaptersFileService)
        def usecase = createUseCase(
            Spy(util.PipelineSteps),
            util,
            Mock(DocGenService),
            Mock(JenkinsService),
            jira,
            levaFiles,
            Mock(NexusService),
            Mock(OpenShiftService),
            Mock(PDFUtil)
        )

        GroovyMock(LeVaDocumentUseCase, global: true)

        def project = createProject()

        def type = LeVaDocumentUseCase.DocumentTypes.TIP

        when:
        usecase.createTIP(project)

        then:
        1 * jira.getDocumentChapterData(project.id, type) >> ["sec1": "myContent"]
        0 * levaFiles.getDocumentChapterData(type)
        _ * util.getBuildParams() >> buildParams

        then:
        1 * LeVaDocumentUseCase.createDocument(_, type, project, null, _, [:], _, null)
    }

    def "create TIP without Jira"() {
        given:
        def buildParams = createBuildEnvironment(env)

        def util = Mock(MROPipelineUtil)
        def jira = Mock(JiraUseCase)
        def levaFiles = Mock(LeVaDocumentChaptersFileService)
        def usecase = createUseCase(
            Spy(util.PipelineSteps),
            util,
            Mock(DocGenService),
            Mock(JenkinsService),
            jira,
            levaFiles,
            Mock(NexusService),
            Mock(OpenShiftService),
            Mock(PDFUtil)
        )

        GroovyMock(LeVaDocumentUseCase, global: true)

        def project = createProject()
        project.services.jira = null

        def type = LeVaDocumentUseCase.DocumentTypes.TIP

        when:
        usecase.createTIP(project)

        then:
        1 * jira.getDocumentChapterData(project.id, type) >> [:]
        1 * levaFiles.getDocumentChapterData(type)
    }

    def "create TIR"() {
        given:
        def buildParams = createBuildEnvironment(env)

        def util = Mock(MROPipelineUtil)
        def jenkins = Mock(JenkinsService)
        def jira = Mock(JiraUseCase)
        def levaFiles = Mock(LeVaDocumentChaptersFileService)
        def os = Mock(OpenShiftService)
        def usecase = createUseCase(
            Spy(util.PipelineSteps),
            util,
            Mock(DocGenService),
            jenkins,
            jira,
            levaFiles,
            Mock(NexusService),
            os,
            Mock(PDFUtil)
        )

        GroovyMock(LeVaDocumentUseCase, global: true)

        def project = createProject()
        def repo = project.repositories.first()

        def type = LeVaDocumentUseCase.DocumentTypes.TIR

        when:
        usecase.createTIR(project, repo)

        then:
        1 * jira.getDocumentChapterData(project.id, type) >> ["sec1": "myContent"]
        0 * levaFiles.getDocumentChapterData(type)
        1 * os.getPodDataForComponent(repo.id) >> createOpenShiftPodDataForComponent()
        _ * util.getBuildParams() >> buildParams

        then:
        1 * LeVaDocumentUseCase.createDocument(_, type, project, repo, _, [:], _, null)
    }

    def "create TIR without Jira"() {
        given:
        def buildParams = createBuildEnvironment(env)

        def util = Mock(MROPipelineUtil)
        def jenkins = Mock(JenkinsService)
        def jira = Mock(JiraUseCase)
        def levaFiles = Mock(LeVaDocumentChaptersFileService)
        def os = Mock(OpenShiftService)
        def usecase = createUseCase(
            Spy(util.PipelineSteps),
            util,
            Mock(DocGenService),
            jenkins,
            jira,
            levaFiles,
            Mock(NexusService),
            os,
            Mock(PDFUtil)
        )

        GroovyMock(LeVaDocumentUseCase, global: true)

        def project = createProject()
        def repo = project.repositories.first()

        def type = LeVaDocumentUseCase.DocumentTypes.TIR

        when:
        usecase.createTIR(project, repo)

        then:
        1 * os.getPodDataForComponent(repo.id) >> createOpenShiftPodDataForComponent()

        then:
        1 * jira.getDocumentChapterData(project.id, type) >> [:]
        1 * levaFiles.getDocumentChapterData(type)
    }
}
