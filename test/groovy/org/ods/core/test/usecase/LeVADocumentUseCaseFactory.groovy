package org.ods.core.test.usecase

import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.rules.TemporaryFolder
import org.ods.core.test.LoggerStub
import org.ods.core.test.jira.JiraServiceForWireMock
import org.ods.core.test.wiremock.WiremockManager
import org.ods.orchestration.service.DocGenService
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
import util.FixtureHelper

@Slf4j
class LeVADocumentUseCaseFactory {
    // Add jiraUser & jiraPassword in gradle.properties to change Jira user/password
    private static final String JIRA_USER = System.properties["jiraUser"]
    private static final String JIRA_PASSWORD = System.properties["jiraPassword"]

    private Project project
    private MROPipelineUtil util
    private DocGenService docGen
    private JiraUseCase jiraUseCase
    private JUnitTestReportsUseCase junit
    private LeVADocumentChaptersFileService levaFiles
    private PDFUtil pdfUtil
    private LeVADocumentUseCase usecase
    private ILogger logger
    private WiremockManager jiraServer
    private WiremockManager docGenServer
    private EnvironmentVariables env
    private TemporaryFolder tempFolder
    private JenkinsService jenkins
    private NexusService nexus
    private OpenShiftService os
    private SonarQubeUseCase sq
    private GitService gitService
    private IPipelineSteps steps

    LeVADocumentUseCaseFactory(WiremockManager jiraServer,
                               WiremockManager docGenServer,
                               EnvironmentVariables env,
                               TemporaryFolder tempFolder,
                               JenkinsService jenkins,
                               NexusService nexus,
                               OpenShiftService os,
                               SonarQubeUseCase sq,
                               GitService gitService,
                               IPipelineSteps steps){
        this.sq = sq
        this.os = os
        this.nexus = nexus
        this.jenkins = jenkins
        this.tempFolder = tempFolder
        this.env = env
        this.docGenServer = docGenServer
        this.jiraServer = jiraServer
        this.gitService = gitService
        this.steps = steps
    }

    LeVADocumentUseCase createLeVADocumentUseCase(Map buildParams){
        setup(buildParams)
        return new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdfUtil, sq)
    }

    private def setup(Map buildParams) {
        System.setProperty("java.io.tmpdir", tempFolder.getRoot().absolutePath)
        FileUtils.copyDirectory(new FixtureHelper().getResource("logback.xml").parentFile, tempFolder.getRoot());

        steps.env.RUN_DISPLAY_URL =""
        pdfUtil = new PDFUtil()
        logger = new LoggerStub(log)

        project = buildProject(buildParams)

        util = new MROPipelineUtil(project, steps, null, logger)
        junit = new JUnitTestReportsUseCase(project, steps)
        levaFiles = new LeVADocumentChaptersFileService(steps)
        def jiraService = new JiraServiceForWireMock(jiraServer.mock().baseUrl(), JIRA_USER, JIRA_PASSWORD)
        jiraUseCase = new JiraUseCase(project, steps, util, jiraService, logger)
        docGen = new DocGenService(docGenServer.mock().baseUrl())
        usecase = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdfUtil, sq)
        project.load(gitService, jiraUseCase)
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
