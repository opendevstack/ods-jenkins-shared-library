package org.ods.orchestration.usecase

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import com.github.tomakehurst.wiremock.junit.WireMockRule
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.rules.TemporaryFolder
import org.ods.orchestration.service.DocGenService
import org.ods.orchestration.service.JiraService
import org.ods.orchestration.service.LeVADocumentChaptersFileService
import org.ods.orchestration.util.DocumentHistory
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
import spock.lang.Specification
import util.FixtureHelper
import util.LoggerStub
import util.PipelineSteps
import static com.github.tomakehurst.wiremock.client.WireMock.*

@Slf4j
class VersionHistorySpec  extends Specification {
    public static final String LOCALHOST = "http://localhost"
    public static final String WIREMOCK_FILES = "test/resources/wiremock"

    @Rule
    EnvironmentVariables env = new EnvironmentVariables()

    @Rule
    public TemporaryFolder tempFolder

    @Rule
    public WireMockRule jiraServer = new WireMockRule(
        (WireMockConfiguration
            .wireMockConfig()
            .withRootDirectory(WIREMOCK_FILES)
            .dynamicPort()
            .extensions(new ResponseTemplateTransformer(false)))
    )

    @Rule
    public WireMockRule docGenServer = new WireMockRule(
        (WireMockConfiguration
            .wireMockConfig()
            .withRootDirectory(WIREMOCK_FILES)
            .dynamicPort()
            .extensions(new ResponseTemplateTransformer(false)))
    )

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
    PDFUtil pdfUtil
    SonarQubeUseCase sq
    LeVADocumentUseCase usecase
    ILogger logger

    def setup() {
        log.info "Using temporal folder:${tempFolder.getRoot()}"

        jenkins = Mock(JenkinsService)
        nexus = Mock(NexusService)
        os = Mock(OpenShiftService)
        sq = Mock(SonarQubeUseCase)

        loadJiraData()

        // TODO do asserts
        jiraServer.stubFor(post(urlEqualTo("/rest/api/2/issue/NET-123/comment"))
                .willReturn(aResponse().withStatus(200)));

        jiraServer.stubFor(put(urlEqualTo("/rest/api/2/issue/NET-123"))
                .willReturn(aResponse().withStatus(204)))

        System.setProperty("java.io.tmpdir", tempFolder.getRoot().absolutePath)
        FileUtils.copyDirectory(new FixtureHelper().getResource("Test-1.pdf").parentFile, tempFolder.getRoot());

        steps = new PipelineSteps()
        pdfUtil = new PDFUtil()
        logger = new LoggerStub(logger, true)

        jenkins.unstashFilesIntoPath(_, _, "SonarQube Report") >> true
        project = buildProject()
        util = new MROPipelineUtil(project, steps, null, logger)
        docGen = new DocGenService("${LOCALHOST}:${docGenServer.port()}")
        junit = new JUnitTestReportsUseCase(project, steps)
        levaFiles = new LeVADocumentChaptersFileService(steps)
        def jiraService = new JiraService("${LOCALHOST}:${jiraServer.port()}", "username", "password")
        jiraUseCase = new JiraUseCase(project, steps, util, jiraService, logger)
        usecase = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdfUtil, sq)
        project.load(Mock(GitService), jiraUseCase)
    }

    private void loadJiraData() {
        // load issuetypes definitions
        jiraServer.stubFor(get(urlEqualTo("/rest/api/2/issue/createmeta/NET/issuetypes"))
                .willReturn(aResponse().withStatus(200).withBodyFile("jira/getIssueTypes.json")));

        // load issuetypes
        jiraServer.stubFor(get(urlPathMatching("/rest/api/2/issue/createmeta/NET/issuetypes/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withBodyFile("jira/getIssueTypeMetadata-{{request.pathSegments.[7]}}.json")
                        .withTransformers("response-template")))

        // load custom fields
        jiraServer.stubFor(get(urlPathMatching("/rest/api/2/issue/.*"))
                .withQueryParam("fields", matching("customfield_.*"))
                .willReturn(aResponse().withStatus(200)
                        .withBodyFile("jira/{{request.query.fields}}.json")
                        .withTransformers("response-template")))

        // getDocGenData
        jiraServer.stubFor(get(urlEqualTo("/rest/platform/1.0/docgenreports/NET"))
                .willReturn(aResponse().withStatus(200).withBodyFile("jira/getDocGenData.json")));

        // searchByJQLQuery
        jiraServer.stubFor(post(urlEqualTo("/rest/api/2/search"))
                .willReturn(aResponse().withStatus(200).withBodyFile("jira/searchByJQLQuery.json")));

        // getProjectVersions
        jiraServer.stubFor(get(urlEqualTo("/rest/api/2/project/NET/versions"))
                .willReturn(aResponse().withStatus(200).withBodyFile("jira/getProjectVersions.json")));
    }

    def buildProject() {
        steps.env.BUILD_ID = "1"
        steps.env.WORKSPACE = "${tempFolder.getRoot().absolutePath}/workspace"

        def project = new Project(steps, new Logger(steps, true), [:]).init()
        project.data.buildParams = [:]
        project.data.buildParams.targetEnvironment = "dev"
        project.data.buildParams.targetEnvironmentToken = "D"
        project.data.buildParams.version = "WIP"
        project.data.buildParams.releaseStatusJiraIssueKey = "NET-123"
        project.getOpenShiftApiUrl() >> 'https://api.dev-openshift.com'
        return project
    }

    def "create CFTP"() {
        given:
        usecase = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdfUtil, sq)

        when:
        usecase.createCFTP()

        then:
        true
        docGenServer.stubFor(post(urlEqualTo("/document"))
                .willReturn(aResponse().withStatus(200).withBodyFile("docGen/documentWithHistory.json")))
    }
}
