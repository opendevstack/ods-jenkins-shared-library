package util.wiremock

import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.rules.TemporaryFolder
import org.ods.orchestration.service.DocGenService
import org.ods.orchestration.service.JiraService
import org.ods.orchestration.service.LeVADocumentChaptersFileService
import org.ods.orchestration.usecase.JUnitTestReportsUseCase
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.usecase.LeVADocumentUseCase
import org.ods.orchestration.usecase.SonarQubeUseCase
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PDFUtil
import org.ods.orchestration.util.Project
import org.ods.services.GitService
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import spock.mock.MockingApi
import util.FixtureHelper
import util.LoggerStub
import util.PipelineSteps

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.containing
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import static com.github.tomakehurst.wiremock.client.WireMock.get
import static com.github.tomakehurst.wiremock.client.WireMock.matching
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import static com.github.tomakehurst.wiremock.client.WireMock.post
import static com.github.tomakehurst.wiremock.client.WireMock.put
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching

@Slf4j
class LeVADocumentUseCaseProxy {
    Project project
    IPipelineSteps steps
    MROPipelineUtil util
    DocGenService docGen
    JiraUseCase jiraUseCase
    JUnitTestReportsUseCase junit
    LeVADocumentChaptersFileService levaFiles

    PDFUtil pdfUtil
    LeVADocumentUseCase usecase
    ILogger logger

    private WiremockManager jiraServer
    private WiremockManager docGenServer
    private EnvironmentVariables env
    private TemporaryFolder tempFolder
    private JenkinsService jenkins
    private NexusService nexus
    private OpenShiftService os
    private SonarQubeUseCase sq
    private GitService gitService

    LeVADocumentUseCaseProxy(WiremockManager jiraServer,
                             WiremockManager docGenServer,
                             EnvironmentVariables env,
                             TemporaryFolder tempFolder,
                             JenkinsService jenkins,
                             NexusService nexus,
                             OpenShiftService os,
                             SonarQubeUseCase sq,
                             GitService gitService){
        this.sq = sq
        this.os = os
        this.nexus = nexus
        this.jenkins = jenkins
        this.tempFolder = tempFolder
        this.env = env
        this.docGenServer = docGenServer
        this.jiraServer = jiraServer
        this.gitService = gitService
    }

    LeVADocumentUseCase createLeVADocumentUseCase(Map buildParams){
        setup(buildParams)
        return new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdfUtil, sq)
    }

    private def setup(Map buildParams) {
      //  loadJiraData(buildParams)

        System.setProperty("java.io.tmpdir", tempFolder.getRoot().absolutePath)
        FileUtils.copyDirectory(new FixtureHelper().getResource("Test-1.pdf").parentFile, tempFolder.getRoot());

        steps = new PipelineSteps()
        steps.env.RUN_DISPLAY_URL =""
        pdfUtil = new PDFUtil()
        logger = new LoggerStub(log)

        project = buildProject(buildParams)

        util = new MROPipelineUtil(project, steps, null, logger)
        junit = new JUnitTestReportsUseCase(project, steps)
        levaFiles = new LeVADocumentChaptersFileService(steps)
        def jiraService = new JiraService(jiraServer.mock().baseUrl(), "username", "password")
        jiraUseCase = new JiraUseCase(project, steps, util, jiraService, logger)
        docGen = new DocGenService(docGenServer.mock().baseUrl())
        usecase = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdfUtil, sq)
        project.load(gitService, jiraUseCase)
    }

    private void loadJiraData(Map buildParams) {
        String projectKey = buildParams.projectKey

        // load issuetypes definitions
        jiraServer.mock().stubFor(get(urlEqualTo("/rest/api/2/issue/createmeta/${projectKey}/issuetypes"))
            .willReturn(aResponse().withStatus(200).withBodyFile("getIssueTypes.json")));

        // load issuetypes
        jiraServer.mock().stubFor(get(urlPathMatching("/rest/api/2/issue/createmeta/${projectKey}/issuetypes/.*"))
            .willReturn(aResponse().withStatus(200)
                .withBodyFile("getIssueTypeMetadata-{{request.pathSegments.[7]}}.json")
                .withTransformers("response-template")))

        // load custom fields
        jiraServer.mock().stubFor(get(urlPathMatching("/rest/api/2/issue/.*"))
            .withQueryParam("fields", matching("customfield_.*"))
            .willReturn(aResponse().withStatus(200)
                .withBodyFile("{{request.query.fields}}.json")
                .withTransformers("response-template")))

        // getDocGenData
        jiraServer.mock().stubFor(get(urlEqualTo("/rest/platform/1.0/docgenreports/${projectKey}"))
            .willReturn(aResponse().withStatus(200).withBodyFile("getDocGenData.json")));

        // searchByJQLQuery
        jiraServer.mock().stubFor(post(urlEqualTo("/rest/api/2/search"))
            .willReturn(aResponse().withStatus(200).withBodyFile("searchByJQLQuery.json")));

        // getProjectVersions
        jiraServer.mock().stubFor(get(urlEqualTo("/rest/api/2/project/${projectKey}/versions"))
            .willReturn(aResponse().withStatus(200).withBodyFile("getProjectVersions.json")));

        def releaseStatusV1=buildParams.releaseStatusJiraIssueKey
        def releaseStatusComment= "Pipeline-generated documents are watermarked 'Work in Progress' since the following issues are work in progress:"
        jiraServer.mock().stubFor(post(urlEqualTo("/rest/api/2/issue/${releaseStatusV1}/comment"))
            .withRequestBody(matchingJsonPath("\$.body", containing(releaseStatusComment)))
            .willReturn(aResponse().withStatus(200)));
        jiraServer.mock().stubFor(put(urlEqualTo("/rest/api/2/issue/${releaseStatusV1}"))
            .withRequestBody(equalToJson("{\"update\":{\"customfield_10329\":[{\"set\":\"WIP-null\"}]}}"))
            .willReturn(aResponse().withStatus(204)))
    }

    def buildProject(Map buildParams) {
        steps.env.BUILD_ID = "1"
        steps.env.WORKSPACE = "${tempFolder.getRoot().absolutePath}/workspace"

        def project = new Project(steps, new Logger(steps, true), [:]).init()
        project.data.metadata.id = buildParams.projectKey
        project.data.buildParams = buildParams
        return project
    }
}
