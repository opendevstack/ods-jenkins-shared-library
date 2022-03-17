package org.ods.orchestration.usecase

import groovy.util.logging.Slf4j
import net.sf.json.groovy.JsonSlurper
import net.sf.json.test.JSONAssert
import org.apache.commons.io.FileUtils
import org.json.JSONArray
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.ods.core.test.jira.BitbucketServiceForWireMock
import org.ods.core.test.usecase.ProjectFactory
import org.ods.core.test.usecase.levadoc.fixture.DocTypeProjectFixture
import org.ods.core.test.usecase.levadoc.fixture.LevaDocDataFixture
import org.ods.core.test.usecase.levadoc.fixture.ProjectFixture
import org.ods.core.test.wiremock.WiremockManager
import org.ods.core.test.wiremock.WiremockServers
import org.ods.orchestration.util.Project
import org.ods.orchestration.util.StringCleanup
import org.ods.services.BitbucketService
import org.ods.services.GitService
import org.ods.util.IPipelineSteps
import org.ods.util.UnirestConfig
import spock.lang.Specification
import util.FixtureHelper
import org.ods.core.test.LoggerStub
import util.PipelineSteps
import org.ods.core.test.jira.JiraServiceForWireMock

import static org.assertj.core.api.Assertions.*

@Slf4j
class BitbucketTraceabilityUseCaseSpec extends Specification {
    static final String EXPECTED_BITBUCKET_CSV = "expected/bitbucket.csv"
    private static final String EXPECTED_BITBUCKET_JSON = "expected/bitbucket.json"

    LevaDocWiremock levaDocWiremock

    @Rule
    public TemporaryFolder tempFolder

    IPipelineSteps steps
    GitService gitService
    JiraServiceForWireMock jiraServiceForWireMock
    LoggerStub loggerStub

    def setup() {
        log.info "Using temporal folder:${tempFolder.getRoot()}"

        steps = new PipelineSteps()
        steps.env.WORKSPACE = tempFolder.getRoot().absolutePath

        gitService = Mock(GitService)

        loggerStub = new LoggerStub(log)
        UnirestConfig.init()
    }

    def cleanup() {
        levaDocWiremock?.tearDownWiremock()
    }

    def "Generate the csv source code review file"() {
        given: "There are two Bitbucket repositories"

        ProjectFactory projectFactory = new ProjectFactory(steps, gitService, jiraServiceForWireMock, loggerStub)
        LevaDocDataFixture levaDocDataFixture = new LevaDocDataFixture(tempFolder.getRoot())
        Project project = projectFactory.loadProject(projectFixture, levaDocDataFixture).getProject()
        fixProject(project, projectFixture)

        levaDocWiremock = new LevaDocWiremock()
        levaDocWiremock.setUpWireMock(projectFixture, tempFolder.root)
        WiremockManager bitBucketServer = levaDocWiremock.getBitbucketServer()
        BitbucketService bitbucketService = getBitbucketService(projectFixture.project)

        def useCase = new BitbucketTraceabilityUseCase(bitbucketService, steps, project)

        when: "the source code review file is generated"
        def actualFile = useCase.generateSourceCodeReviewFile()

        then: "the generated file is as the expected csv file"
        reportInfo "Generated csv file:<br/>${readSomeLines(actualFile)}"
        def expectedFile = new FixtureHelper().getResource(EXPECTED_BITBUCKET_CSV)
        assertThat(new File(actualFile)).exists().isFile().hasSameTextualContentAs(expectedFile);

        where:
        projectFixture << new DocTypeProjectFixture().getProjects()
    }

    def "Read the csv source code review file"() {
        given: "There are two Bitbucket repositories"
        ProjectFactory projectFactory = new ProjectFactory(steps, gitService, jiraServiceForWireMock, loggerStub)
        LevaDocDataFixture levaDocDataFixture = new LevaDocDataFixture(tempFolder.getRoot())
        Project project = projectFactory.loadProject(projectFixture, levaDocDataFixture).getProject()
        fixProject(project, projectFixture)

        levaDocWiremock = new LevaDocWiremock()
        levaDocWiremock.setUpWireMock(projectFixture, tempFolder.root)
        BitbucketService bitbucketService = getBitbucketService(projectFixture.project)

        def useCase = new BitbucketTraceabilityUseCase(bitbucketService, steps, project)

        and: 'The characters to change'
        Map CHARACTERS = [
            '/': '/\u200B',
            '@': '@\u200B',
        ]
        when: "the source code review file is readed"
        def data = useCase.readSourceCodeReviewFile(
            new FixtureHelper().getResource(EXPECTED_BITBUCKET_CSV).getAbsolutePath())
        JSONArray result = new JSONArray(data)

        then: "the data contains the same csv info"
        def expectedFile = new FixtureHelper().getResource(EXPECTED_BITBUCKET_JSON)
        def jsonSlurper = new JsonSlurper()
        def expected = jsonSlurper.parse(expectedFile)

        JSONAssert.assertJsonEquals(StringCleanup.removeCharacters(expected.toString(), CHARACTERS), result.toString())

        where:
        projectFixture << new DocTypeProjectFixture().getProjects()
    }

    private String readSomeLines(String filePath){
        File file = new File(filePath)
        def someLines = 3
        String lines = ""
        file.withReader { r -> while( someLines-- > 0 && (( lines += r.readLine() + "<br/>" ) != null));}
        lines += "..."
        return lines
    }

    private void fixProject(Project project, ProjectFixture projectFixture) {
        FileUtils.copyDirectory(new FixtureHelper().getResource("workspace/metadata.yml").parentFile, tempFolder.getRoot())

        steps.env.BUILD_ID = "1"
        steps.env.WORKSPACE = "${tempFolder.getRoot().absolutePath}"

    }

    private BitbucketService getBitbucketService(String project) {
        String bitBucketURL = levaDocWiremock.nexusServer.server().baseUrl()
        BitbucketService bitbucketService = new BitbucketServiceForWireMock(
            bitBucketURL, WiremockServers.BITBUCKET.getUser(), WiremockServers.BITBUCKET.getPassword(), project, loggerStub)

    }
}
