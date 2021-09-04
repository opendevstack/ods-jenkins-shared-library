package org.ods.orchestration.usecase

import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.rules.TemporaryFolder
import org.ods.core.test.PipelineSpecBase
import org.ods.core.test.pdf.PdfCompare
import org.ods.core.test.usecase.LeVADocumentUseCaseFactory
import org.ods.core.test.wiremock.WiremockServers
import org.ods.core.test.wiremock.WiremockManager
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
 *  - RECORD=true & GENERATE_EXPECTED_PDF_FILES=true will record and generate new pdf expected files
 *
 */
@Slf4j
class LevaDocumentUseCasePipelineSpec extends PipelineSpecBase {
    /**
     * By default RECORD = false
     * Add 'testRecordMode = true' in gradle.properties Change to record Wiremock interactions
     * When RECORD wiremock will record the interaction with the servers
     *
     * After Wiremock record:
     *  in recorded files change "date_created":"...."  by "date_created":"${json-unit.any-string}"
     */
    private static final boolean RECORD = Boolean.parseBoolean(System.properties["testRecordMode"])

    /**
     * By default GENERATE_EXPECTED_PDF_FILES = false
     * Add 'generateExpectedPdfFiles = true' in gradle.properties Change to generate new expected files
     * When GENERATE_EXPECTED_PDF_FILES it will remove the expected pdfs and create a new ones
     * (ie. GENERATE_EXPECTED_PDF_FILES=true will not compare pdfs)
     */
    private static final boolean GENERATE_EXPECTED_PDF_FILES = Boolean.parseBoolean(System.properties["generateExpectedPdfFiles"])

    private static final String PROJECT_KEY = "OFI2004"
    private static final String PROJECT_KEY_RELEASE_ID = "207"
    private static final String SAVED_DOCUMENTS="build/reports/LeVADocs"

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

    // TODO docs with params:  "DTR",  "CFTR", "IVR",  "TCR", "TIR", "TRC"

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

