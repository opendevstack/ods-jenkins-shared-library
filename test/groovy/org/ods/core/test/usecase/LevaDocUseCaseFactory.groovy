package org.ods.core.test.usecase

import groovy.util.logging.Slf4j
import org.junit.rules.TemporaryFolder
import org.ods.core.test.LoggerStub
import org.ods.core.test.jira.JiraServiceForWireMock
import org.ods.core.test.usecase.levadoc.fixture.LevaDocDataFixture
import org.ods.core.test.usecase.levadoc.fixture.ProjectFixture
import org.ods.core.test.wiremock.WiremockServers
import org.ods.orchestration.mapper.LeVADocumentParamsMapper
import org.ods.orchestration.service.DocGenService
import org.ods.orchestration.service.LeVADocumentChaptersFileService
import org.ods.orchestration.usecase.BitbucketTraceabilityUseCase
import org.ods.orchestration.usecase.JUnitTestReportsUseCase
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.usecase.LeVADocumentUseCase
import org.ods.orchestration.usecase.LevaDocWiremock
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
import util.PipelineSteps

@Slf4j
class LevaDocUseCaseFactory {

    private LevaDocWiremock levaDocWiremock
    private TemporaryFolder tempFolder
    private JenkinsService jenkins
    private OpenShiftService os
    private GitService gitService
    private Project project
    private BitbucketTraceabilityUseCase bbt
    private IPipelineSteps steps
    private LevaDocDataFixture dataFixture

    LevaDocUseCaseFactory(LevaDocWiremock levaDocWiremock,
                          GitService gitService,
                          TemporaryFolder tempFolder,
                          JenkinsService jenkins,
                          OpenShiftService os,
                          BitbucketTraceabilityUseCase bbt){
        this.levaDocWiremock = levaDocWiremock
        this.gitService = gitService
        this.os = os
        this.bbt = bbt
        this.jenkins = jenkins
        this.tempFolder = tempFolder
        this.steps = new PipelineSteps()
        this.dataFixture = new LevaDocDataFixture(tempFolder.root)
    }

    def loadProject(ProjectFixture projectFixture) {
        try {
            def logger = new LoggerStub(log)
            project = buildProject(projectFixture, logger)
            def util = new MROPipelineUtil(project, steps, null, logger)
            def jiraUseCase = new JiraUseCase(project, steps, util, buildJiraServiceForWireMock(), logger)
            project.load(gitService, jiraUseCase)
            project.data.openshift.targetApiUrl = "https://openshift-sample"
            project.data.build.testResultsURLs = generateTestResultURLs()
            project.repositories.each { repo -> repo.metadata = dataFixture.loadMetadata(repo) }
        } catch(RuntimeException e){
            log.error("setup error:${e.getMessage()}", e)
            throw e
        }
        return this
    }

    Map<String, Map<String, String>> generateTestResultURLs() {
        def result = [:]

        result[Project.TestType.UNIT + "-Repo1"] = testStructure("Unit")
        result[Project.TestType.UNIT + "-Repo2"] = testStructure("Unit")
        result[Project.TestType.ACCEPTANCE] = testStructure("Unit")
        result[Project.TestType.INSTALLATION] = testStructure("Unit")
        result[Project.TestType.INTEGRATION] = testStructure("Unit")

    }

    Map<String, String> generateTestStruct(String type) {
        Map<String, String> result = [:]
        result["url"] = "https//nexus-sample/${type}"
        result["type"] = "${type}"
        result["path"] = "path-to-the-files"

    }

    LeVADocumentUseCase build(String docGenUrl = null){
        if (!docGenUrl){
            docGenUrl = levaDocWiremock.docGenServer.server().baseUrl()
        }
        String nexusUrl = levaDocWiremock.nexusServer.server().baseUrl()
        def nexusService = new NexusService(nexusUrl, WiremockServers.NEXUS.getUser(), WiremockServers.NEXUS.getPassword())
        return new LeVADocumentUseCase
            (
                project,
                new DocGenService(docGenUrl),
                jenkins,
                nexusService,
                new LeVADocumentParamsMapper(project),
                new LoggerStub(log)
            )
    }

    private Project buildProject(ProjectFixture projectFixture, ILogger logger) {
        Project.METADATA_FILE_NAME = 'metadata.yml'
        steps.env = dataFixture.loadEnvData(projectFixture)
        def project = new Project(steps, logger, [:]).init("refs/heads/master")
        project.data.metadata.id = projectFixture.project
        project.data.buildParams =  dataFixture.buildParams(projectFixture)
        project.data.git = dataFixture.buildGitData()
        return project
    }

    private JiraServiceForWireMock buildJiraServiceForWireMock() {
        String jiraUrl = levaDocWiremock.jiraServer.server().baseUrl()
        new JiraServiceForWireMock(jiraUrl, WiremockServers.JIRA.getUser(), WiremockServers.JIRA.getPassword())
    }
}
