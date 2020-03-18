package org.ods.usecase

import groovy.json.JsonOutput

import java.nio.file.Files

import org.ods.service.*
import org.ods.util.*

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class LeVADocumentUseCaseSpec extends SpecHelper {

    Project project
    IPipelineSteps steps
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

    def setup() {
        project = Spy(createProject())
        steps = Spy(util.PipelineSteps)
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
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))
    }

    def "compute test discrepancies"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

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

    def "create CSD"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.CSD as String

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"

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
    }

    def "create TRC"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.TRC as String

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"

        when:
        usecase.createTRC()

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)

        then:
        1 * project.getSystemRequirements()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], _)
        1 * usecase.createDocument(documentType, null, _, [:], _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
    }

    def "create DIL"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.DIL as String
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
    }

    def "create DTP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

        // Test Parameters
        def repo = project.repositories.first()

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.DTP as String

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"

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
    }

    def "create DTP without Jira"() {
        given:
        project.services.jira = null

        // Test Parameters
        def repo = project.repositories.first()

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.DTP as String

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"

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
        1 * usecase.computeTestDiscrepancies("Development Tests", testIssues, testResults)
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
        1 * usecase.computeTestDiscrepancies("Development Tests", testIssues, testResults)
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], repo)
        1 * usecase.createDocument(documentType, repo, _, files, _, null, _)
    }

    def "create CFTP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.CFTP as String

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"

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
        1 * usecase.notifyJiraTrackingIssue(*_)
    }

    def "create CFTR"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

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
        def documentType = LeVADocumentUseCase.DocumentType.CFTR as String
        def files = [ "raw/${xmlFile.name}": xmlFile.bytes ]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"

        when:
        usecase.createCFTR(null, data)

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)

        then:
        1 * project.getAutomatedTestsTypeAcceptance() >> acceptanceTestIssues
        1 * project.getAutomatedTestsTypeIntegration() >> integrationTestIssues
        1 * usecase.computeTestDiscrepancies("Integration and Acceptance Tests", SortUtil.sortIssuesByProperties(acceptanceTestIssues + integrationTestIssues, ["key"]), junit.combineTestResults([data.tests.acceptance.testResults, data.tests.integration.testResults]))
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.getWatermarkText(documentType)
        1 * usecase.createDocument(documentType, null, _, files, null, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")

        cleanup:
        xmlFile.delete()
    }

    def "create TCP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.TCP as String

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"

        when:
        usecase.createTCP()

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
    }

    def "create TCR"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

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
        def documentType = LeVADocumentUseCase.DocumentType.TCR as String
        def files = ["raw/${xmlFile.name}": xmlFile.bytes]

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"

        when:
        usecase.createTCR(null, data)

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)

        then:
        1 * project.getAutomatedTestsTypeIntegration() >> integrationTestIssues
        1 * project.getAutomatedTestsTypeAcceptance() >> acceptanceTestIssues
        1 * usecase.createDocument(documentType, null, _, [:], null, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.getWatermarkText(documentType)
        1 * jiraUseCase.matchTestIssuesAgainstTestResults(acceptanceTestIssues, testResults, _, _)
        1 * jiraUseCase.matchTestIssuesAgainstTestResults(integrationTestIssues, testResults, _, _)

        cleanup:
        xmlFile.delete()
    }

    def "create IVP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.IVP as String

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"

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
    }

    def "create IVR"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

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

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"

        when:
        usecase.createIVR(null, data)

        then:
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType)

        then:
        1 * project.getAutomatedTestsTypeInstallation() >> testIssues
        1 * usecase.computeTestDiscrepancies("Installation Tests", testIssues, testResults)
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType])
        1 * usecase.createDocument(documentType, null, _, files, null, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")

        cleanup:
        xmlFile.delete()
    }

    def "create SSDS"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

        steps.env.BUILD_ID = "0815"

        // Test Parameters
        def repo = project.repositories.first()

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.SSDS as String
        def sqReportsPath = "sonarqube/${repo.id}"
        def sqReportsStashName = "scrr-report-${repo.id}-${steps.env.BUILD_ID}"

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"
        def documentIssue = createJiraDocumentIssues().first()
        def sqReportFiles = [getResource("Test.docx")]
        def requirement = [key: "REQ-1", name: "This is the req 1", gampTopic: "roles"]
        def techSpec = [key: "TS-1", softwareDesignSpec: "This is the software design spec for TS-1", name: "techSpec 1"]
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
                requirements  : [requirement],
                techSpecs     : [techSpec]
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
    }

    def "create RA"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.RA as String

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"

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
    }

    def "create TIP"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.TIP as String

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"

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
    }

    def "create TIP without Jira"() {
        given:
        project.services.jira = null

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.TIP as String

        // Stubbed Method Responses
        def chapterData = ["sec1": "myContent"]
        def uri = "http://nexus"

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
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType)

        then:
        1 * os.getPodDataForComponent(repo.id) >> createOpenShiftPodDataForComponent()
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
        1 * jiraUseCase.getDocumentChapterData(documentType) >> chapterData
        0 * levaFiles.getDocumentChapterData(documentType)
        1 * usecase.getWatermarkText(documentType)

        then:
        1 * os.getPodDataForComponent(repo.id) >> createOpenShiftPodDataForComponent()
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType], repo)
        1 * usecase.createDocument(documentType, repo, _, [:], _, null, _)
    }

    def "create overall DTR"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.DTR as String
        def documentTypeName = LeVADocumentUseCase.DocumentType.OVERALL_DTR as String

        // Stubbed Method Responses
        def uri = "http://nexus"

        when:
        usecase.createOverallDTR()

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentTypeName])
        1 * usecase.createOverallDocument("Overall-Cover", documentType, _, null, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentTypeName]} has been generated and is available at: ${uri}.")
    }

    def "create overall TIR"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

        // Argument Constraints
        def documentType = LeVADocumentUseCase.DocumentType.TIR as String
        def documentTypeName = LeVADocumentUseCase.DocumentType.OVERALL_TIR as String

        // Stubbed Method Responses
        def uri = "http://nexus"

        when:
        usecase.createOverallTIR()

        then:
        1 * usecase.getDocumentMetadata(LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentTypeName])
        1 * usecase.createOverallDocument("Overall-TIR-Cover", documentType, _, _, _) >> uri
        1 * usecase.notifyJiraTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentTypeName]} has been generated and is available at: ${uri}.")
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

    def "notify LeVA document issue in DEV"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

        def documentType = "myType"
        def message = "myMessage"

        when:
        usecase.notifyJiraTrackingIssue(documentType, message)

        then:
        1 * jiraUseCase.jira.appendCommentToIssue("DEMO-71", message)
    }

    def "notify LeVA document issue when no issues found in project.data.docs"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

        def documentType = "myTypeNotfound"
        def message = "myMessage"

        when:
        usecase.notifyJiraTrackingIssue(documentType, message)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: No Jira issues associated with document type '${documentType}'."
    }

    def "notify LeVA document with 2 issues not DONE yet"() {
        given:
        jiraUseCase = Spy(new JiraUseCase(project, steps, util, Mock(JiraService)))
        usecase = Spy(new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq))

        def documentType = "myTypeNotDone"
        def message = "myMessage"

        when:
        usecase.notifyJiraTrackingIssue(documentType, message)

        then:
        2 * jiraUseCase.jira.appendCommentToIssue(_, message.concat(" Attention: this document is work in progress! See issues: DEMO-69,DEMO-70"))
    }

    def "docs with watermark text in DEV"() {
        given:
        project.buildParams.targetEnvironment = "dev"

        when:
        def result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.CSD as String)

        then:
        result == "Developer Preview"

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.DTP as String)

        then:
        result == null

        when:
        result = usecase.getWatermarkText(LeVADocumentUseCase.DocumentType.TRC as String)

        then:
        result == "Developer Preview"

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
