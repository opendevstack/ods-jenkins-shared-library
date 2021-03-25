package org.ods.orchestration.usecase

import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.ods.orchestration.util.Project
import org.ods.services.BitbucketService
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import spock.lang.Specification
import util.FixtureHelper
import util.LoggerStub
import util.PipelineSteps
import util.wiremock.BitbucketServiceMock

import static org.assertj.core.api.Assertions.*;

@Slf4j
class BitbucketTraceabilityUseCaseSpec extends Specification {
    private static final String EXPECTED_BITBUCKET_CSV = "expected/bitbucket.csv"

    // Change for local development or CI testing
    private static final Boolean RECORD_WIREMOCK = false
    private static final String BB_URL_TO_RECORD = "http://bitbucket.odsbox.lan:7990/"
    // project-readonly-user
    private static final String BB_TOKEN = ""
    private static final String PROJECT_KEY = "EDPT3"

    @Rule
    public TemporaryFolder tempFolder

    BitbucketServiceMock bitbucketServiceMock
    IPipelineSteps steps
    Project project
    ILogger logger
    BitbucketService bitbucketService

    def setup() {
        log.info "Using temporal folder:${tempFolder.getRoot()}"

        steps = new PipelineSteps()
        steps.env.WORKSPACE = tempFolder.getRoot().absolutePath
        logger = new LoggerStub(log)
        project = buildProject(logger)
        bitbucketServiceMock = new BitbucketServiceMock().setUp("csv").startServer(RECORD_WIREMOCK, BB_URL_TO_RECORD)
        bitbucketService = Spy(
                new BitbucketService(
                        null,
                        bitbucketServiceMock.getWireMockServer().baseUrl(),
                        PROJECT_KEY,
                        "passwordCredentialsId",
                        logger))
        bitbucketService.getToken() >> BB_TOKEN
    }

    def buildProject(logger) {
        FileUtils.copyDirectory(new FixtureHelper().getResource("workspace/metadata.yml").parentFile, tempFolder.getRoot())

        steps.env.BUILD_ID = "1"
        steps.env.WORKSPACE = "${tempFolder.getRoot().absolutePath}"

        def project = new Project(steps, logger, [:])
        project.data.metadata = project.loadMetadata("metadata.yml")
        project.data.metadata.id = PROJECT_KEY
        project.data.buildParams = [:]
        project.data.buildParams.targetEnvironment = "dev"
        project.data.buildParams.targetEnvironmentToken = "D"
        project.data.buildParams.version = "WIP"
        project.data.buildParams.releaseStatusJiraIssueKey = "${PROJECT_KEY}-123"
        return project
    }

    def cleanup() {
        bitbucketServiceMock.tearDown()
    }

    def "Generate the csv source code review file"() {
        given: "There are two Bitbucket repositories"
        def useCase = new BitbucketTraceabilityUseCase(bitbucketService, steps, project)

        when: "the source code review file is generated"
        def actualFile = useCase.generateSourceCodeReviewFile()

        then: "the generated file is as the expected csv file"
        reportInfo "Generated csv file:<br/>${readSomeLines(actualFile)}"
        def expectedFile = new FixtureHelper().getResource(EXPECTED_BITBUCKET_CSV)
        assertThat(new File(actualFile)).exists().isFile().hasSameTextualContentAs(expectedFile);
    }

    private String readSomeLines(String filePath){
        File file = new File(filePath)
        def someLines = 3
        String lines = ""
        file.withReader { r -> while( someLines-- > 0 && (( lines += r.readLine() + "<br/>" ) != null));}
        lines += "..."
        return lines
    }
}
