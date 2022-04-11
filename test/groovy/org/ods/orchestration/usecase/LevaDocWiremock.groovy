package org.ods.orchestration.usecase

import groovy.util.logging.Slf4j
import org.ods.core.test.usecase.levadoc.fixture.ProjectFixture
import org.ods.core.test.wiremock.WiremockManager
import org.ods.core.test.wiremock.WiremockServers
import org.ods.orchestration.service.DocGenService
import org.ods.orchestration.service.JiraService
import org.ods.services.NexusService
import org.springframework.stereotype.Component

@Slf4j
@Component
class LevaDocWiremock {

    private static final boolean GENERATE_EXPECTED_PDF_FILES = Boolean.parseBoolean(System.properties["generateExpectedPdfFiles"] as String)
    private static final boolean RECORD = Boolean.parseBoolean(System.properties["testRecordMode"] as String)

    WiremockManager jiraServer
    WiremockManager docGenServer
    WiremockManager nexusServer
    WiremockManager sonarServer
    WiremockManager bitbucketServer

    void setUpWireMock(ProjectFixture projectFixture, File tempFolder, String subScenarioId = "") {
        startUpWiremockServers(projectFixture, tempFolder, subScenarioId)
    }

    void tearDownWiremock(){
        docGenServer?.tearDown()
        jiraServer?.tearDown()
        nexusServer?.tearDown()
        sonarServer?.tearDown()
        bitbucketServer?.tearDown()
    }

    private void startUpWiremockServers(ProjectFixture projectFixture, File tempFolder, String subScenarioId) {
        String projectKey = projectFixture.project, doctype = projectFixture.docType
        log.info "Using PROJECT_KEY:${projectKey}"
        log.info "Using RECORD Wiremock:${RECORD}"
        log.info "Using GENERATE_EXPECTED_PDF_FILES:${GENERATE_EXPECTED_PDF_FILES}"
        log.info "Using temporal folder:${tempFolder.absolutePath}"

        String overall = (projectFixture.overall) ? "/overall" : ""
        String component = (projectFixture.component) ? "/${projectFixture.component}" : ""
        String subScenario = (subScenarioId) ? ("/subScenario-" + removeSpecialChars(subScenarioId)) : ""
        String scenarioPath = "${projectKey}${component}${subScenario}/${doctype}${overall}/${projectFixture.version}"

        docGenServer = WiremockServers.DOC_GEN.build().withScenario(scenarioPath).startServer(true)
        jiraServer = WiremockServers.JIRA.build().withScenario(scenarioPath).startServer(RECORD)
        nexusServer = WiremockServers.NEXUS.build().withScenario(scenarioPath).startServer(RECORD)
        bitbucketServer = WiremockServers.BITBUCKET.build().withScenario(scenarioPath).startServer(RECORD)
    }

    private String removeSpecialChars(String pathRoute) {
        pathRoute.replace("/", "_")
            .replace(" ", "_")
            .replace("\\", "_")
    }

    JiraService getJiraService(){
        return new JiraService(jiraServer.server().baseUrl(), WiremockServers.JIRA.getUser(), WiremockServers.JIRA.getPassword())
    }

    NexusService getNexusService(){
        return new NexusService(nexusServer.server().baseUrl(), WiremockServers.NEXUS.getUser(), WiremockServers.NEXUS.getPassword())
    }

    DocGenService getDocGenService(){
        return new DocGenService(docGenServer.server().baseUrl())
    }
}
