package org.ods.orchestration.usecase

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.ods.orchestration.service.DocGenService
import org.ods.orchestration.service.JiraService
import org.ods.orchestration.service.LeVADocumentChaptersFileService
import org.ods.orchestration.util.DocumentHistory
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PDFUtil
import org.ods.orchestration.util.Project
import org.ods.orchestration.util.SortUtil
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import spock.lang.Unroll
import util.FixtureHelper
import util.OpenShiftHelper
import util.SpecHelper

import java.nio.file.Files
import java.nio.file.NoSuchFileException

import static org.ods.orchestration.usecase.DocumentType.CFTP
import static org.ods.orchestration.usecase.DocumentType.CFTR
import static org.ods.orchestration.usecase.DocumentType.CSD
import static org.ods.orchestration.usecase.DocumentType.DIL
import static org.ods.orchestration.usecase.DocumentType.DTP
import static org.ods.orchestration.usecase.DocumentType.DTR
import static org.ods.orchestration.usecase.DocumentType.IVP
import static org.ods.orchestration.usecase.DocumentType.IVR
import static org.ods.orchestration.usecase.DocumentType.OVERALL_DTR
import static org.ods.orchestration.usecase.DocumentType.OVERALL_TIR
import static org.ods.orchestration.usecase.DocumentType.RA
import static org.ods.orchestration.usecase.DocumentType.SSDS
import static org.ods.orchestration.usecase.DocumentType.TCP
import static org.ods.orchestration.usecase.DocumentType.TCR
import static org.ods.orchestration.usecase.DocumentType.TIP
import static org.ods.orchestration.usecase.DocumentType.TIR
import static org.ods.orchestration.usecase.DocumentType.TRC
import static util.FixtureHelper.createJUnitXMLTestResults
import static util.FixtureHelper.createProject
import static util.FixtureHelper.createProjectBuildEnvironment
import static util.FixtureHelper.createSockShopJUnitXmlTestResults

@Slf4j
class LeVADocumentUseCaseSpec extends SpecHelper {

    @Rule
    public TemporaryFolder tempFolder

    Project project
    IPipelineSteps steps
    IPipelineSteps stepsNoWip
    MROPipelineUtil util
    DocGenService docGen
    JenkinsService jenkins
    JiraUseCase jiraUseCase
    JUnitTestReportsUseCase junit
    LeVADocumentChaptersFileService levaFiles
    NexusService nexus
    OpenShiftService os
    PDFUtil pdf
    SonarQubeUseCase sq
    LeVADocumentUseCase usecase
    ILogger logger
    DocumentHistory docHistory
    BitbucketTraceabilityUseCase bbt

    def setup() {
        project = Spy(createProject())
        project.buildParams.targetEnvironment = "dev"
        project.buildParams.targetEnvironmentToken = "D"
        project.buildParams.version = "WIP"

        steps = Spy(util.PipelineSteps)
        stepsNoWip = Spy(util.PipelineSteps)
        util = Mock(MROPipelineUtil)
        docGen = Mock(DocGenService)
        jenkins = Mock(JenkinsService)
        jiraUseCase = Mock(JiraUseCase)
        junit = Spy(new JUnitTestReportsUseCase(project, steps))
        levaFiles = Mock(LeVADocumentChaptersFileService)
        nexus = Mock(NexusService)
        os = Mock(OpenShiftService)
        pdf = Mock(PDFUtil)
        sq = Mock(SonarQubeUseCase)
        logger =  new org.ods.core.test.LoggerStub(log)
        ServiceRegistry.instance.add(Logger, logger)
        bbt = Mock(BitbucketTraceabilityUseCase)
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))
        project.getOpenShiftApiUrl() >> 'https://api.dev-openshift.com'
        project.getDocumentTrackingIssuesForHistory(_) >> [[key: 'ID-01', status: 'TODO']]
        ServiceRegistry.instance.add(Logger, logger)


        docHistory = new DocumentHistory(steps, logger, 'D', 'SSD')
        steps.readFile(_) >> { Map args ->
            if (args.file ==~ 'projectData/documentHistory-[DQP]-.*\\.json') {
                throw new NoSuchFileException(args.file)
            }
        }
        docHistory.load(project.data.jira, [])
        usecase.getAndStoreDocumentHistory(*_) >> docHistory
        jenkins.unstashFilesIntoPath(_, _, "SonarQube Report") >> true
        steps.getEnv() >> ['RELEASE_PARAM_VERSION': 'WIP', 'BUILD_NUMBER': '10', 'BUILD_URL': 'http://jenkins-project-cd/job/10', 'JOB_NAME': 'project-cd/project-cd-releasemanager-master']
        stepsNoWip.getEnv() >> ['RELEASE_PARAM_VERSION': 'CHG00001']
    }

    def "compute test discrepancies"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        def name = "myTests"

        when:
        def testIssues = []
        def testResults = [
            testsuites: [
                [
                    testcases: []
                ]
            ]
        ]

        def result = usecase.computeTestDiscrepancies(name, testIssues, testResults)

        then:
        result.discrepancies == "No discrepancies found."
        result.conclusion.summary == "Complete success, no discrepancies"
        result.conclusion.statement == "It is determined that all steps of the ${name} have been successfully executed and signature of this report verifies that the tests have been performed according to the plan. No discrepancies occurred."

        // a single, successful testcase
        when:
        testIssues = []
        testResults = [
            testsuites: [
                [
                    testcases: [
                        [
                            name: "JIRA1_my-testcase-1",
                        ]
                    ]
                ]
            ]
        ]

        result = usecase.computeTestDiscrepancies(name, testIssues, testResults)

        then:
        result.discrepancies == "No discrepancies found."
        result.conclusion.summary == "Complete success, no discrepancies"
        result.conclusion.statement == "It is determined that all steps of the ${name} have been successfully executed and signature of this report verifies that the tests have been performed according to the plan. No discrepancies occurred."

        // a single testcase with an error
        when:
        testIssues = []
        testResults = [
            testsuites: [
                [
                    testcases: [
                        [
                            name: "JIRA1_my-testcase-1",
                            error: [ text: "This is an error." ]
                        ]
                    ]
                ]
            ]
        ]

        result = usecase.computeTestDiscrepancies(name, testIssues, testResults)

        then:
        result.discrepancies == "The following major discrepancies were found during testing. Other failed tests: 1."
        result.conclusion.summary == "No success - major discrepancies found"
        result.conclusion.statement == "Some discrepancies found as tests did fail."

        // a single testcase with a failure
        when:
        testIssues = []
        testResults = [
            testsuites: [
                [
                    testcases: [
                        [
                            name: "JIRA1_my-testcase-1",
                            failure: [ text: "This is a failure." ]
                        ]
                    ]
                ]
            ]
        ]

        result = usecase.computeTestDiscrepancies(name, testIssues, testResults)

        then:
        result.discrepancies == "The following major discrepancies were found during testing. Other failed tests: 1."
        result.conclusion.summary == "No success - major discrepancies found"
        result.conclusion.statement == "Some discrepancies found as tests did fail."

        when:
        // only successful testIssues
        testIssues = [
            [ key: "JIRA-1" ]
        ]
        testResults = [
            testsuites: [
                [
                    testcases: [
                        [
                            name: "JIRA1_my-testcase-1"
                        ]
                    ]
                ]
            ]
        ]

        result = usecase.computeTestDiscrepancies(name, testIssues, testResults)

        then:
        result.discrepancies == "No discrepancies found."
        result.conclusion.summary == "Complete success, no discrepancies"
        result.conclusion.statement == "It is determined that all steps of the ${name} have been successfully executed and signature of this report verifies that the tests have been performed according to the plan. No discrepancies occurred."

        when:
        // a single testIssue with an error
        testIssues = [
            [ key: "JIRA-1" ]
        ]
        testResults = [
            testsuites: [
                [
                    testcases: [
                        [
                            name: "JIRA1_my-testcase-1",
                            error: [ text: "This is an error." ]
                        ]
                    ]
                ]
            ]
        ]

        result = usecase.computeTestDiscrepancies(name, testIssues, testResults)

        then:
        result.discrepancies == "The following major discrepancies were found during testing. Failed tests: JIRA-1."
        result.conclusion.summary == "No success - major discrepancies found"
        result.conclusion.statement == "Some discrepancies found as tests did fail."

        when:
        // a single testIssue with a failure
        testIssues = [
            [ key: "JIRA-1" ]
        ]
        testResults = [
            testsuites: [
                [
                    testcases: [
                        [
                            name: "JIRA1_my-testcase-1",
                            failure: [ text: "This is a failure." ]
                        ]
                    ]
                ]
            ]
        ]

        result = usecase.computeTestDiscrepancies(name, testIssues, testResults)

        then:
        result.discrepancies == "The following major discrepancies were found during testing. Failed tests: JIRA-1."
        result.conclusion.summary == "No success - major discrepancies found"
        result.conclusion.statement == "Some discrepancies found as tests did fail."

        when:
        // two testIssues with an error and a failure
        testIssues = [
            [ key: "JIRA-1" ], [ key: "JIRA-2" ]
        ]
        testResults = [
            testsuites: [
                [
                    testcases: [
                        [
                            name: "JIRA1_my-testcase-1",
                            error: [ text: "This is an error." ]
                        ]
                    ]
                ],
                [
                    testcases: [
                        [
                            name: "JIRA2_my-testcase-2",
                            failure: [ text: "This is a failure." ]
                        ]
                    ]
                ]
            ]
        ]

        result = usecase.computeTestDiscrepancies(name, testIssues, testResults)

        then:
        result.discrepancies == "The following major discrepancies were found during testing. Failed tests: JIRA-1, JIRA-2."
        result.conclusion.summary == "No success - major discrepancies found"
        result.conclusion.statement == "Some discrepancies found as tests did fail."

        when:
        // an unexecuted testIssue
        testIssues = [
            [ key: "JIRA-1" ]
        ]
        testResults = [
            testsuites: []
        ]

        result = usecase.computeTestDiscrepancies(name, testIssues, testResults)

        then:
        result.discrepancies == "The following major discrepancies were found during testing. Unexecuted tests: JIRA-1."
        result.conclusion.summary == "No success - major discrepancies found"
        result.conclusion.statement == "Some discrepancies found as tests were not executed."

        when:
        // two testIssues with an error, and an unexecuted
        testIssues = [
            [ key: "JIRA-1" ], [ key: "JIRA-2" ]
        ]
        testResults = [
            testsuites: [
                [
                    testcases: [
                        [
                            name: "JIRA1_my-testcase-1",
                            error: [ text: "This is an error." ]
                        ]
                    ]
                ]
            ]
        ]

        result = usecase.computeTestDiscrepancies(name, testIssues, testResults)

        then:
        result.discrepancies == "The following major discrepancies were found during testing. Failed tests: JIRA-1. Unexecuted tests: JIRA-2."
        result.conclusion.summary == "No success - major discrepancies found"
        result.conclusion.statement == "Some discrepancies found as tests did fail and others were not executed."

        when:
        // an erroneous testIssue and a failing extraneous testcase
        testIssues = [
            [ key: "JIRA-1" ]
        ]
        testResults = [
            testsuites: [
                [
                    testcases: [
                        [
                            name: "JIRA1_my-testcase-1",
                            error: [ text: "This is an error." ]
                        ]
                    ]
                ],
                [
                    testcases: [
                        [
                            name: "my-testcase-2",
                            failure: [ text: "This is an error." ]
                        ]
                    ]
                ]
            ]
        ]

        result = usecase.computeTestDiscrepancies(name, testIssues, testResults)

        then:
        result.discrepancies == "The following major discrepancies were found during testing. Failed tests: JIRA-1. Other failed tests: 1."
        result.conclusion.summary == "No success - major discrepancies found"
        result.conclusion.statement == "Some discrepancies found as tests did fail."
    }

    def "get correct templates for GAMP category sensitive documents"() {
        given:
        project.getCapability("LeVADocs").GAMPCategory = "999"

        expect:
        usecase.getDocumentTemplateName(documentType) == template

        where:
        documentType                                        || template
        CSD as String || (CSD as String) + "-999"
        SSDS as String || (SSDS as String) + "-999"
        CFTP as String || (CFTP as String) + "-999"
        CFTR as String || (CFTR as String) + "-999"
        RA as String   || (RA as String)
    }

    def "create CSD"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        // Argument Constraints
        def documentType = CSD as String

        // Stubbed Method Responses
        def chapterData = ["sec1": [content: "myContent", status: "DONE", key:"DEMO-1"]]
        def uri = "http://nexus"
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        def jiraDataItem1 = project.getSystemRequirements().first().cloneIt()
        jiraDataItem1.put("key", "1")
        jiraDataItem1.put("name", "1")
        jiraDataItem1.get("configSpec").put("name", "cs1")
        jiraDataItem1.get("funcSpec").put("name", "fs1")

        def jiraDataItem2 = project.getSystemRequirements().first().cloneIt()
        jiraDataItem2.put("key", "2")
        jiraDataItem2.put("name", "2")
        jiraDataItem2.put("gampTopic", "gamP topic")
        jiraDataItem2.get("configSpec").put("name", "cs2")
        jiraDataItem2.get("funcSpec").put("name", "fs2")

        def jiraDataItem3 = project.getSystemRequirements().first().cloneIt()
        jiraDataItem3.put("key", "3")
        jiraDataItem3.put("name", "3")
        jiraDataItem3.put("gampTopic", "GAMP TOPIC")
        jiraDataItem3.get("configSpec").put("name", "cs3")
        jiraDataItem3.get("funcSpec").put("name", "fs3")

        def requirements = [jiraDataItem1, jiraDataItem2, jiraDataItem3]
        project.data.jira.discontinuationsPerType = [requirements:[]]
        when:
        usecase.createCSD()

        then:
        1 * usecase.getDocumentSections(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText

        then:
        1 * usecase.getDocumentTemplateName(documentType) >> documentTemplate
        1 * project.getSystemRequirements() >> requirements
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.createDocument(documentType, null, _, [:], _, documentTemplate, watermarkText) >> uri
        1 * usecase.getSectionsNotDone(documentType) >> []
        1 * usecase.updateJiraDocumentationTrackingIssue(documentType, uri, "${docHistory.getVersion()}")
    }

    def "create TRC"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        // Test Parameters
        def xmlFile = Files.createTempFile("junit", ".xml").toFile()
        xmlFile << "<?xml version='1.0' ?>\n" + createJUnitXMLTestResults()

        def testReportFiles = [xmlFile]
        def testResults = new JUnitTestReportsUseCase(project, steps).parseTestReportFiles(testReportFiles)
        def data = [
            tests: [
                acceptance : [
                    testReportFiles: testReportFiles,
                    testResults    : testResults
                ],
                installation: [
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
        def documentType = TRC as String

        // Stubbed Method Responses
        def chapterData = ["sec1": [content: "myContent", status: "DONE", key:"DEMO-1"]]
        def uri = "http://nexus"
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        when:
        usecase.createTRC(null, data)

        then:
        1 * usecase.getDocumentSections(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)

        then:
        1 * usecase.getDocumentTemplateName(documentType) >> documentTemplate
        2 * project.getSystemRequirements()
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], _)
        1 * usecase.createDocument(documentType, null, _, [:], _, documentTemplate, watermarkText) >> uri
        1 * usecase.getSectionsNotDone(documentType) >> []
        1 * usecase.updateJiraDocumentationTrackingIssue(documentType, uri, "${docHistory.getVersion()}")
    }

    def "create DIL"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        // Argument Constraints
        def documentType = DIL as String
        def uri = "http://nexus"
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        when:
        usecase.createDIL()

        then:
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText

        then:
        1 * usecase.getDocumentTemplateName(documentType) >> documentTemplate
        1 * project.getBugs()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.createDocument(documentType, null, _, [:], _, documentTemplate, watermarkText) >> uri
        1 * usecase.getSectionsNotDone(documentType) >> []
        1 * usecase.updateJiraDocumentationTrackingIssue(documentType, uri)
    }

    def "create DTP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        // Test Parameters
        def repo = project.repositories.first()

        // Argument Constraints
        def documentType = DTP as String

        // Stubbed Method Responses
        def chapterData = ["sec1": [content: "myContent", status: "DONE"]]
        def uri = "http://nexus"
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        when:
        usecase.createDTP(repo)

        then:
        1 * usecase.getDocumentSectionsFileOptional(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText

        then:
        1 * usecase.getDocumentTemplateName(documentType) >> documentTemplate
        1 * project.getAutomatedTestsTypeUnit()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], repo)
        1 * usecase.createDocument(documentType, null, _, [:], _, documentTemplate, watermarkText) >> uri
        1 * usecase.getSectionsNotDone(documentType) >> []
        1 * usecase.updateJiraDocumentationTrackingIssue(documentType, uri, "${docHistory.getVersion()}")
    }

    def "create DTP without Jira"() {
        given:
        project.services.jira = null

        // Test Parameters
        def repo = project.repositories.first()

        // Argument Constraints
        def documentType = DTP as String

        // Stubbed Method Responses
        def chapterData = ["sec1": [content: "myContent", status: "DONE"]]
        def uri = "http://nexus"
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        when:
        usecase.createDTP(repo)

        then:
        1 * usecase.getDocumentSectionsFileOptional(documentType)
        1 * levaFiles.getDocumentChapterData(documentType) >> chapterData
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText

        then:
        1 * usecase.getDocumentTemplateName(documentType) >> documentTemplate
        1 * project.getAutomatedTestsTypeUnit()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], repo)
        1 * usecase.createDocument(documentType, null, _, [:], _, documentTemplate, watermarkText) >> uri
        0 * usecase.getSectionsNotDone(documentType) >> []
        1 * usecase.updateJiraDocumentationTrackingIssue(*_)
    }

    def "create DTR"() {
        given:
        // Test Parameters
        def xmlFile = Files.createTempFile("junit", ".xml").toFile()
        xmlFile << "<?xml version='1.0' ?>\n" + createSockShopJUnitXmlTestResults()

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
        def documentType = DTR as String
        def files = ["raw/${xmlFile.name}": xmlFile.bytes]

        // Stubbed Method Responses
        def chapterData = ["sec1": [content: "myContent", status: "DONE"]]
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        when:
        usecase.createDTR(repo, data)

        then:
        1 * usecase.getDocumentSectionsFileOptional(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText

        then:
        1 * usecase.getDocumentTemplateName(documentType, repo) >> documentTemplate
        1 * project.getAutomatedTestsTypeUnit("Technology-${repo.id}")
        1 * usecase.computeTestDiscrepancies("Development Tests", testIssues, testResults)
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], repo)
        1 * usecase.createDocument(documentType, repo, _, files, _, documentTemplate, watermarkText)

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
        def documentType = DTR as String
        def files = ["raw/${xmlFile.name}": xmlFile.bytes]

        // Stubbed Method Responses
        def buildParams = createProjectBuildEnvironment(env)
        def chapterData = ["sec1": [content: "myContent", status: "DONE"]]
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        when:
        usecase.createDTR(repo, data)

        then:
        1 * usecase.getDocumentSectionsFileOptional(documentType)
        1 * levaFiles.getDocumentChapterData(documentType) >> chapterData
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText

        then:
        1 * usecase.getDocumentTemplateName(documentType, repo) >> documentTemplate
        1 * project.getAutomatedTestsTypeUnit("Technology-${repo.id}") >> testIssues
        1 * usecase.computeTestDiscrepancies("Development Tests", testIssues, testResults)
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], repo)
        1 * usecase.createDocument(documentType, repo, _, files, _, documentTemplate, watermarkText)
    }

    def "create CFTP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        // Argument Constraints
        def documentType = CFTP as String

        // Stubbed Method Responses
        def chapterData = ["sec1": [content: "myContent", status: "DONE"]]
        def uri = "http://nexus"
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        when:
        usecase.createCFTP()

        then:
        1 * usecase.getDocumentSections(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText

        then:
        1 * usecase.getDocumentTemplateName(documentType) >> documentTemplate
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.createDocument(documentType, null, _, [:], _, documentTemplate, watermarkText) >> uri
        1 * usecase.getSectionsNotDone(documentType) >> []
        1 * usecase.updateJiraDocumentationTrackingIssue(documentType, uri, "${docHistory.getVersion()}")
    }

    def "create CFTR"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        // Test Parameters
        def xmlFile = Files.createTempFile("junit", ".xml").toFile()
        xmlFile << "<?xml version='1.0' ?>\n" + createJUnitXMLTestResults()

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
        def documentType = CFTR as String
        def files = [ "raw/${xmlFile.name}": xmlFile.bytes ]

        // Stubbed Method Responses
        def chapterData = ["sec1": [content: "myContent", status: "DONE"]]
        def uri = "http://nexus"
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        when:
        usecase.createCFTR(null, data)

        then:
        1 * usecase.getDocumentSections(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText

        then:
        1 * project.getAutomatedTestsTypeAcceptance() >> acceptanceTestIssues
        1 * project.getAutomatedTestsTypeIntegration() >> integrationTestIssues
        1 * usecase.computeTestDiscrepancies("Integration and Acceptance Tests", SortUtil.sortIssuesByKey(acceptanceTestIssues + integrationTestIssues), junit.combineTestResults([data.tests.acceptance.testResults, data.tests.integration.testResults]), false   )
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.getDocumentTemplateName(documentType) >> documentTemplate
        1 * usecase.createDocument(documentType, null, _, files, null, documentTemplate, watermarkText) >> uri
        1 * usecase.getSectionsNotDone(documentType) >> []
        1 * usecase.updateJiraDocumentationTrackingIssue(documentType, uri, "${docHistory.getVersion()}")

        cleanup:
        xmlFile.delete()
    }

    def "create TCP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        // Argument Constraints
        def documentType = TCP as String

        // Stubbed Method Responses
        def chapterData = ["sec1": [content: "myContent", status: "DONE"]]
        def uri = "http://nexus"
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        when:
        usecase.createTCP()

        then:
        1 * usecase.getDocumentSections(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText

        then:
        1 * project.getAutomatedTestsTypeAcceptance()
        1 * project.getAutomatedTestsTypeIntegration()
        1 * usecase.getDocumentTemplateName(documentType) >> documentTemplate
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.createDocument(documentType, null, _, [:], _, documentTemplate, watermarkText) >> uri
        1 * usecase.getSectionsNotDone(documentType) >> []
        1 * usecase.updateJiraDocumentationTrackingIssue(documentType, uri, "${docHistory.getVersion()}")
    }

    def "create TCR"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        // Test Parameters
        def xmlFile = Files.createTempFile("junit", ".xml").toFile()
        xmlFile << "<?xml version='1.0'?>\n" + createSockShopJUnitXmlTestResults()

        def integrationTestIssues = project.getAutomatedTestsTypeIntegration()
        def acceptanceTestIssues = project.getAutomatedTestsTypeAcceptance()
        def testReportFiles = [xmlFile]
        def testResults = new JUnitTestReportsUseCase(project, steps).parseTestReportFiles(testReportFiles)
        def data = [
            tests: [
                integration: [
                    testReportFiles: testReportFiles,
                    testResults    : testResults
                ],
                acceptance : [
                    testReportFiles: testReportFiles,
                    testResults    : testResults
                ]
            ]
        ]

        // Argument Constraints
        def documentType = TCR as String
        def files = ["raw/${xmlFile.name}": xmlFile.bytes]

        // Stubbed Method Responses
        def chapterData = ["sec1": [content: "myContent", status: "DONE"]]
        def uri = "http://nexus"
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        when:
        usecase.createTCR(null, data)

        then:
        1 * usecase.getDocumentSections(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText

        then:
        1 * project.getAutomatedTestsTypeIntegration() >> integrationTestIssues
        1 * project.getAutomatedTestsTypeAcceptance() >> acceptanceTestIssues
        1 * usecase.getDocumentTemplateName(documentType) >> documentTemplate
        1 * usecase.createDocument(documentType, null, _, [:], null, documentTemplate, watermarkText) >> uri
        1 * usecase.getSectionsNotDone(documentType) >> []
        1 * usecase.updateJiraDocumentationTrackingIssue(documentType, uri, "${docHistory.getVersion()}")
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * jiraUseCase.matchTestIssuesAgainstTestResults(acceptanceTestIssues, testResults, _, _)
        1 * jiraUseCase.matchTestIssuesAgainstTestResults(integrationTestIssues, testResults, _, _)

        cleanup:
        xmlFile.delete()
    }

    def "create IVP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        // Argument Constraints
        def documentType = IVP as String

        // Stubbed Method Responses
        def chapterData = ["sec1": [content: "myContent", status: "DONE"]]
        def uri = "http://nexus"
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        when:
        usecase.createIVP()

        then:
        1 * usecase.getDocumentSections(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText

        then:
        1 * project.getAutomatedTestsTypeInstallation()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.getDocumentTemplateName(documentType) >> documentTemplate
        1 * usecase.createDocument(documentType, null, _, [:], _, documentTemplate, watermarkText) >> uri
        1 * usecase.getSectionsNotDone(documentType) >> []
        1 * usecase.updateJiraDocumentationTrackingIssue(documentType, uri, "${docHistory.getVersion()}")
    }

    def "create IVR"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

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
        def documentType = IVR as String
        def files = ["raw/${xmlFile.name}": xmlFile.bytes]

        // Stubbed Method Responses
        def chapterData = ["sec1": [content: "myContent", status: "DONE"]]
        def uri = "http://nexus"
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        when:
        usecase.createIVR(null, data)

        then:
        1 * usecase.getDocumentSections(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText

        then:
        1 * project.getAutomatedTestsTypeInstallation() >> testIssues
        1 * usecase.computeTestDiscrepancies("Installation Tests", testIssues, testResults)
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.getDocumentTemplateName(documentType) >> documentTemplate
        1 * usecase.createDocument(documentType, null, _, files, null, documentTemplate, watermarkText) >> uri
        1 * usecase.getSectionsNotDone(documentType) >> []
        1 * usecase.updateJiraDocumentationTrackingIssue(documentType, uri, "${docHistory.getVersion()}")

        cleanup:
        xmlFile.delete()
    }

    @Unroll
    def "create SSDS #scenario"() {
        given:
        // Data from project-jira-data.json#techSpecs[NET-128]
        project.data.jira.techSpecs["NET-128"] = new JsonSlurper().parseText(techSpecsParam)

        def systemDesignSpec = project.data.jira.techSpecs["NET-128"]["systemDesignSpec"]

        // needed for unroll and overwrite types
        def testRepo = project.repositories.find {repo -> repo.id == 'demo-app-catalogue'}
        testRepo.type = odsRepoType
        testRepo.doInstall = doInstall
        this.logger.debug("repos: ${testRepo} / ${odsRepoType} / ${componentTypeLong} / ${doInstall}")

        def version = (odsRepoType == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE) ? 'WIP' : '1.0'

        def expectedSpecifications = systemDesignSpec
                                    ? ["key":"NET-128",
                                      "req_key":"NET-125",
                                      "description":systemDesignSpec]
                                    : null
        def expectedComponents = ["key":"Technology-demo-app-catalogue",
                                  "nameOfSoftware":"demo-app-catalogue",
                                  "componentType":componentTypeLong,
                                  "componentId":"N/A - part of this application",
                                  "description":"Some description for demo-app-catalogue",
                                  "supplier":"https://github.com/microservices-demo/",
                                  "version":version,
                                  "references":"N/A",
                                  "doInstall":doInstall]
        def expectedModules = ["key":"Technology-demo-app-catalogue",
                               "componentName":"Technology-demo-app-catalogue",
                               "componentId":"N/A - part of this application",
                               "componentType":componentTypeLong,
                               "doInstall":doInstall,
                               "odsRepoType":odsRepoType,
                               "description":"Some description for demo-app-catalogue",
                               "nameOfSoftware":"demo-app-catalogue",
                               "references":"N/A",
                               "supplier":"https://github.com/microservices-demo/",
                               "version":version,
                               "requirements":[
                                   ["gampTopic":"performance requirements",
                                    "requirementsofTopic":[
                                        ["key":"NET-125",
                                         "name":"As a user I want my payments to be processed quickly",
                                         "reqDescription":"Payments have to be conducted quickly to keep up with the elevated expectations of customers",
                                         "gampTopic":"performance requirements"]]]],
                               "requirementKeys":["NET-125"],
                               "softwareDesignSpecKeys":["NET-128"],
                               "softwareDesignSpec":[
                                   ["key":"NET-128",
                                    "softwareDesignSpec":"Implement the system using a loosely coupled micro services architecture for improved extensibility and maintainability."
                                   ]]]

        if (!doInstall) {
            expectedModules = null
        }

        def expectedDocs = ["number":"1", "documents":["SSDS"], "section":"sec1", "version":"1.0", "key":"DOC-1", "name": "name", "content":"myContent", "show": true]

        log.info "Using temporal folder:${tempFolder.getRoot()}"
        steps.env.BUILD_ID = "1"
        steps.env.WORKSPACE = tempFolder.getRoot().absolutePath
        FileUtils.copyDirectory(new FixtureHelper().getResource("Test-1.pdf").parentFile, tempFolder.getRoot());
        def pdfDoc = new FixtureHelper().getResource("Test-1.pdf").bytes

        def documentType = SSDS as String
        def uri = new URI("http://nexus")
        def pdfUtil = new PDFUtil()
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        util = Spy(new MROPipelineUtil(project, steps, null, logger))
        usecase = Spy(
            new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdfUtil, sq, bbt, logger)
        )

        when:
        def answer = usecase.createSSDS()

        then:
        answer == uri.toString()

        // headsup -> if any of the asserts fail - you'll get a nullpointer (doc watermarks can't be rendered, null doc!!!)
        // java.lang.RuntimeException: Error: unable to add watermark to PDF document: Ambiguous method overloading for method org.apache.pdfbox.pdmodel.PDDocument#load.
        // Cannot resolve which method to invoke for [null] due to overlapping prototypes between:
        1 * docGen.createDocument(
            "SSDS-5",
            "1.0",
            {
                log.info("should / is: \n -${it.data.sections.sec1}\n -${expectedDocs}")
                assert it.data.sections.sec1 == expectedDocs
                log.info("should / is: \n -${it.data.sections.sec5s1.components[0]}\n -${expectedComponents}")
                assert it.data.sections.sec5s1.components[0] == expectedComponents
                log.info("should / is: \n -${it.data.sections.sec3s1.specifications[0]}\n -${expectedSpecifications}")
                assert it.data.sections.sec3s1.specifications[0] == expectedSpecifications
                log.info("should / is: \n -${it.data.sections.sec10.modules[0]}\n -${expectedModules}")
                assert it.data.sections.sec10.modules[0] == expectedModules
            }
        ) >> pdfDoc // TODO replace this pdf with the real expected one
        1 * nexus.storeArtifact("leva-documentation",
            "net-WIP",
            "SSDS-net-WIP-1.zip",
            !null,
            "application/zip"
        ) >> uri
        1 * usecase.updateJiraDocumentationTrackingIssue(documentType, uri.toString(), "${docHistory.getVersion()}")
        // TODO compare the pdf result with the expected one (https://github.com/vinsguru/pdf-util)

        where:
        scenario << ["Neither systemDesignSpec nor softwareDesignSpec",
                     "Only systemDesignSpec",
                     "Both softwareDesignSpec & systemDesignSpec"]

        odsRepoType << ['ods-test', 'ods-saas-service', 'ods']
        componentTypeLong = LeVADocumentUseCase.INTERNAL_TO_EXT_COMPONENT_TYPES.get(odsRepoType)
        doInstall = MROPipelineUtil.PipelineConfig.INSTALLABLE_REPO_TYPES.contains(odsRepoType)

        techSpecsParam << ['''
          {
            "key": "NET-128",
            "id": "128",
            "version": "1.0",
            "name": "Containerized Infrastructure",
            "description": "The system should be set up as containerized infrastructure in the openshift cluster.",
            "status": "IN DESIGN",
            "components": ["Technology-demo-app-catalogue"],
            "requirements": ["NET-125"],
            "risks": ["NET-126"],
            "tests": ["NET-127"]
        }''',
                     '''
          {
            "key": "NET-128",
            "id": "128",
            "version": "1.0",
            "name": "Containerized Infrastructure",
            "description": "The system should be set up as containerized infrastructure in the openshift cluster.",
            "status": "IN DESIGN",
            "systemDesignSpec": "Use containerized infrastructure to support quick and easy provisioning of a multitude of micro services that do one thing only and one thing right and fast.",
            "components": ["Technology-demo-app-catalogue"],
            "requirements": ["NET-125"],
            "risks": ["NET-126"],
            "tests": ["NET-127"]
        }''',
                     '''
          {
            "key": "NET-128",
            "id": "128",
            "version": "1.0",
            "name": "Containerized Infrastructure",
            "description": "The system should be set up as containerized infrastructure in the openshift cluster.",
            "status": "IN DESIGN",
            "systemDesignSpec": "Use containerized infrastructure to support quick and easy provisioning of a multitude of micro services that do one thing only and one thing right and fast.",
            "softwareDesignSpec": "Implement the system using a loosely coupled micro services architecture for improved extensibility and maintainability.",
            "components": ["Technology-demo-app-catalogue"],
            "requirements": ["NET-125"],
            "risks": ["NET-126"],
            "tests": ["NET-127"]
        }''']

    }

    def "create RA"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        // Argument Constraints
        def documentType = RA as String

        // Stubbed Method Responses
        def chapterData = ["sec1": [content: "myContent", status: "DONE"]]
        def uri = "http://nexus"
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        when:
        usecase.createRA()

        then:
        1 * usecase.getDocumentSections(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText

        then:
        3 * project.getRisks()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], null)
        1 * usecase.getDocumentTemplateName(documentType) >> documentTemplate
        1 * usecase.createDocument(documentType, null, _, [:], _, documentTemplate, watermarkText) >> uri
        1 * usecase.getSectionsNotDone(documentType) >> []
        1 * usecase.updateJiraDocumentationTrackingIssue(documentType, uri, "${docHistory.getVersion()}")
    }

    def "create TIP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        // Argument Constraints
        def documentType = TIP as String

        // Stubbed Method Responses
        def chapterData = ["sec1": [content: "myContent", status: "DONE"]]
        def uri = "http://nexus"
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        when:
        usecase.createTIP()

        then:
        1 * usecase.getDocumentSectionsFileOptional(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.getDocumentTemplateName(documentType) >> documentTemplate
        1 * usecase.createDocument(documentType, null, _, [:], _, documentTemplate, watermarkText) >> uri
        1 * usecase.getSectionsNotDone(documentType) >> []
        1 * usecase.updateJiraDocumentationTrackingIssue(documentType, uri, "${docHistory.getVersion()}")
    }

    def "create TIP without Jira"() {
        given:
        project.services.jira = null

        // Argument Constraints
        def documentType = TIP as String

        // Stubbed Method Responses
        def chapterData = ["sec1": [content: "myContent", status: "DONE"]]
        def uri = "http://nexus"
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        when:
        usecase.createTIP()

        then:
        1 * usecase.getDocumentSectionsFileOptional(documentType)
        1 * levaFiles.getDocumentChapterData(documentType) >> chapterData
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.getDocumentTemplateName(documentType) >> documentTemplate
        1 * usecase.createDocument(documentType, null, _, [:], _, documentTemplate, watermarkText) >> uri
        0 * usecase.getSectionsNotDone(documentType) >> []
        1 * usecase.updateJiraDocumentationTrackingIssue(*_)
    }

    def "create TIR"() {
        given:
        // Argument Constraints
        def documentType = TIR as String

        // Stubbed Method Responses
        def chapterData = ["sec1": [content: "myContent", status: "DONE"]]
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        when:
        usecase.createTIR(repo, data)

        then:
        // Jira enabled
        2 * jiraUseCase.jira >> Mock(JiraService)
        1 * usecase.getDocumentSectionsFileOptional(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], repo)
        1 * usecase.getDocumentTemplateName(documentType, repo) >> documentTemplate
        0 * usecase.obtainCodeReviewReport(_) >> []
        1 * usecase.createDocument(documentType, repo, _, [:], _, documentTemplate, watermarkText) >> {
            assert it[2]."deploymentMean"
            assert it[2]."legacy" == legacy
        }

        where:
        legacy  | data                                  | repo
        false   | FixtureHelper.createTIRDataHelm()     | FixtureHelper.createTIRRepoHelm()
        true    | FixtureHelper.createTIRDataTailor()   | FixtureHelper.createTIRRepoTailor()
    }

    def "create TIR without Jira"() {
        given:
        // Argument Constraints
        def documentType = TIR as String

        // Stubbed Method Responses
        def chapterData = ["sec1": [content: "myContent", status: "DONE"]]
        def documentTemplate = "template"
        def watermarkText = "WATERMARK"

        when:
        usecase.createTIR(repo, data)

        then:
        // No Jira enabled
        2 * jiraUseCase.jira >> null
        1 * project.getDocumentChaptersForDocument(documentType) >> []
        1 * usecase.getDocumentSectionsFileOptional(documentType)
        1 * levaFiles.getDocumentChapterData(documentType) >> chapterData
        1 * usecase.getWatermarkText(documentType, _) >> watermarkText
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], repo)
        1 * usecase.getDocumentTemplateName(documentType, repo) >> documentTemplate
        1 * usecase.obtainCodeReviewReport(_) >> []
        1 * usecase.createDocument(documentType, repo, _, [:], _, documentTemplate, watermarkText) >> {
            assert it[2]."deploymentMean"
            assert it[2]."legacy" == legacy
        }

        where:
        legacy  | data                                  | repo
        false   | FixtureHelper.createTIRDataHelm()     | FixtureHelper.createTIRRepoHelm()
        true    | FixtureHelper.createTIRDataTailor()   | FixtureHelper.createTIRRepoTailor()
    }

    def "createTIR for infra component"() {
        when: 'Preparing deploymentMean info for infra component'
        def deploymentMeanInfo = usecase.prepareDeploymentMeanInfo(null, 'dev')

        then: 'The obtained info is null or empty, but no exception is thrown'
        noExceptionThrown()
        !deploymentMeanInfo

        when: 'Preparing deployment info for infra component'
        def deploymentInfo = usecase.prepareDeploymentInfo(null)

        then: 'The obtained info is null or empty, but no exception is thrown'
        noExceptionThrown()
        !deploymentInfo
    }

    def "assemble deploymentMean and deploymentInfo for TIR with helm"() {
        given:
        def deployments = FixtureHelper.createTIRRepoHelm().data.openshift.deployments
        OpenShiftHelper.isDeploymentKind(*_) >> true

        def expectedMeanInfo = [
            'namespace': 'myodsproject-dev',
            'type': 'helm',
            'descriptorPath': 'chart',
            'defaultCmdLineArgs': '--install --atomic',
            'additionalCmdLineArgs': '--additional-flag-1 --additional-flag-2',
            'configParams': '''<ul class='inner-ul'><li>registry: image-registry.openshift.svc:1000</li><li>componentId: backend-helm-monorepo</li></ul>''',
            'configFiles': '''<ul class='inner-ul'><li>values.yaml</li></ul>''',
            'envConfigFiles': '''<ul class='inner-ul'><li>values1.dev.yaml</li><li>values2.dev.yaml</li></ul>''',
            'deploymentStatus':  [
                'deployStatus': 'Successfully deployed',
                'resultMessage': 'Upgrade complete',
                'lastDeployed': '2024-10-31T11:10:27.478860933Z',
                'resources': '''<ul class='inner-ul'><li>Deployment: <ul class='inner-ul'><li>backend-helm-monorepo-chart-component-a</li><li>backend-helm-monorepo-chart-component-b</li></ul></li><li>Service: <ul class='inner-ul'><li>backend-helm-monorepo-chart</li></ul></li></ul>''',
            ] ,
        ]

        def expectedDeploymentInfo = [
            'backend-helm-monorepo-chart-component-b':  [
                'podNamespace': 'myodsproject-dev',
                'podStatus': 'Running',
                'deploymentId': 'backend-helm-monorepo-chart-component-b-567ff4f8f6',
                'podMetaDataCreationTimestamp': '2024-10-31T11:10:28Z',
                'containers':   [
                    'chart-component-b': 'image-registry.openshift.svc:1000/myodsproject-dev/backend-helm-monorepo-component-b@sha256:10002345abcde',
                ] ,
            ] ,
            'backend-helm-monorepo-chart-component-a':  [
                'podNamespace': 'myodsproject-dev',
                'podStatus': 'Running',
                'deploymentId': 'backend-helm-monorepo-chart-component-a-5ffd9c7cbd',
                'podMetaDataCreationTimestamp': '2024-10-31T11:10:28Z',
                'containers':   [
                    'chart-component-a': 'image-registry.openshift.svc:1000/myodsproject-dev/backend-helm-monorepo-component-a@sha256:10002345abcde',
                ] ,
            ] ,
        ]

        when:
        def deploymentMeanInfo = usecase.prepareDeploymentMeanInfo(deployments, 'dev')
        def deploymentInfo = usecase.prepareDeploymentInfo(deployments)

        then:
        deploymentMeanInfo == expectedMeanInfo
        deploymentInfo == expectedDeploymentInfo
    }

    def "assemble deploymentMean for TIR with helm and default values"() {
        given:
        def deployments = FixtureHelper.createTIRRepoHelm().data.openshift.deployments
        def deploymentMean = deployments.'backend-helm-monorepo-chart-component-b-deploymentMean'
        OpenShiftHelper.isDeploymentKind(*_) >> true

        deploymentMean.namespace = ''
        deploymentMean.chartDir = ''
        deploymentMean.helmDefaultFlags = []
        deploymentMean.helmAdditionalFlags = []
        deploymentMean.helmValues = [:]
        deploymentMean.helmValuesFiles = []
        deploymentMean.helmEnvBasedValuesFiles = []
        deploymentMean.helmStatus.status = 'SOME OTHER STATUS'
        deploymentMean.helmStatus.resourcesByKind = [:]

        when:
        def deploymentMeanInfo = usecase.prepareDeploymentMeanInfo(deployments, 'dev')

        then:
        deploymentMeanInfo.namespace == 'None'
        deploymentMeanInfo.descriptorPath == '.'
        deploymentMeanInfo.defaultCmdLineArgs == 'None'
        deploymentMeanInfo.additionalCmdLineArgs == 'None'
        deploymentMeanInfo.configParams =='None'
        deploymentMeanInfo.configFiles =='None'
        deploymentMeanInfo.envConfigFiles == 'None'
        deploymentMeanInfo.deploymentStatus.deployStatus == 'SOME OTHER STATUS'
        deploymentMeanInfo.deploymentStatus.resources == 'None'
    }

    def "assemble deploymentMean and deploymentInfo for TIR with tailor"() {
        given:
        def deployments = FixtureHelper.createTIRRepoTailor().data.openshift.deployments
        OpenShiftHelper.isDeploymentKind(*_) >> true

        def expectedMeanInfo = [
            'tailorParamFile': 'a-param-file.yaml',
            'tailorParams': [ 'fake-param1', 'fake-param2' ] ,
            'selector': 'app=myodsproject-flask-backend',
            'type': 'tailor',
            'tailorSelectors':  [
                'selector': 'app=myodsproject-flask-backend',
                'exclude': 'bc,is',
            ] ,
            'tailorPreserve': [ 'fake-preserve1', 'fake-preserve2' ] ,
            'tailorVerify': true,
        ]

        def expectedDeploymentInfo = [
            'flask-backend':  [
                'podNamespace': 'myodsproject-dev',
                'podStatus': 'Running',
                'deploymentId': 'flask-backend-2',
                'podMetaDataCreationTimestamp': '2024-10-31T11:09:56Z',
                'containers':   [
                    'flask-backend': 'image-registry.openshift.svc:1000/myodsproject-dev/flask-backend@sha256:10002345abcde',
                ] ,
            ] ,
        ]

        when:
        def deploymentMeanInfo = usecase.prepareDeploymentMeanInfo(deployments, 'dev')
        def deploymentInfo = usecase.prepareDeploymentInfo(deployments)

        then:
        deploymentMeanInfo == expectedMeanInfo
        deploymentInfo == expectedDeploymentInfo
    }

    def "assemble deploymentMean for TIR with tailor and default values"() {
        given:
        def deployments = FixtureHelper.createTIRRepoTailor().data.openshift.deployments
        def deploymentMean = deployments.'flask-backend-deploymentMean'
        OpenShiftHelper.isDeploymentKind(*_) >> true

        // These are special cases to be replaced with a custom value when a falsy value is present
        deploymentMean.tailorParamFile = ''
        deploymentMean.tailorParams = []
        deploymentMean.tailorPreserve = []

        // This one is the general case
        deploymentMean.tailorSelectors = []

        when:
        def deploymentMeanInfo = usecase.prepareDeploymentMeanInfo(deployments, 'dev')

        then:
        deploymentMeanInfo.tailorParamFile == 'None'
        deploymentMeanInfo.tailorParams == 'None'
        deploymentMeanInfo.tailorPreserve == 'No extra resources specified to be preserved'
        deploymentMeanInfo.tailorSelectors == 'N/A'
    }

    def "create overall DTR"() {
        given:
        // Argument Constraints
        def documentType = DTR as String
        def documentTypeName = OVERALL_DTR as String

        // Stubbed Method Responses
        def uri = "http://nexus"

        when:
        usecase.createOverallDTR()

        then:
        1 * project.getDocumentVersionFromHistories(*_) >> docHistory.getVersion()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentTypeName])
        1 * usecase.createOverallDocument("Overall-Cover", documentType, _, _, _) >> uri
        1 * usecase.updateJiraDocumentationTrackingIssue(documentType, uri, "${docHistory.getVersion()}")
    }

    def "create overall TIR"() {
        given:
        // Argument Constraints
        def documentType = TIR as String
        def documentTypeName = OVERALL_TIR as String

        // Stubbed Method Responses
        def uri = "http://nexus"

        when:
        usecase.createOverallTIR()

        then:
        1 * project.getDocumentVersionFromHistories(*_) >> docHistory.getVersion()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentTypeName])
        1 * usecase.createOverallDocument("Overall-TIR-Cover", documentType, _, _, _) >> uri
        1 * usecase.updateJiraDocumentationTrackingIssue(documentType, uri, "${docHistory.getVersion()}")
    }

    def "get supported documents"() {
        when:
        def result = usecase.getSupportedDocuments()

        then:
        result.size() == 18

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
        result.contains("TCP")
        result.contains("TCR")
        result.contains("RA")
        result.contains("TIP")
        result.contains("TIR")
        result.contains("TRC")
        result.contains("OVERALL_DTR")
        result.contains("OVERALL_IVR")
        result.contains("OVERALL_TIR")
    }

    def "update Jira documentation tracking issue in DEV"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        def documentType = "myType"
        def uri = "myMessage"

        def trackingIssues = [
            "TRK-1"      : [
                "key"        : "TRK-1",
                "name"       : "Document Demo",
                "description": "Tracking issue in Q",
                "status"     : "PENDING",
                "labels"     : [
                    "Doc:${documentType}"
                ]
            ]
        ]

        project.data.jira.trackingDocs << trackingIssues

        when:
        usecase.updateJiraDocumentationTrackingIssue(documentType, uri)

        then:
        1 * project.getWIPDocChaptersForDocument(documentType) >> ["TRK-1"]
        1 * jiraUseCase.jira.appendCommentToIssue("TRK-1", _)
    }

    def "update Jira documentation tracking issue when no issues found in project.data.docs"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        def documentType = "myTypeNotFound"
        def message = "myMessage"

        when:
        usecase.updateJiraDocumentationTrackingIssue(documentType, message)

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Error: no Jira tracking issue associated with document type '${documentType}'.")
    }

    def "update Jira documentation tracking issue with 2 chapters issue not DONE yet"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        def documentType = "myTypeNotDone"
        def uri = "http://"

        def sectionNotDone = ["DOC-1", "DOC-3"]

        def trackingIssues = [
            "TRK-1"      : [
                "key"        : "TRK-1",
                "name"       : "Document Demo",
                "description": "Undone document tracking issue in Q",
                "status"     : "PENDING",
                "labels"     : [
                    "Doc:${documentType}"
                ]
            ],
            "TRK-2"      : [
                "key"        : "TRK-2",
                "name"       : "Document Demo",
                "description": "Undone document tracking issue in P",
                "status"     : "PENDING",
                "labels"     : [
                    "Doc:${documentType}"
                ]
            ]
        ]

        project.data.jira.trackingDocs << trackingIssues

        when:
        usecase.updateJiraDocumentationTrackingIssue(documentType, uri)

        then:
        1 * usecase.getSectionsNotDone(documentType) >> sectionNotDone

        then:
        1 * jiraUseCase.jira.appendCommentToIssue("TRK-1", "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}. Attention: this document is work in progress! See issues: DOC-1, DOC-3")

        then:
        1 * jiraUseCase.jira.appendCommentToIssue("TRK-2", "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}. Attention: this document is work in progress! See issues: DOC-1, DOC-3")
    }

    def "update document version in Jira documentation tracking issue when official release and no WIP issues"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        def documentType = "CSD"
        def message = "myMessage"

        def trackingIssues = [
            "TRK-1"      : [
                "key"        : "TRK-1",
                "name"       : "Document Demo",
                "description": "Tracking issue in Q",
                "status"     : "PENDING",
                "labels"     : [
                    "Doc:${documentType}"
                ]
            ]
        ]

        project.data.jira.trackingDocs << trackingIssues

        when:
        usecase.updateJiraDocumentationTrackingIssue(documentType, message, "1")

        then:
        1 * usecase.getSectionsNotDone(documentType) >> []
        (1.._) * this.project.isDeveloperPreviewMode() >> false
        (1.._) * this.project.hasWipJiraIssues() >> false

        then:
        1 * usecase.updateValidDocVersionInJira("TRK-1", "1")
    }

    def "does not update document version in Jira documentation tracking issue when run is developer preview"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        def documentType = "CSD"
        def uri = "http://document "

        def trackingIssues = [
            "TRK-1"      : [
                "key"        : "TRK-1",
                "name"       : "Document Demo",
                "description": "Tracking issue in Q",
                "status"     : "PENDING",
                "labels"     : [
                    "Doc:${documentType}"
                ]
            ]
        ]

        project.data.jira.trackingDocs << trackingIssues

        when:
        usecase.updateJiraDocumentationTrackingIssue(documentType, uri, "1")

        then:
        1 * usecase.getSectionsNotDone(documentType) >> []
        (1.._) * this.project.isDeveloperPreviewMode() >> true
        0 * this.project.hasWipJiraIssues() >> false

        then:
        0 * usecase.updateValidDocVersionInJira(_)
    }

    def "does not update document version in Jira documentation tracking issue when project has WIP issues"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        def documentType = "CSD"
        def message = "myMessage"

        def trackingIssues = [
            "TRK-1"      : [
                "key"        : "TRK-1",
                "name"       : "Document Demo",
                "description": "Tracking issue in Q",
                "status"     : "PENDING",
                "labels"     : [
                    "Doc:${documentType}"
                ]
            ]
        ]

        project.data.jira.trackingDocs << trackingIssues

        when:
        usecase.updateJiraDocumentationTrackingIssue(documentType, message,"1")

        then:
        1 * usecase.getSectionsNotDone(documentType) >> []
        (1.._) * this.project.isDeveloperPreviewMode() >> false
        (1.._) * this.project.hasWipJiraIssues() >> true

        then:
        0 * usecase.updateValidDocVersionInJira(_)
    }

    def "watermark 'work in progress' should be applied to some document type when there are 'work in progress' issues"() {
        given:
        project.buildParams.version = "1.0"
        project.buildParams.targetEnvironment = "dev"

        when:
        def result = usecase.getWatermarkText("myDocumentType", true)

        then:
        result == usecase.WORK_IN_PROGRESS_WATERMARK
    }

    def "watermark 'developer preview' should be applied to some document type when in developer preview mode"() {
        given:
        project.buildParams.version = "WIP"
        project.buildParams.targetEnvironment = "dev"

        when:
        def result = usecase.getWatermarkText("myDocumentType", false)

        then:
        result == usecase.DEVELOPER_PREVIEW_WATERMARK
    }

    def "get document templates version"() {
        when:
        def result = usecase.getDocumentTemplatesVersion()

        then:
        result == "1.0"
    }

    def "converts html images to base64"() {
        given:
        // Argument Constraints
        def contentWithImage = '<img src=http://image.com/image >'
        def contentNoImage = 'contentHere'
        def docChapters = [[key:'DC-1', content: contentWithImage], [key:'DC-1', content: contentNoImage]]
        def docChapters2 = ['1': [key:'DC-1', content: contentWithImage], '2': [key:'DC-1', content: contentNoImage]]
        def imageb64 = 'thisIsAn64BaSeIMaGeEeE'
        def requirements = project.getSystemRequirements().each {
            it.cloneIt()
        }
        def jiraDataItem = requirements.first()
        jiraDataItem.put("description", contentWithImage)
        jiraDataItem.get("funcSpec").put("description", contentWithImage)
        jiraDataItem.get("configSpec").put("description", contentWithImage)

        def techSpecs = [[key:"2", systemDesignSpec:contentWithImage],[key:"3", softwareDesignSpec: contentWithImage]]
        def compMetadata = [
            "demo-app-front-end": [
                key           : "Front-key",
                componentName : "demo-app-front-end",
                componentId   : "front",
                componentType : "ODS Component",
                odsRepoType   : "ods",
                description   : "Example description",
                nameOfSoftware: "Stock Shop frontend",
                references    : "N/A",
                supplier      : "N/A",
                version       : "0.1",
                requirements  : [],
                techSpecs     : [techSpecs[0]]
            ]
        ]
        project.data.jira.discontinuationsPerType = [requirements:[]]

        when:
        def result = usecase.convertImages(contentWithImage)

        then:
        1 * jiraUseCase.convertHTMLImageSrcIntoBase64Data(contentWithImage) >> imageb64
        result == imageb64

        when:
        result = usecase.convertImages(contentNoImage)

        then:
        0 * jiraUseCase.convertHTMLImageSrcIntoBase64Data(contentWithImage)
        result == contentNoImage

        when:
        usecase.getDocumentSections('somedoc')

        then:
        1 * project.getDocumentChaptersForDocument(_) >> docChapters
        2 * usecase.convertImages(_)
        1 * jiraUseCase.convertHTMLImageSrcIntoBase64Data(contentWithImage) >> imageb64

        when:
        usecase.createCSD()

        then:
        1 * usecase.getDocumentSections(_) >> docChapters2
        1 * project.getSystemRequirements() >> requirements
        5 * usecase.convertImages(_)
        3 * jiraUseCase.convertHTMLImageSrcIntoBase64Data(contentWithImage) >> imageb64
        1 * usecase.createDocument(*_) >> ''
        1 * usecase.updateJiraDocumentationTrackingIssue(*_)

        when:
        usecase.createSSDS()

        then:
        1 * usecase.getDocumentSections(_) >> docChapters2
        1 * usecase.computeComponentMetadata(_) >> compMetadata

        then:
        2 * usecase.convertImages(_)
        1 * jiraUseCase.convertHTMLImageSrcIntoBase64Data(contentWithImage) >> imageb64
        1 * usecase.createDocument(*_) >> ''
        usecase.obtainCodeReviewReport(*_) >> []
        project.getTechnicalSpecifications() >> techSpecs
        1 * usecase.updateJiraDocumentationTrackingIssue(*_)
    }

    def "order steps"() {
        given:
        def  testIssue = [ key: "JIRA-1" ,
                           steps: [
                               [
                                   orderId: 2,
                                   data: "N/A"
                               ],
                               [
                                   orderId: 1,
                                   data: "N/A"
                               ]
                           ]]

            when:
            LeVADocumentUseCase leVADocumentUseCase = new LeVADocumentUseCase(null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null)
            def ordered = leVADocumentUseCase.sortTestSteps(testIssue.steps)

            then:
            ordered.get(0).orderId == 1
    }

    def "referenced documents version"() {
        given:
        def project = Stub(Project)
        project.isVersioningEnabled >> true
        project.getDocumentVersionFromHistories('CSD') >> 3L
        project.getDocumentVersionFromHistories('DTR') >> 4L
        project.buildParams >> [targetEnvironmentToken: 'D', configItem: 'ConfigItem']
        def jiraService = Stub(JiraService)
        def jiraUseCase = Spy(new JiraUseCase(null, null, null, jiraService, null))
        jiraUseCase.getLatestDocVersionId(_) >> 1L
        def useCase = Spy(new LeVADocumentUseCase(project, steps, null, null, null, jiraUseCase, null, null, null, null, null, null, null, null))

        when:
        def versions = useCase.getReferencedDocumentsVersion()

        then:
        9 * useCase.getDocumentTrackingIssuesForHistory(_, _) >> []
        versions == [
            CSD: 'ConfigItem / See version created within this change',
            CSD_version: 'WIP/3',
            SSDS: 'ConfigItem / See version created within this change',
            SSDS_version: 'WIP/2',
            RA: 'ConfigItem / See version created within this change',
            RA_version: 'WIP/2',
            TRC: 'ConfigItem / See version created within this change',
            TRC_version: 'WIP/2',
            DTP: 'ConfigItem / See version created within this change',
            DTP_version: 'WIP/2',
            DTR: 'ConfigItem / See version created within this change',
            DTR_version: 'WIP/4',
            CFTP: 'ConfigItem / See version created within this change',
            CFTP_version: 'WIP/2',
            CFTR: 'ConfigItem / See version created within this change',
            CFTR_version: 'WIP/1',
            TIR: 'ConfigItem / See version created within this change',
            TIR_version: 'WIP/2',
            TIP: 'ConfigItem / See version created within this change',
            TIP_version: 'WIP/2',
            TCP: 'ConfigItem / See version created within this change',
            TCP_version: 'WIP/2'
        ]
    }

    def "get version if versioning not enabled"() {
        given:
        def project = Stub(Project)
        project.isVersioningEnabled >> false
        project.buildParams >> [version: '3']
        steps.env.BUILD_NUMBER = '56'
        def jiraService = Stub(JiraService)
        def jiraUseCase = Spy(new JiraUseCase(null, null, null, jiraService, null))
        def useCase = Spy(new LeVADocumentUseCase(project, steps, null, null, null, jiraUseCase, null, null, null, null, null, null, null, null))

        when:
        def version = useCase.getVersion(project, 'CSD')

        then:
        version == '3-56'
    }

    def "get version from histories not WIP"() {
        given:
        def project = Stub(Project)
        project.isVersioningEnabled >> true
        project.getDocumentVersionFromHistories('CSD') >> 3L
        def jiraService = Stub(JiraService)
        def jiraUseCase = Spy(new JiraUseCase(null, null, null, jiraService, null))
        def useCase = Spy(new LeVADocumentUseCase(project, stepsNoWip, null, null, null, jiraUseCase, null, null, null, null, null, null, null, null))

        when:
        def version = useCase.getVersion(project, 'CSD')

        then:
        version == 'CHG00001/3'
    }

    def "get version from histories WIP"() {
        given:
        def project = Stub(Project)
        project.isVersioningEnabled >> true
        project.isWorkInProgress >> true
        project.getDocumentVersionFromHistories('CSD') >> 3L
        def jiraService = Stub(JiraService)
        def jiraUseCase = Spy(new JiraUseCase(null, null, null, jiraService, null))
        def useCase = Spy(new LeVADocumentUseCase(project, steps, null, null, null, jiraUseCase, null, null, null, null, null, null, null, null))

        when:
        def version = useCase.getVersion(project, 'CSD')

        then:
        version == 'WIP/3'
    }

    def "get version new version WIP"() {
        given:
        def project = Stub(Project)
        project.isVersioningEnabled >> true
        project.isWorkInProgress >> true
        project.getDocumentVersionFromHistories('CSD') >> null
        def jiraService = Stub(JiraService)
        def jiraUseCase = Spy(new JiraUseCase(null, null, null, jiraService, null))
        jiraUseCase.getLatestDocVersionId(_) >> 4L
        def useCase = Spy(new LeVADocumentUseCase(project, steps, null, null, null, jiraUseCase, null, null, null, null, null, null, null, null))

        when:
        def version = useCase.getVersion(project, 'CSD')

        then:
        1 * useCase.getDocumentTrackingIssuesForHistory('CSD', _) >> []
        version == 'WIP/5'
    }

    def "get version new version not WIP in first environment"() {
        given:
        def project = Stub(Project)
        project.isVersioningEnabled >> true
        project.buildParams >> [targetEnvironmentToken: 'D']
        project.getDocumentVersionFromHistories('CSD') >> null
        def jiraService = Stub(JiraService)
        def jiraUseCase = Spy(new JiraUseCase(null, null, null, jiraService, null))
        jiraUseCase.getLatestDocVersionId(_) >> 4L
        def useCase = Spy(new LeVADocumentUseCase(project, stepsNoWip, null, null, null, jiraUseCase, null, null, null, null, null, null, null, null))

        when:
        def version = useCase.getVersion(project, 'CSD')

        then:
        1 * useCase.getDocumentTrackingIssuesForHistory('CSD', _) >> []
        version == 'CHG00001/5'
    }

    def "get version new version not WIP in second environment"() {
        given:
        def project = Stub(Project)
        project.isVersioningEnabled >> true
        project.buildParams >> [targetEnvironmentToken: 'Q']
        project.getDocumentVersionFromHistories('CSD') >> null
        def jiraService = Stub(JiraService)
        def jiraUseCase = Spy(new JiraUseCase(null, null, null, jiraService, null))
        jiraUseCase.getLatestDocVersionId(_) >> 4L
        def useCase = Spy(new LeVADocumentUseCase(project, stepsNoWip, null, null, null, jiraUseCase, null, null, null, null, null, null, null, null))

        when:
        def version = useCase.getVersion(project, 'CSD')

        then:
        1 * useCase.getDocumentTrackingIssuesForHistory('CSD', _) >> []
        version == 'CHG00001/4'
    }

    def "requirements are properly sorted and indexed by epic and key"() {
        given:
        def updatedReqs = [
            [
                key:  'key5',
                epic: 'epic2'
            ],
            [
                key:  'key2',
                epic: null
            ],
            [
                key:  'key3',
                epic: 'epic2'
            ],
            [
                key:  'key1',
                epic: null
            ],
            [
                key:  'key8',
                epic: 'epic1'
            ],
            [
                key:  'key4',
                epic: 'epic1'
            ],
            [
                key:  'key6',
                epic: 'epic2'
            ],
            [
                key:  'key7',
                epic: 'epic1'
            ],
            [
                key:  'key9',
                epic: null
            ],
        ]

        when:
        def groupedReqs = usecase.sortByEpicAndRequirementKeys(updatedReqs)

        then:
        // Requirements without epic are sorted by key
        assert groupedReqs.noepics == groupedReqs.noepics.toSorted { req -> req.key }
        // Epics are sorted by epic key
        assert groupedReqs.epics == groupedReqs.epics.toSorted { epic -> epic.key }
        // Epics are correctly indexed, according to the order by epic key
        assert groupedReqs.epics == groupedReqs.epics.toSorted { epic -> epic.epicIndex }
        // For each epic, its requirements are sorted by requirement key
        groupedReqs.epics.each { epic ->
            assert epic.stories == epic.stories.toSorted { req -> req.key }
        }
    }

    def "dash to unicode conversion"() {
        given:
        LeVADocumentUseCase leVADocumentUseCase = new LeVADocumentUseCase(null, null, null,
            null, null, null, null, null, null, null, null, null, null, null)

        expect:
        leVADocumentUseCase.replaceDashToNonBreakableUnicode(value) == result

        where:
        value     | result
        null      | null
        'abc'     | 'abc'
        '-'       | '&#x2011;'
        'ab-c'    | 'ab&#x2011;c'
        'ab-c-d'  | 'ab&#x2011;c&#x2011;d'
    }

    def "getTestDescription"(testIssue, expected) {
        given:
        LeVADocumentUseCase leVADocumentUseCase = new LeVADocumentUseCase(null, null, null,
            null, null, null, null, null, null, null, null,
            null, null, null)

        when:
        def result = leVADocumentUseCase.getTestDescription(testIssue)

        then:
        result == expected

        where:
        testIssue                                      |       expected
        [name: '',description: '']                     |       'N/A'
        [name: 'NAME',description: '']                 |       'NAME'
        [name: '',description: 'DESCRIPTION']          |       'DESCRIPTION'
        [name: 'NAME',description: 'DESCRIPTION']      |       'DESCRIPTION'
    }

    @Unroll
    def "add correct show flag for issues"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))
        project.projectProperties."PROJECT.IS_GXP" = isGxp
        project.data.jira.docs.doc2 = [
            documents: [documentType],
            status: status,
            number: number,
            section: "sec1",
            content: "Original content"]

        when:
        def result = usecase.getDocumentSections(documentType)

        then:
        result["sec1"].show == expected

        where:
        isGxp | documentType   | status        | number || expected
        false | CSD as String  | "IN PROGRESS" | "2"    || false
        false | SSDS as String | "IN PROGRESS" | "2"    || false
        false | CSD as String  | "DONE"        | "2"    || true
        false | SSDS as String | "DONE"        | "2"    || true
        false | CSD as String  | "DONE"        | "1"    || true
        false | CSD as String  | "CANCELLED"   | "2"    || false
        false | SSDS as String | "CANCELLED"   | "2"    || false
        false | CSD as String  | "IN PROGRESS" | "1"    || false
        false | CSD as String  | "IN PROGRESS" | "3.1"  || false
        false | SSDS as String | "IN PROGRESS" | "1"    || false
        false | SSDS as String | "IN PROGRESS" | "2.1"  || false
        false | SSDS as String | "IN PROGRESS" | "3.1"  || false
        false | SSDS as String | "IN PROGRESS" | "5.4"  || false
        true  | CSD as String  | "IN PROGRESS" | "2"    || true
    }

    @Unroll
    def "fillRASections update section sec4s2s2 according to project property PROJECT.USES_POO"() {
        given:
        def sections = ["sec4s2s2":[]]
        def risks = [[:]]
        def proposedMeasuresDesription = [[:]]
        def low = "low"
        def medium = "medium"
        def high = "high"
        def project = Mock(Project)
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))

        when:
        usecase.fillRASections(sections, risks, proposedMeasuresDesription)

        then:
        project.getProjectProperties() >> [
            "PROJECT.NON-GXP_EVALUATION" : "",
            "PROJECT.USES_POO" : poo,
            "PROJECT.POO_CAT.LOW" : low,
            "PROJECT.POO_CAT.MEDIUM" : medium,
            "PROJECT.POO_CAT.HIGH": high
        ]

        and:
        sections.sec4s2s2 == expectedResult

        where:
        poo             |  expectedResult
        "true"          |  [usesPoo:"true", lowDescription:"low", mediumDescription:"medium", highDescription:"high"]
        "false"         |  [:]
        "TRUE"          |  [usesPoo:"true", lowDescription:"low", mediumDescription:"medium", highDescription:"high"]
        "FALSE"         |  [:]
        "invalidValue"  |  [:]
        null            |  [:]
        true            |  [usesPoo:"true", lowDescription:"low", mediumDescription:"medium", highDescription:"high"]
        false           |  [:]
    }

    def "getRequirement can return empty requirement"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService), logger))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger))
        def risk = Mock(Project.JiraDataItem)
        risk.getResolvedTechnicalSpecifications() >> []
        risk.getResolvedSystemRequirements() >> []

        when:
        def result = usecase.metaClass.getMetaMethod("getRequirement", Project.JiraDataItem).invoke(usecase, risk)

        then:
        null == result
    }

}
