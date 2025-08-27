package org.ods.core.test.usecase

import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.rules.TemporaryFolder
import org.ods.core.test.LoggerStub
import org.ods.core.test.jira.JiraServiceForWireMock
import org.ods.core.test.wiremock.WiremockManager
import org.ods.core.test.wiremock.WiremockServers
import org.ods.orchestration.service.DocGenService
import org.ods.orchestration.service.LeVADocumentChaptersFileService
import org.ods.orchestration.usecase.BitbucketTraceabilityUseCase
import org.ods.orchestration.usecase.JUnitTestReportsUseCase
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.usecase.LeVADocumentUseCase
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PDFUtil
import org.ods.orchestration.util.Project
import org.ods.services.GitService
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import util.PipelineSteps

@Slf4j
class LevaDocUseCaseFactory {
    private IPipelineSteps steps = new PipelineSteps()

    private WiremockManager jiraServer
    private WiremockManager docGenServer
    private WiremockManager nexusServer
    private EnvironmentVariables env
    private TemporaryFolder tempFolder
    private JenkinsService jenkins
    private OpenShiftService os
    private GitService gitService
    private Project project
    private BitbucketTraceabilityUseCase bbt

    LevaDocUseCaseFactory(WiremockManager jiraServer,
                          WiremockManager docGenServer,
                          WiremockManager nexusServer,
                          EnvironmentVariables env,
                          TemporaryFolder tempFolder,
                          JenkinsService jenkins,
                          OpenShiftService os,
                          GitService gitService,
                          BitbucketTraceabilityUseCase bbt){
        this.docGenServer = docGenServer
        this.jiraServer = jiraServer
        this.nexusServer = nexusServer
        this.gitService = gitService

        this.os = os
        this.bbt = bbt
        this.jenkins = jenkins
        this.tempFolder = tempFolder
        this.env = env
    }

    def loadProject(Map buildParams) {
        log.info "loadProject with:[${buildParams}]"
        try {
            def logger = new LoggerStub(log)
            project = buildProject(buildParams, logger)
            def util = new MROPipelineUtil(project, steps, null, logger)
            def jiraUseCase = new JiraUseCase(project, steps, util, buildJiraServiceForWireMock(), logger)
            project.load(gitService, jiraUseCase)
            project.data.openshift.targetApiUrl = "https://openshift-sample"
            project.repositories.each { repo ->
                repo.metadata = loadMetadata(repo)
                repo.doInstall = MROPipelineUtil.PipelineConfig.INSTALLABLE_REPO_TYPES.contains(repo.type)
                repo.include = true
            }
        } catch(RuntimeException e){
            log.error("setup error:${e.getMessage()}", e)
            throw e
        }
        return this
    }

    LeVADocumentUseCase build(){
        def nexusService = new NexusService(nexusServer.mock().baseUrl(), WiremockServers.NEXUS.user, WiremockServers.NEXUS.password)
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
                nexusService,
                os,
                new PDFUtil(),
                bbt,
                new LoggerStub(log)
            )
    }

    private Project buildProject(Map buildParams, ILogger logger) {
        def tmpWorkspace = new FileTreeBuilder(tempFolder.getRoot()).dir("workspace")
        System.setProperty("java.io.tmpdir", tmpWorkspace.absolutePath)
        FileUtils.copyDirectory(new File("test/resources/workspace/${buildParams.projectKey}"), tmpWorkspace)

        steps.env.BUILD_ID = "1"
        steps.env.WORKSPACE = tmpWorkspace.absolutePath
        steps.env.RUN_DISPLAY_URL =""
        steps.env.version = buildParams.version
        steps.env.configItem = "Functional-Test"
        steps.env.RELEASE_PARAM_VERSION = "4.0"
        steps.env.BUILD_NUMBER = "666"
        steps.env.BUILD_URL = "https://jenkins-sample"
        steps.env.JOB_NAME = "ofi2004-cd/ofi2004-cd-release-master"

        Project.METADATA_FILE_NAME = 'metadata.yml'

        def project = new Project(steps, logger, [:]).init()
        project.data.metadata.id = buildParams.projectKey
        project.data.buildParams = buildParams
        project.data.git = buildGitData()
        return project
    }

    private JiraServiceForWireMock buildJiraServiceForWireMock() {
        new JiraServiceForWireMock(jiraServer.mock().baseUrl(), WiremockServers.JIRA.user, WiremockServers.JIRA.password)
    }

    def buildGitData() {
        return  [
            commit: "1e84b5100e09d9b6c5ea1b6c2ccee8957391beec",
            url: "https://bitbucket/scm/ofi2004/ofi2004-release.git",
            baseTag: "ods-generated-v3.0-3.0-0b11-D",
            targetTag: "ods-generated-v3.0-3.0-0b11-D",
            author: "s2o",
            message: "Swingin' The Bottle",
            time: "2021-04-20T14:58:31.042152",
        ]
    }

    def loadMetadata(repo) {
        return  [
            id: repo.id,
            name: repo.name,
            description: "myDescription-A",
            supplier: "mySupplier-A",
            version: "myVersion-A",
            references: "myReferences-A"
        ]
    }
}
