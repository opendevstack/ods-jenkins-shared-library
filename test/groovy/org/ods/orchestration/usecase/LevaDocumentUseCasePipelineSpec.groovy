package org.ods.orchestration.usecase

import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.rules.TemporaryFolder
import org.ods.core.test.PipelineSpecBase
import org.ods.core.test.pdf.PdfCompare
import org.ods.core.test.usecase.LeVADocumentUseCaseFactory
import org.ods.core.test.wiremock.WiremockManager
import org.ods.core.test.wiremock.WiremockServers
import org.ods.services.GitService
import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import spock.lang.Ignore
import spock.lang.Unroll
import util.FixtureHelper

/**
 * IMPORTANT: this test use Wiremock files to mock all the external interactions.
 *
 * We have 2 flags to play with the test:
 *  - RECORD: When TRUE wiremock will record the interaction with the servers and compare the pdf results with the expected
 *  - GENERATE_EXPECTED_PDF_FILES: When TRUE it will remove the expected pdfs and create a new ones
 *
 *  ie:
 *  - RECORD=false & GENERATE_EXPECTED_PDF_FILES=false are the default values. So then it can be executed everywhere.
 *  - RECORD=true & GENERATE_EXPECTED_PDF_FILES=false will record and compare the generate pdfs with the 'old' expected files
 *      ==> with this combination, if there's an error,
 *          we can compare new pdf with the old one, and see the implications of our changes in the pdfs
 *          see here _build/reports/LeVADocs_ the compared results images
 *  - RECORD=true & GENERATE_EXPECTED_PDF_FILES=true will record and generate new pdf expected files
 *
 */
@Slf4j
class LevaDocumentUseCasePipelineSpec extends PipelineSpecBase {
    private static final boolean RECORD = Boolean.parseBoolean(System.properties["testRecordMode"])
    private static final boolean GENERATE_EXPECTED_PDF_FILES = Boolean.parseBoolean(System.properties["generateExpectedPdfFiles"])
    private static final String PROJECT_KEY = "TES53"           //"TES53" "OFI2004"
    private static final String PROJECT_KEY_RELEASE_ID = "123" //"207" "123"
    private static final String SAVED_DOCUMENTS="build/reports/LeVADocs"
    private static final Map<String, String> LINK_TESTS_IN_JIRA = ['testOk1': '132',
                                                                   'testOk2': '137',
                                                                   'testOk3': '138',
                                                                   'testOk4': '139',
                                                                   'testOk5': '140',
                                                                   'testOk6': '141',
                                                                   'testOk7': '142',
                                                                   'testOk8': '143',
                                                                   'testKo1': '144',
                                                                   'testKo2': '145',
                                                                   'testSkipped1': '146']

    @Rule
    EnvironmentVariables env = new EnvironmentVariables()

    @Rule
    public TemporaryFolder tempFolder

    private WiremockManager jiraServer
    private WiremockManager docGenServer
    private WiremockManager nexusServer
    private WiremockManager sonarServer

    def setupSpec(){
        new File(SAVED_DOCUMENTS).mkdirs()
    }

    def cleanup() {
        docGenServer?.tearDown()
        jiraServer?.tearDown()
        nexusServer?.tearDown()
        sonarServer?.tearDown()
    }

    @Unroll
    def "create #doctype"() {
        given: "There's a LeVADocument service"
        LeVADocumentUseCase useCase = getLeVADocumentUseCaseFactory(doctype, version)
            .loadProject(setBuildParams(version))
            .createLeVADocumentUseCase()

        when: "the user creates a LeVA document"
        useCase."create${doctype}"()

        then: "the generated PDF is as expected"
        validatePDF(doctype, version)

        where:
        doctype << [ "CSD", "DIL", "DTP", "RA",  "CFTP", "IVP", "SSDS", "TCP",  "TIP"]
        version = "WIP"
    }


    @Unroll
    def "create TCR"() {
        given: "There's a LeVADocument service"
        LeVADocumentUseCase useCase = getLeVADocumentUseCaseFactory(doctype, version)
            .loadProject(setBuildParams(version))
            .createLeVADocumentUseCase()

        when: "the user creates a LeVA document"
        def data = getTestResult(useCase, LINK_TESTS_IN_JIRA)

        useCase.createTCR(null, data)

        then: "the generated PDF is as expected"
        validatePDF(doctype, version)

        where:
        doctype = "TCR"
        version = "WIP"
    }

    // TODO docs with params:  "DTR",  "CFTR", "IVR",  "TCR", "TIR"

    @Ignore // until DTR", "TIR" are done
    @Unroll
    def "create Overall #doctype"() {
        given: "There's a LeVADocument service"
        LeVADocumentUseCase useCase = getLeVADocumentUseCaseFactory("Overall$doctype", version)
            .loadProject(setBuildParams(version))
            .createLeVADocumentUseCase()

        when: "the user creates a LeVA document"
        useCase."createOverall${doctype}"()

        then: "the generated PDF is as expected"
        validatePDF(doctype, version, "OverAll")

        where:
        doctype << ["DTR", "TIR"] // TODO IVR
        version = "WIP"
    }

    private LeVADocumentUseCaseFactory getLeVADocumentUseCaseFactory(String doctype, String version) {
        log.info "Using record Wiremock:${RECORD}"
        log.info "Using GENERATE_EXPECTED_PDF_FILES:${GENERATE_EXPECTED_PDF_FILES}"
        log.info "Using temporal folder:${tempFolder.getRoot()}"
        log.info "Using PROJECT_KEY:${PROJECT_KEY}"

        String scenarioPath = "${this.class.simpleName}/${doctype}/${version}"
        docGenServer = WiremockServers.DOC_GEN.build().withScenario(scenarioPath).startServer(RECORD)
        jiraServer = WiremockServers.JIRA.build().withScenario(scenarioPath).startServer(RECORD)
        nexusServer = WiremockServers.NEXUS.build().withScenario(scenarioPath).startServer(RECORD)
        sonarServer = WiremockServers.SONAR_QU.build().withScenario(scenarioPath).startServer(RECORD)

        JenkinsService jenkins = Mock(JenkinsService)
        OpenShiftService openShiftService = Mock(OpenShiftService)
        GitService gitService = Mock(GitService)
        jenkins.unstashFilesIntoPath(_, _, "SonarQube Report") >> true
        BitbucketTraceabilityUseCase bitbucketTraceabilityUseCase = Spy(new BitbucketTraceabilityUseCase(null, null, null))
        bitbucketTraceabilityUseCase.generateSourceCodeReviewFile() >> new FixtureHelper()
            .getResource(BitbucketTraceabilityUseCaseSpec.EXPECTED_BITBUCKET_CSV).getAbsolutePath()
        return new LeVADocumentUseCaseFactory(
            jiraServer,
            docGenServer,
            nexusServer,
            sonarServer,
            env,
            tempFolder,
            jenkins,
            openShiftService,
            gitService,
            bitbucketTraceabilityUseCase)
    }

    private Map<String, Map<String, Map<String, Cloneable>>> getTestResult(LeVADocumentUseCase useCase, Map<String, String> linkTestsInJira) {
        def xmlJUnitTest = new FixtureHelper().getResource("jUnitTestResult.xml")
        def testReportFiles = [xmlJUnitTest]
        def testResults = new JUnitTestReportsUseCase(useCase.project, useCase.steps)
            .parseTestReportFiles(testReportFiles, linkTestsInJira)
        Map data = [
            tests: [
                acceptance  : [
                    testReportFiles: testReportFiles,
                    testResults    : testResults
                ],
                installation: [
                    testReportFiles: testReportFiles,
                    testResults    : testResults
                ],
                integration : [
                    testReportFiles: testReportFiles,
                    testResults    : testResults
                ]
            ]
        ]
        return data
    }

    private boolean validatePDF(doctype, version, oveAllPrefix = "") {
        unzipGeneratedArtifact(doctype, version)
        if (GENERATE_EXPECTED_PDF_FILES) {
            copyDocWhenRecording(doctype, version, oveAllPrefix)
            return true
        } else {
            def actualDoc = actualDoc(doctype, version)
            def expectedDoc = expectedDoc(doctype, version, oveAllPrefix)
            return new PdfCompare(SAVED_DOCUMENTS).compare(actualDoc.absolutePath, expectedDoc.absolutePath)
        }
    }

    private File actualDoc(doctype, version) {
        new File("${tempFolder.getRoot()}/${doctype}-${PROJECT_KEY}-${version}-1.pdf")
    }

    private Object unzipGeneratedArtifact(doctype, version) {
        new AntBuilder().unzip(
            src: "${tempFolder.getRoot()}/workspace/artifacts/${doctype}-${PROJECT_KEY}-${version}-1.zip",
            dest: "${tempFolder.getRoot()}",
            overwrite: "true")
    }

    private File expectedDoc(doctype, version, oveAllPrefix) {
        new FixtureHelper().getResource("expected/${this.class.simpleName}/${oveAllPrefix}${doctype}-${PROJECT_KEY}-${version}-1.pdf")
    }

    private void copyDocWhenRecording(doctype, version, oveAllPrefix) {
        def expectedDoc = new File("test/resources/expected/${this.class.simpleName}/${oveAllPrefix}${doctype}-${PROJECT_KEY}-${version}-1.pdf")
        FileUtils.copyFile(actualDoc(doctype, version), expectedDoc)
    }

    def setBuildParams(version){
        def buildParams = [:]
        buildParams.projectKey = PROJECT_KEY
        buildParams.targetEnvironment = "dev"
        buildParams.targetEnvironmentToken = "D"
        buildParams.version = "${version}"
        buildParams.configItem = "BI-IT-DEVSTACK"
        buildParams.releaseStatusJiraIssueKey = "${PROJECT_KEY}-${PROJECT_KEY_RELEASE_ID}"
        return buildParams
    }
}

