package org.ods.core.test.usecase


import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.junit.rules.TemporaryFolder
import org.ods.core.test.LoggerStub
import org.ods.core.test.jira.JiraServiceForWireMock
import org.ods.core.test.service.BitbucketReleaseManagerService
import org.ods.core.test.usecase.levadoc.fixture.LevaDocDataFixture
import org.ods.core.test.usecase.levadoc.fixture.ProjectFixture
import org.ods.core.test.usecase.levadoc.utils.DocGenServiceForWireMock
import org.ods.core.test.usecase.levadoc.utils.LeVADocumentParamsMapperWithLogging
import org.ods.core.test.wiremock.WiremockServers
import org.ods.orchestration.service.DocGenService
import org.ods.orchestration.usecase.BitbucketTraceabilityUseCase
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.usecase.LeVADocumentUseCase
import org.ods.orchestration.usecase.LevaDocWiremock
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.services.BitbucketService
import org.ods.services.GitService
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.util.IPipelineSteps
import util.PipelineSteps

import java.nio.file.Paths

@Slf4j
class LevaDocUseCaseFactory {

    private LevaDocWiremock levaDocWiremock
    private TemporaryFolder tempFolder
    private JenkinsService jenkins
    private OpenShiftService os
    private GitService gitService
    private BitbucketTraceabilityUseCase bbt
    private IPipelineSteps steps
    private BitbucketService bitbucketService
    private JiraServiceForWireMock jiraService
    private NexusService nexusService
    private DocGenService docGenService
    private Project project
    private LoggerStub loggerStub

    LevaDocUseCaseFactory(LevaDocWiremock levaDocWiremock,
                          GitService gitService,
                          TemporaryFolder tempFolder,
                          JenkinsService jenkins,
                          OpenShiftService os,
                          BitbucketTraceabilityUseCase bbt,
                          BitbucketService bitbucketService,
                          String docGenUrl = null){

        this.levaDocWiremock = levaDocWiremock
        this.gitService = gitService
        this.os = os
        this.bbt = bbt
        this.bitbucketService = bitbucketService
        this.jenkins = jenkins
        this.tempFolder = tempFolder
        this.steps = new PipelineSteps()

        this.jiraService = buildJiraServiceForWireMock()
        this.docGenService = buildDocGenService(docGenUrl)
        this.nexusService = buildNexusServiceForWireMock()
        this.loggerStub = new LoggerStub(log)
    }

    LeVADocumentUseCase build(ProjectFixture projectFixture){

        buildProject(projectFixture)

        return new LeVADocumentUseCase
            (
                project,
                docGenService,
                jenkins,
                nexusService,
                new LeVADocumentParamsMapperWithLogging(project),
                steps,
                loggerStub
            )
    }

    private Project buildProject(ProjectFixture projectFixture) {
        Project project
        try {
            File tmpWorkspace = setTemporalWorkspace(projectFixture)

            Project.METADATA_FILE_NAME = 'metadata.yml'
            project = new Project(steps, loggerStub, [:]).init("refs/heads/master")
            new LevaDocDataFixture().buildFixtureData(projectFixture, project, steps, tmpWorkspace)

            def util = new MROPipelineUtil(project, steps, gitService, loggerStub)
            def jiraUseCase = new JiraUseCase(project, steps, util, jiraService, loggerStub)
            project.load(gitService, jiraUseCase)

            project.repositories.each { repo -> repo.metadata = loadMetadataFromDataFixture(repo) }

        } catch(RuntimeException e){
            loggerStub.error("setup error:${e.getMessage()}", e)
            throw e
        }
        return project
    }

    private JiraServiceForWireMock buildJiraServiceForWireMock() {
        String jiraUrl = levaDocWiremock.jiraServer.server().baseUrl()
        new JiraServiceForWireMock(jiraUrl, WiremockServers.JIRA.getUser(), WiremockServers.JIRA.getPassword())
    }

    private DocGenService buildDocGenService(String docGenUrl) {
        if (!docGenUrl){
            docGenUrl = levaDocWiremock.docGenServer.server().baseUrl()
        }
        return new DocGenServiceForWireMock(docGenUrl)
    }

    private NexusService buildNexusServiceForWireMock() {
        String nexusUrl = levaDocWiremock.nexusServer.server().baseUrl()
        return new NexusService(nexusUrl, WiremockServers.NEXUS.getUser(), WiremockServers.NEXUS.getPassword())
    }

    private def loadMetadataFromDataFixture(repo) {
        return  [
            id: repo.id,
            name: repo.name,
            description: "myDescription-A",
            supplier: "mySupplier-A",
            version: "myVersion-A",
            references: "myReferences-A"
        ]
    }

    private File setTemporalWorkspace(ProjectFixture projectFixture) {
        File tmpWorkspace = new FileTreeBuilder(tempFolder.root).dir("workspace")
        System.setProperty("java.io.tmpdir", tmpWorkspace.absolutePath)
        File workspace = Paths.get("test/resources/workspace/${projectFixture.project}").toFile()

        boolean RECORD = Boolean.parseBoolean(System.properties["testRecordMode"] as String)
        if (RECORD) {
            workspace.mkdirs()
            downloadReleaseManagerRepo(projectFixture, workspace)
        }
        FileUtils.copyDirectory(workspace, tmpWorkspace)
        return tmpWorkspace
    }

    private downloadReleaseManagerRepo(ProjectFixture projectFixture, File tempFolder) {
        new BitbucketReleaseManagerService().downloadRepo(
            projectFixture.project,
            projectFixture.releaseManagerRepo,
            projectFixture.releaseManagerBranch,
            tempFolder.absolutePath)
    }

}
