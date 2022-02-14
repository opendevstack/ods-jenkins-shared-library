package org.ods.core.test.usecase

import groovy.util.logging.Slf4j
import org.junit.rules.TemporaryFolder
import org.ods.core.test.LoggerStub
import org.ods.core.test.jira.JiraServiceForWireMock
import org.ods.core.test.usecase.levadoc.fixture.LevaDocDataFixture
import org.ods.core.test.usecase.levadoc.fixture.ProjectFixture
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
            project.repositories.each { repo -> repo.metadata = dataFixture.loadMetadata(repo) }
        } catch(RuntimeException e){
            log.error("setup error:${e.getMessage()}", e)
            throw e
        }
        return this
    }

    LeVADocumentUseCase build(){
        String nexusUrl = levaDocWiremock.nexusServer.server().baseUrl()
       // String docGenUrl = levaDocWiremock.docGenServer.server().baseUrl()
        String docGenUrl = System.properties["docGen.url"]
        def nexusService = new NexusService(nexusUrl, "user", "password")
        return new LeVADocumentUseCase
            (
                project,
                steps,
                project.jiraUseCase.util,
                new DocGenService(docGenUrl),
                jenkins,
                project.jiraUseCase,
                new JUnitTestReportsUseCase(project, steps),
                new LeVADocumentChaptersFileService(steps),
                nexusService,
                os,
                new PDFUtil(),
                new SonarQubeUseCase(project, steps, nexusService),
                bbt,
                new LoggerStub(log)
            )
    }

    private Project buildProject(ProjectFixture projectFixture, ILogger logger) {
        Map buildParams = dataFixture.buildParams(projectFixture)
        steps.env = dataFixture.loadEnvData(buildParams)

        Project.METADATA_FILE_NAME = 'metadata.yml'
        def project = new Project(steps, logger, [:]).init("refs/tags/CHG0066328")
        project.data.metadata.id = buildParams.projectKey
        project.data.buildParams = buildParams
        project.data.git = dataFixture.buildGitData()
        return project
    }

    private JiraServiceForWireMock buildJiraServiceForWireMock() {
        String jiraUrl = levaDocWiremock.jiraServer.server().baseUrl()
        new JiraServiceForWireMock(jiraUrl, "user", "password")
    }
}
