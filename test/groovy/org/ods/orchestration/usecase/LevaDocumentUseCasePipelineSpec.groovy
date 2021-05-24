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
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import spock.lang.Shared
import spock.lang.Unroll
import util.FixtureHelper

@Slf4j
class LevaDocumentUseCasePipelineSpec extends PipelineSpecBase {
    private static final String PROJECT_KEY = "OFI2004"
    private static final String PROJECT_KEY_RELEASE_ID = "207"
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
            .loadProject(buildParams(version))
            .createLeVADocumentUseCase()

        when: "the user creates a LeVA document"
        useCase."create${doctype}"()

        then: "the generated PDF is as expected"
        validatePDF(doctype, version)

        where:
        doctype << ["CFTP", "CSD", "DIL", "DTP", "IVP", "RA", "TCP",  "TIP"]   // TODO: SSDS, TCR
        version = "WIP"
    }

    @Unroll
    def "create Overall #doctype"() {
        given: "There's a LeVADocument service"
        LeVADocumentUseCase useCase = getLeVADocumentUseCaseFactory("Overall$doctype", version)
            .loadProject(buildParams(version))
            .createLeVADocumentUseCase()

        when: "the user creates a LeVA document"
        useCase."createOverall${doctype}"()

        then: "the generated PDF is as expected"
        validatePDF(doctype, version, "OverAll")

        where:
        doctype << ["TIR", "DTR"]
        version = "WIP"
    }

    private LeVADocumentUseCaseFactory getLeVADocumentUseCaseFactory(String doctype, String version) {
        log.info "Using temporal folder:${tempFolder.getRoot()}"
        log.info "Using record Wiremock:${RECORD}"
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

        return new LeVADocumentUseCaseFactory(
            jiraServer,
            docGenServer,
            nexusServer,
            sonarServer,
            env,
            tempFolder,
            jenkins,
            openShiftService,
            gitService)
    }

    private boolean validatePDF(doctype, version, oveAllPrefix = "") {
        unzipGeneratedArtifact(doctype, version)
        if (RECORD) {
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
        new FixtureHelper().getResource("expected/${oveAllPrefix}${doctype}-${PROJECT_KEY}-${version}-1.pdf")
    }

    private File savedDoc(doctype, version) {
        return new File("${SAVED_DOCUMENTS}/${doctype}-${PROJECT_KEY}-${version}-1.pdf")
    }

    private void copyDocWhenRecording(doctype, version, oveAllPrefix) {
        def expectedDoc = new File("test/resources/expected/${oveAllPrefix}${doctype}-${PROJECT_KEY}-${version}-1.pdf")
        FileUtils.copyFile(actualDoc(doctype, version), expectedDoc)
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

