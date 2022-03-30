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
import org.ods.orchestration.usecase.BitbucketTraceabilityUseCase
import org.ods.orchestration.usecase.LeVADocumentUseCase
import org.ods.orchestration.usecase.LevaDocWiremock
import org.ods.orchestration.util.Project
import org.ods.services.BitbucketService
import org.ods.services.GitService
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.util.IPipelineSteps
import util.PipelineSteps

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

    LevaDocUseCaseFactory(LevaDocWiremock levaDocWiremock,
                          GitService gitService,
                          TemporaryFolder tempFolder,
                          JenkinsService jenkins,
                          OpenShiftService os,
                          BitbucketTraceabilityUseCase bbt,
                          BitbucketService bitbucketService){
        this.levaDocWiremock = levaDocWiremock
        this.gitService = gitService
        this.os = os
        this.bbt = bbt
        this.bitbucketService = bitbucketService
        this.jenkins = jenkins
        this.tempFolder = tempFolder
        this.steps = new PipelineSteps()
    }

    LeVADocumentUseCase build(ProjectFixture projectFixture, String docGenUrl = null){
        LevaDocDataFixture dataFixture = new LevaDocDataFixture(tempFolder.root)
        JiraServiceForWireMock jiraServiceForWireMock = buildJiraServiceForWireMock()

        if (!docGenUrl){
            docGenUrl = levaDocWiremock.docGenServer.server().baseUrl()
        }
        String nexusUrl = levaDocWiremock.nexusServer.server().baseUrl()
        def nexusService = new NexusService(nexusUrl, WiremockServers.NEXUS.getUser(), WiremockServers.NEXUS.getPassword())

        ProjectFactory projectFactory = new ProjectFactory(steps, gitService, jiraServiceForWireMock, new LoggerStub(log))
        Project project = projectFactory.getProject(projectFixture, dataFixture)

        return new LeVADocumentUseCase
            (
                project,
                new DocGenService(docGenUrl),
                jenkins,
                nexusService,
                new LeVADocumentParamsMapper(project),
                steps,
                new LoggerStub(log)
            )
    }

    private JiraServiceForWireMock buildJiraServiceForWireMock() {
        String jiraUrl = levaDocWiremock.jiraServer.server().baseUrl()
        new JiraServiceForWireMock(jiraUrl, WiremockServers.JIRA.getUser(), WiremockServers.JIRA.getPassword())
    }
}
