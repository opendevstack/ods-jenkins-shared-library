package org.ods.core.test.usecase

import groovy.util.logging.Slf4j
import hudson.model.Run
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
        log.info "createLeVADocumentUseCase with:[${buildParams}]"
        setupProject(buildParams)
        return new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdfUtil, sq)
    }

    private void setupProject(Map buildParams) {
        try {
            logger = new LoggerStub(log)
            project = buildProject(buildParams, logger)
            pdfUtil = new PDFUtil()
            util = new MROPipelineUtil(project, steps, null, logger)
            junit = new JUnitTestReportsUseCase(project, steps)
            levaFiles = new LeVADocumentChaptersFileService(steps)
            jiraUseCase = new JiraUseCase(project, steps, util, buildJiraServiceForWireMock(), logger)
            docGen = new DocGenService(docGenServer.mock().baseUrl())
            project.load(gitService, jiraUseCase)
        } catch(RuntimeException e){
            log.error("setup error:${e.getMessage()}", e)
            throw e
        }
    }

    private JiraServiceForWireMock buildJiraServiceForWireMock() {
        new JiraServiceForWireMock(jiraServer.mock().baseUrl(), JIRA_USER, JIRA_PASSWORD)
    }

    def buildProject(Map buildParams, ILogger logger) {
        System.setProperty("java.io.tmpdir", tempFolder.getRoot().absolutePath)
        def tmpWorkspace = new FileTreeBuilder(tempFolder.getRoot()).dir("workspace")
        FileUtils.copyDirectory(new File("test/resources/workspace"), tmpWorkspace)

        steps.env.BUILD_ID = "1"
        steps.env.WORKSPACE = tmpWorkspace.absolutePath
        steps.env.RUN_DISPLAY_URL =""

        def project = new Project(steps, logger, [:]).init()
        project.data.metadata.id = buildParams.projectKey
        project.data.buildParams = buildParams

        return project
    }
}
