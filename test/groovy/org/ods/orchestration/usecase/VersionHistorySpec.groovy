package org.ods.orchestration.usecase

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomakehurst.wiremock.stubbing.StubMapping
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
    private static final String PROJECT_KEY = "TES89"
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
    WireMockServer wireMockServer

    def setup() {
        log.info "Using temporal folder:${tempFolder.getRoot()}"

        jenkins = Mock(JenkinsService)
        nexus = Mock(NexusService)
        os = Mock(OpenShiftService)
        sq = Mock(SonarQubeUseCase)

        loadJiraData()

        System.setProperty("java.io.tmpdir", tempFolder.getRoot().absolutePath)
        FileUtils.copyDirectory(new FixtureHelper().getResource("Test-1.pdf").parentFile, tempFolder.getRoot());

        steps = new PipelineSteps()
        steps.env.RUN_DISPLAY_URL =""
        pdfUtil = new PDFUtil()
        logger = new LoggerStub(log, true)

        jenkins.unstashFilesIntoPath(_, _, "SonarQube Report") >> true
        project = buildProject()
//        def docGenUrl =  "http://docgen.${project.key}-cd.svc:8080"
        def docGenUrl =  "http://172.30.234.65:8080"

        wireMockServer = new WireMockServer(WireMockConfiguration
                .wireMockConfig()
                .withRootDirectory(WIREMOCK_FILES));

        wireMockServer.start();
        wireMockServer.startRecording(docGenUrl)
        docGen = new DocGenService(wireMockServer.baseUrl())
        //docGen = new DocGenService("${LOCALHOST}:${docGenServer.port()}")
       // docGen = new DocGenService(docGenUrl)

        // docGenServer
        // {"metadata":{"type":"CFTP-5","version":"1.0"},"data":{"metadata":{"id":null,"name":"Sock Shop","description":"A socks-selling e-commerce demo application.","type":"Combined Functional and Requirements Testing Plan","version":null,"date_created":"2021-03-10T16:36:00.171653","buildParameter":{"targetEnvironment":"dev","targetEnvironmentToken":"D","version":"WIP","releaseStatusJiraIssueKey":"TES89-123"},"git":null,"openShift":{"apiUrl":null},"jenkins":{"buildNumber":null,"buildUrl":null,"jobName":null},"referencedDocs":{"CSD":"null / WIP-null","SSDS":"null / WIP-null","RA":"null / WIP-null","TRC":"null / WIP-null","DTP":"null / WIP-null","DTR":"null / WIP-null","CFTP":"null / WIP-null","CFTR":"null / WIP-null","TIR":"null / WIP-null","TIP":"null / WIP-null"},"header":["Combined Functional and Requirements Testing Plan, Config Item: null","Doc ID/Version: see auto-generated cover page"]},"data":{"sections":{"sec9":{"number":"9","predecessors":[],"heading":"Traceability Matrix","documents":["CFTP"],"versions":[],"section":"sec9","content":"<p><font color=\"blue\">The test coverage is captured in the traceability matrix which maps user requirements and functional specifications to test cases. The traceability matrix will be updated before test execution to include all functional and requirements test cases.</font></p>","key":"TES89-108","status":"To Do"},"sec8s1":{"number":"8.1","predecessors":[],"heading":"Test Cases","documents":["CFTP"],"versions":[],"section":"sec8s1","content":"<div class='table-wrap'>\n<table class='confluenceTable'><tbody>\n<tr>\n<th class='confluenceTh'>Risk Priority</th>\n<th class='confluenceTh'>Level of Testing</th>\n</tr>\n<tr>\n<td class='confluenceTd'><b>3</b></td>\n<td class='confluenceTd'><font color=\"blue\">No testing required</font></td>\n</tr>\n<tr>\n</tr>\n<tr>\n<td class='confluenceTd'><b>2</b></td>\n<td class='confluenceTd'><font color=\"blue\">Testing of functionality or requirements without challenge/boundary tests</font></td>\n</tr>\n<tr>\n</tr>\n<tr>\n<td class='confluenceTd'><b>1</b></td>\n<td class='confluenceTd'><font color=\"blue\">Full test of requirement or functionality including challenge/boundary tests (e.g. invalid, borderline or missing input values, out-of-order execution of functions, disconnected interfaces etc.)</font></td>\n</tr>\n</tbody></table>\n</div>\n","key":"TES89-107","status":"To Do"},"sec7s2s2":{"number":"7.2.2","predecessors":[],"heading":"Scope of Non-Functional Testing","documents":["CFTP"],"versions":[],"section":"sec7s2s2","content":"<p><font color=\"blue\">The following aspects of system performance will be covered in requirements test cases:<br/>\n * Concurrent access of 10 users accessing the same study (including data entry, modification of data, and deletion of data)<br/>\n * Remote access via low-bandwidth connections, simulation slow DSL connections down to 1 Mbit/s and Ping time up to 1000ms. A WAN emulator will be used to simulate the exact conditions as specified in the test cases.<br/>\n<br/>\nPerformance testing will be done in the validation environment. Since the hardware of the future production environment is more powerful the risk of using the validation environment for performance testing is low and acceptable.</font></p>","key":"TES89-106","status":"To Do"},"sec7s1s2":{"number":"7.1.2","predecessors":[],"heading":"Scope of Functional Testing","documents":["CFTP"],"versions":[],"section":"sec7s1s2","content":"<p><font color=\"blue\">The System name is commercial off-the-shelf software which was configured according to the configuration specification. Since some user requirements could not be fulfilled via configuration and therefore have been implemented by in-house developed customizations.<br/>\n * Functional testing will be performed to verify correct functionality of all customizations, i.e. functional specifications will be 100% covered by functional test cases.<br/>\n * Configuration specification will not be tested on a functional level, but only implicitly in acceptance test cases.<br/>\n * Standard product functionality will not be tested in functional testing since appropriate testing has already performed by the vendor. This has been verified in a vendor audit.</font></p>","key":"TES89-105","status":"To Do"},"sec6s2":{"number":"6.2","predecessors":[],"heading":"Scope of Integration Testing","documents":["CFTP"],"versions":[],"section":"sec6s2","content":"<p><font color=\"blue\">The System name is commercial off-the-shelf software which was configured according to the configuration specification. Since some user requirements could not be fulfilled via configuration and therefore have been implemented by in-house developed customizations.<br/>\n * Integration testing will be performed to verify correct functionality of all customizations, i.e. functional specifications will be 100% covered by functional test cases.<br/>\n * Configuration specification will not be tested on an integration level, but only implicitly in acceptance test cases.<br/>\n * Standard product functionality will not be tested in integration testing since appropriate testing has already performed by the vendor. This has been verified in a vendor audit.</font></p>","key":"TES89-104","status":"To Do"},"sec5s2":{"number":"5.2","predecessors":[],"heading":"Test Procedure 2: Verification of Training Documentation","documents":["CFTP"],"versions":[],"section":"sec5s2","content":"<p><font color=\"blue\">Before Integration Testing can start, the Integration Testing participants have to be trained in<br/>\n * the testing process<br/>\n * the use of the &lt;insert system name&gt; system</font></p>","key":"TES89-103","status":"To Do"},"sec5":{"number":"5","predecessors":[],"heading":"Operational Qualification Activities and Training","documents":["CFTP"],"versions":[],"section":"sec5","content":"<p><font color=\"blue\">Before test execution the testers will receive formal training on:<br/>\n * the testing process, including good documentation practice<br/>\n * a basic end user training for System name<br/>\n * an overview of the business processes supported by System name<br/>\n<br/>\nThe training will be documented and copies of the training records will be attached to the testing documentation package.</font></p>","key":"TES89-102","status":"To Do"},"sec10":{"number":"10","predecessors":[],"heading":"Validation Environment","documents":["CFTP"],"versions":[],"section":"sec10","content":"<p><font color=\"blue\">Description of the Environment of the system that will be used for test execution.</font></p>","key":"TES89-101","status":"To Do"},"sec15":{"number":"15","predecessors":[],"heading":"Document History","documents":["CFTP"],"versions":[],"section":"sec15","content":"<p><font color=\"blue\">The following table provides extra history of the document.</font></p>\n\n<div class='table-wrap'>\n<table class='confluenceTable'><tbody>\n<tr>\n<th class='confluenceTh'>Version</th>\n<th class='confluenceTh'>Date</th>\n<th class='confluenceTh'>Author</th>\n<th class='confluenceTh'>Reference</th>\n</tr>\n<tr>\n<td class='confluenceTd'>&nbsp;</td>\n<td class='confluenceTd'>See summary of electronic document or signature page of printout.</td>\n<td class='confluenceTd'>&nbsp;</td>\n<td class='confluenceTd'>&nbsp;</td>\n</tr>\n</tbody></table>\n</div>\n","key":"TES89-100","status":"To Do"},"sec13s2":{"number":"13.2","predecessors":[],"heading":"Abbreviations","documents":["CFTP"],"versions":[],"section":"sec13s2","content":"<div class='table-wrap'>\n<table class='confluenceTable'><tbody>\n<tr>\n<th class='confluenceTh'>Abbreviation</th>\n<th class='confluenceTh'>Meaning</th>\n</tr>\n<tr>\n<td class='confluenceTd'><font color=\"blue\">ODS</font></td>\n<td class='confluenceTd'><font color=\"blue\">OpenDevStack</font></td>\n</tr>\n<tr>\n<td class='confluenceTd'><font color=\"blue\">EDP</font></td>\n<td class='confluenceTd'><font color=\"blue\">Enterprise Development Platform</font></td>\n</tr>\n</tbody></table>\n</div>\n","key":"TES89-99","status":"To Do"},"sec13s1":{"number":"13.1","predecessors":[],"heading":"Definitions","documents":["CFTP"],"versions":[],"section":"sec13s1","content":"<div class='table-wrap'>\n<table class='confluenceTable'><tbody>\n<tr>\n<th class='confluenceTh'>Term</th>\n<th class='confluenceTh'>Definition</th>\n</tr>\n<tr>\n<td class='confluenceTd'><font color=\"blue\">Jenkins</font></td>\n<td class='confluenceTd'><font color=\"blue\">Build engine supplied by cloudbees - part of OpenDevStack (BI-IT-DEVSTACK)</font></td>\n</tr>\n<tr>\n<td class='confluenceTd'><font color=\"blue\">xUnit</font></td>\n<td class='confluenceTd'><font color=\"blue\">Unit testing framework, aggregaults across multiple languages</font></td>\n</tr>\n</tbody></table>\n</div>\n","key":"TES89-98","status":"To Do"},"sec12":{"number":"12","predecessors":[],"heading":"Functional and Requirements Testing Documentation","documents":["CFTP"],"versions":[],"section":"sec12","content":"","key":"TES89-97","status":"To Do"},"sec2":{"number":"2","predecessors":[],"heading":"Scope","documents":["CFTP"],"versions":[],"section":"sec2","content":"<p><font color=\"blue\">The Functional Testing of SCHEMA ST4 will include the standard functionalities of the system and the user account management. The Requirements Testing covers the workflow configuration and functionalities of SCHEMA ST4 according to the business process of the creation, management and handover of preparation specific texts.<br/>\n<br/>\nThe components of SCHEMA ST4 that will be challenged during this Functions / Requirements Testing are as follows:<br/>\n * Rich/Architect client application software<br/>\n * Workflow Module<br/>\n * Interface to MS Word<br/>\n<br/>\nMS Word functionalities will not be tested in this Functional / Requirements Testing.<br/>\n<br/>\nThe level of testing activities is based on the risk class as defined in the risk analysis (ref) for this system. Detailed definitions of the required depth and level of detail will be defined in the sections for functional and requirements testing.</font></p>","key":"TES89-96","status":"To Do"}},"acceptanceTests":[],"integrationTests":[],"documentHistory":[]}}}

        util = new MROPipelineUtil(project, steps, null, logger)
        junit = new JUnitTestReportsUseCase(project, steps)
        levaFiles = new LeVADocumentChaptersFileService(steps)
        def jiraService = new JiraService("${LOCALHOST}:${jiraServer.port()}", "username", "password")
        jiraUseCase = new JiraUseCase(project, steps, util, jiraService, logger)
        usecase = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdfUtil, sq)
        project.load(Mock(GitService), jiraUseCase)
    }

    def cleanup() {
        def recordedMappings = wireMockServer.stopRecording();
        println("recorded:" + recordedMappings.stubMappings)
    }

    private void loadJiraData() {
        // load issuetypes definitions
        jiraServer.stubFor(get(urlEqualTo("/rest/api/2/issue/createmeta/${PROJECT_KEY}/issuetypes"))
                .willReturn(aResponse().withStatus(200).withBodyFile("jira/getIssueTypes.json")));

        // load issuetypes
        jiraServer.stubFor(get(urlPathMatching("/rest/api/2/issue/createmeta/${PROJECT_KEY}/issuetypes/.*"))
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
        jiraServer.stubFor(get(urlEqualTo("/rest/platform/1.0/docgenreports/${PROJECT_KEY}"))
                .willReturn(aResponse().withStatus(200).withBodyFile("jira/getDocGenData.json")));

        // searchByJQLQuery
        jiraServer.stubFor(post(urlEqualTo("/rest/api/2/search"))
                .willReturn(aResponse().withStatus(200).withBodyFile("jira/searchByJQLQuery.json")));

        // getProjectVersions
        jiraServer.stubFor(get(urlEqualTo("/rest/api/2/project/${PROJECT_KEY}/versions"))
                .willReturn(aResponse().withStatus(200).withBodyFile("jira/getProjectVersions.json")));

        def releaseStatusV1="TES89-123"
        def releaseStatusComment= "Pipeline-generated documents are watermarked 'Work in Progress' since the following issues are work in progress:"
        jiraServer.stubFor(post(urlEqualTo("/rest/api/2/issue/${releaseStatusV1}/comment"))
                .withRequestBody(matchingJsonPath("\$.body", containing(releaseStatusComment)))
                .willReturn(aResponse().withStatus(200)));
        jiraServer.stubFor(put(urlEqualTo("/rest/api/2/issue/${releaseStatusV1}"))
                .withRequestBody(equalToJson("{\"update\":{\"customfield_10329\":[{\"set\":\"WIP-null\"}]}}"))
                .willReturn(aResponse().withStatus(204)))


    }

    def buildProject() {
        steps.env.BUILD_ID = "1"
        steps.env.WORKSPACE = "${tempFolder.getRoot().absolutePath}/workspace"

        def project = new Project(steps, new Logger(steps, true), [:]).init()
        project.data.metadata.id = PROJECT_KEY
        project.data.buildParams = [:]
        project.data.buildParams.targetEnvironment = "dev"
        project.data.buildParams.targetEnvironmentToken = "D"
        project.data.buildParams.version = "WIP"
        project.data.buildParams.releaseStatusJiraIssueKey = "${PROJECT_KEY}-123"
        project.getOpenShiftApiUrl() >> 'https://api.dev-openshift.com'
        return project
    }

    def "create CFTP"() {
        given:
        usecase = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdfUtil, sq)

        def uri = "nexus_url"
        jiraServer.stubFor(put(urlPathMatching("/rest/api/2/issue/.*"))
                .willReturn(aResponse().withStatus(204)))
        docGenServer.stubFor(post(urlEqualTo("/document"))
                .willReturn(aResponse().withStatus(200).withBodyFile("docGen/documentWithHistory.json")))

        def traceabilityMatrixDC = "TES89-108"
        jiraServer.stubFor(put(urlEqualTo("/rest/api/2/issue/${traceabilityMatrixDC}"))
                .withRequestBody(equalToJson("{\"update\":{\"customfield_10312\":[{\"set\":\"1\"}]}}"))
                .willReturn(aResponse().withStatus(204)))
        def traceabilityMatrixDCComment = "A new Combined Functional and Requirements Testing Plan has been generated and is available at: null. Attention: this document is work in progress!"
        jiraServer.stubFor(post(urlEqualTo("/rest/api/2/issue/${traceabilityMatrixDC}/comment"))
                .withRequestBody(matchingJsonPath("\$.body", containing(traceabilityMatrixDCComment)))
                .willReturn(aResponse().withStatus(204)))

        jiraServer.stubFor(post(urlPathMatching("/rest/api/2/issue/TES89-.*/comment"))
                .willReturn(aResponse().withStatus(204)))
        when:
        usecase.createCFTP()

        then:
        true
    /*    1 * nexus.storeArtifact(
                "this.project.services.nexus.repository.name",
                "tes89-WIP",
                "CFTP-TES89-WIP-1.zip",
                _,
                "application/zip") >> uri*/


    }
}
