package org.ods.orchestration.usecase

import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.rules.TemporaryFolder
import org.ods.core.test.PipelineSpecBase
import org.ods.core.test.pdf.PdfCompare
import org.ods.core.test.usecase.LeVADocumentUseCaseFactory
import org.ods.core.test.wiremock.WiremockFactory
import org.ods.core.test.wiremock.WiremockManager
import org.ods.orchestration.service.leva.ProjectDataBitbucketRepository
import org.ods.services.GitService
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.util.IPipelineSteps
import spock.lang.Shared
import spock.lang.Unroll
import util.FixtureHelper

@Slf4j
class LevaDocumentUseCasePipelineSpec extends PipelineSpecBase {
    private static final String PROJECT_KEY = "OFI1805"
    private static final String PROJECT_KEY_RELEASE_ID = "123"
    private static final String SAVED_DOCUMENTS="build/reports/LeVADocs"

    /**
     * By default testRecordMode = false
     * Add 'testRecordMode = true' in gradle.properties Change to record Wiremock interactions
     *
     * After Wiremock record:
     *  in recorded files change "date_created":"...."  by "date_created":"${json-unit.any-string}"
     */
    private static final boolean RECORD = Boolean.parseBoolean(System.properties["testRecordMode"])

    @Rule
    EnvironmentVariables env = new EnvironmentVariables()

    @Rule
    public TemporaryFolder tempFolder

    @Shared
    WiremockManager jiraServer

    @Shared
    WiremockManager docGenServer

    JenkinsService jenkins
    NexusService nexus
    OpenShiftService openShiftService
    SonarQubeUseCase sonarQubeUseCase
    GitService gitService
    IPipelineSteps steps

    def setupSpec(){
        new File(SAVED_DOCUMENTS).mkdirs()
    }

    def cleanupSpec() {
        docGenServer?.tearDown()
        jiraServer?.tearDown()
    }

    @Unroll
    def "create #doctype"() {
        given: "There's a LeVADocument service"
        LeVADocumentUseCase useCase = getLeVADocumentUseCaseFactory(doctype, version)
            .loadProject(buildParams(version))
            .createLeVADocumentUseCase()

        when: "the user creates a LeVA document"
        useCase."create${doctype}"()

        then: "Nexus stored the document"
        1 * nexus.storeArtifact(
            "leva-documentation",
            "${PROJECT_KEY.toLowerCase()}-${version}",
            "${doctype}-${PROJECT_KEY}-${version}-1.zip",
            !null,
            "application/zip")

        and: "validate PDF is as expected"
        validatePDF(doctype, version)

        where:
        doctype << [ "CFTP", "CSD", "DIL", "IVP", "RA", "TCP", "TIP"]
        version = "WIP"
    }

    private boolean validatePDF(doctype, version) {
        unzipGeneratedArtifact(doctype, version)
        if (RECORD) {
            copyDocWhenRecording(doctype, version)
            return true
        } else {
            def actualDoc = actualDoc(doctype, version)
            def expectedDoc = expectedDoc(doctype, version)
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

    private File expectedDoc(doctype, version) {
        new FixtureHelper().getResource("expected/${doctype}-${PROJECT_KEY}-${version}-1.pdf")
    }

    File savedDoc(String doctype, version) {
        return new File("${SAVED_DOCUMENTS}/${doctype}-${PROJECT_KEY}-${version}-1.pdf")
    }

    void copyDocWhenRecording(String doctype, version) {
        def expectedDoc = new File("test/resources/expected/${doctype}-${PROJECT_KEY}-${version}-1.pdf")
        FileUtils.copyFile(actualDoc(doctype, version), expectedDoc)
    }

    private LeVADocumentUseCaseFactory getLeVADocumentUseCaseFactory(String doctype, String version) {
        log.info "Using temporal folder:${tempFolder.getRoot()}"
        log.info "Using record Wiremock:${RECORD}"
        log.info "Using PROJECT_KEY:${PROJECT_KEY}"

        String scenarioPath = "${this.class.simpleName}/${doctype}/${version}"
        docGenServer = WiremockFactory.DOC_GEN.build().withScenario(scenarioPath).startServer(RECORD)
        jiraServer = WiremockFactory.JIRA.build().withScenario(scenarioPath).startServer(RECORD)

        steps = Spy(util.PipelineSteps)
        jenkins = Mock(JenkinsService)
        nexus = Mock(NexusService)
        openShiftService = Mock(OpenShiftService)
        sonarQubeUseCase = Mock(SonarQubeUseCase)
        gitService = Mock(GitService)
        jenkins.unstashFilesIntoPath(_, _, "SonarQube Report") >> true

        steps.readFile(file: "${ProjectDataBitbucketRepository.BASE_DIR}/documentHistory-D-${doctype}.json") >>  "{}"

        return new LeVADocumentUseCaseFactory(
            jiraServer,
            docGenServer,
            env,
            tempFolder,
            jenkins,
            nexus,
            openShiftService,
            sonarQubeUseCase,
            gitService,
            steps)
    }

    def buildParams(version){
        def buildParams = [:]
        buildParams.projectKey = PROJECT_KEY
        buildParams.targetEnvironment = "dev"
        buildParams.targetEnvironmentToken = "D"
        buildParams.version = "${version}"
        buildParams.releaseStatusJiraIssueKey = "${PROJECT_KEY}-${PROJECT_KEY_RELEASE_ID}"
        return buildParams
    }
}

