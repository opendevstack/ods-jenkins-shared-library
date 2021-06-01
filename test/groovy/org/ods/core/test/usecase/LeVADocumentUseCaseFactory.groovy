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

@Slf4j
class LeVADocumentUseCaseFactory {
    // Add jiraUser & jiraPassword in gradle.properties to change Jira user/password
    private static final String JIRA_USER = System.properties["jiraUser"]
    private static final String JIRA_PASSWORD = System.properties["jiraPassword"]

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
    private Project project

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

    def loadProject(Map buildParams) {
        log.info "loadProject with:[${buildParams}]"
        try {
            project = loadProject(buildParams)
            def logger = new LoggerStub(log)
            project = buildProject(buildParams, logger)
            def util = new MROPipelineUtil(project, steps, null, logger)
            def jiraUseCase = new JiraUseCase(project, steps, util, buildJiraServiceForWireMock(), logger)
            project.load(gitService, jiraUseCase)
        } catch(RuntimeException e){
            log.error("setup error:${e.getMessage()}", e)
            throw e
        }
        return this
    }

    LeVADocumentUseCase createLeVADocumentUseCase(){
        return new LeVADocumentUseCase
            (
                project,
                steps,
                project.jiraUseCase.util,
                new DocGenService(docGenServer.mock().baseUrl()),
                jenkins,
                project.jiraUseCase,
                new JUnitTestReportsUseCase(project, steps),
                new LeVADocumentChaptersFileService(steps),
                nexus,
                os,
                new PDFUtil(),
                sq
            )
    }

    private Project buildProject(Map buildParams, ILogger logger) {
        System.setProperty("java.io.tmpdir", tempFolder.getRoot().absolutePath)
        def tmpWorkspace = new FileTreeBuilder(tempFolder.getRoot()).dir("workspace")
        FileUtils.copyDirectory(new File("test/resources/workspace"), tmpWorkspace)

        steps.env.BUILD_ID = "1"
        steps.env.WORKSPACE = tmpWorkspace.absolutePath
        steps.env.RUN_DISPLAY_URL =""
        Project.METADATA_FILE_NAME = 'metadata.yml'

        def project = new Project(steps, logger, [:]).init()
        project.data.metadata.id = buildParams.projectKey
        project.data.buildParams = buildParams

        return project
    }

    private JiraServiceForWireMock buildJiraServiceForWireMock() {
        new JiraServiceForWireMock(jiraServer.mock().baseUrl(), JIRA_USER, JIRA_PASSWORD)
    }
}
