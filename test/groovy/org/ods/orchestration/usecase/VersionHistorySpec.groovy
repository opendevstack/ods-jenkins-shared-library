package org.ods.orchestration.usecase

import com.github.tomakehurst.wiremock.verification.LoggedRequest
import de.redsix.pdfcompare.CompareResult
import de.redsix.pdfcompare.PdfComparator
import groovy.util.logging.Slf4j
import org.apache.poi.ss.formula.functions.LookupUtils
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.rules.TemporaryFolder
import org.ods.services.GitService
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import spock.lang.Specification
import util.FixtureHelper
import util.wiremock.LeVADocumentUseCaseProxy
import util.wiremock.WiremockManager
import util.wiremock.WiremockFactory

import static com.github.tomakehurst.wiremock.client.WireMock.*

@Slf4j
class VersionHistorySpec  extends Specification {
    private static final String PROJECT_KEY = "TES89"

    @Rule
    EnvironmentVariables env = new EnvironmentVariables()

    @Rule
    public TemporaryFolder tempFolder

    WiremockManager jiraServer
    WiremockManager docGenServer
    JenkinsService jenkins
    NexusService nexus
    OpenShiftService openShiftService
    SonarQubeUseCase sonarQubeUseCase
    GitService gitService
    LeVADocumentUseCaseProxy useCaseProxy

    def setup() {
        log.info "Using temporal folder:${tempFolder.getRoot()}"

        docGenServer = WiremockFactory.DOC_GEN.build().withScenario(this.class.simpleName).startServer()
        jiraServer = WiremockFactory.JIRA.build().withScenario(this.class.simpleName).startServer()

        jenkins = Mock(JenkinsService)
        nexus = Mock(NexusService)
        openShiftService = Mock(OpenShiftService)
        sonarQubeUseCase = Mock(SonarQubeUseCase)
        gitService = Mock(GitService)
        jenkins.unstashFilesIntoPath(_, _, "SonarQube Report") >> true

        useCaseProxy = new LeVADocumentUseCaseProxy(
            jiraServer,
            docGenServer,
            env,
            tempFolder,
            jenkins,
            nexus,
            openShiftService,
            sonarQubeUseCase,
            gitService)
    }

    def cleanup() {
        docGenServer.tearDown()
        jiraServer.tearDown()
    }

    def "create CFTP"() {
        given: "I have a project"
        def useCase = useCaseProxy.createLeVADocumentUseCase(buildParams())
        def traceabilityMatrixDC = "TES89-108"
        def traceabilityMatrixDCComment = "A new Combined Functional and Requirements Testing Plan has been generated and is available at: null. Attention: this document is work in progress!"

        jiraServer.mock().stubFor(put(urlPathMatching("/rest/api/2/issue/.*"))
                .willReturn(aResponse().withStatus(204)))
        jiraServer.mock().stubFor(put(urlEqualTo("/rest/api/2/issue/${traceabilityMatrixDC}"))
                .withRequestBody(equalToJson("{\"update\":{\"customfield_10312\":[{\"set\":\"1\"}]}}"))
                .willReturn(aResponse().withStatus(204)))
        jiraServer.mock().stubFor(post(urlEqualTo("/rest/api/2/issue/${traceabilityMatrixDC}/comment"))
                .withRequestBody(matchingJsonPath("\$.body", containing(traceabilityMatrixDCComment)))
                .willReturn(aResponse().withStatus(204)))

        jiraServer.mock().stubFor(post(urlPathMatching("/rest/api/2/issue/TES89-.*/comment"))
                .willReturn(aResponse().withStatus(204)))

        when: "I create a CFTP document"
        useCase.createCFTP()

        and: "Compare with the saved report"
        new AntBuilder().unzip(
            src:"${tempFolder.getRoot()}/workspace/artifacts/CFTP-TES89-WIP-1.zip",
            dest:"${tempFolder.getRoot()}",
            overwrite:"true")
        def isEquals = new PdfComparator(
            new FixtureHelper().getResource("expected/CFTP-TES89-WIP-1.pdf").absolutePath,
            "${tempFolder.getRoot()}/CFTP-TES89-WIP-1.pdf")
            .compare()

        then: "Both documents are equals"
        isEquals

        and: "Nexus stored the artifact"
        1 * nexus.storeArtifact(
                "leva-documentation",
                "tes89-WIP",
                "CFTP-TES89-WIP-1.zip",
                _,
                "application/zip")

    }

    def buildParams(){
        def buildParams = [:]
        buildParams.projectKey = PROJECT_KEY
        buildParams.targetEnvironment = "dev"
        buildParams.targetEnvironmentToken = "D"
        buildParams.version = "WIP"
        buildParams.releaseStatusJiraIssueKey = "${PROJECT_KEY}-123"
        return buildParams
    }
}
