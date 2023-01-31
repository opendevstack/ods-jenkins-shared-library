package org.ods.orchestration.usecase

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.rules.TemporaryFolder
import org.ods.core.test.usecase.levadoc.fixture.DocTypeProjectFixture
import org.ods.core.test.usecase.levadoc.fixture.DocTypeProjectFixtureWithComponent
import org.ods.core.test.usecase.levadoc.fixture.DocTypeProjectFixturesOverall
import org.ods.core.test.usecase.levadoc.fixture.DocTypeProjectFixtureWithTestData
import org.ods.core.test.usecase.LevaDocUseCaseFactory
import org.ods.core.test.usecase.levadoc.fixture.PipelineProcess
import org.ods.core.test.usecase.levadoc.fixture.ProjectFixture
import org.ods.core.test.wiremock.WiremockServers
import org.ods.core.test.wiremock.WiremockManager
import org.ods.orchestration.util.StringCleanup
import org.ods.services.GitService
import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import spock.lang.Specification
import spock.lang.Unroll
import util.FixtureHelper

/**
 * IMPORTANT: this test use Wiremock files to mock all the external interactions.
 *
 * ==>> HOW TO add more projects:
 *  In order to execute this against any project:
 *  1. Copy into test/resources/workspace/ID-project
 *      - metadata.yml: from Jenkins workspace
 *      - docs: from Jenkins workspace
 *      - xunit: from Jenkins workspace
 *      - ods-state: from Jenkins workspace
 *      - projectData: from Jenkins workspace (or release manager repo in BB)
 *  2. Add a 'release' component to metadata.yml (if not exist). Sample:
 *        - id: release
 *          name: /ID-project-release
 *          type: ods
 *  3. Update test/resources/leva-doc-functional-test-projects.yml
 *
 * ==>> HOW TO use record/play:
 *  We have 2 flags to play with the test:
 *  - RECORD: When TRUE wiremock will record the interaction with the servers and compare the pdf results with the expected
 *  - GENERATE_EXPECTED_PDF_FILES: When TRUE it will remove the expected pdfs and create a new ones
 *
 *  ie:
 *  - RECORD=false & GENERATE_EXPECTED_PDF_FILES=false are the default values. So then it can be executed everywhere.
 *  - RECORD=true & GENERATE_EXPECTED_PDF_FILES=false will record and compare the generate pdfs with the 'old' expected files
 *      ==> with this combination, if there's an error,
 *          we can compare new pdf with the old one, and see the implications of our changes in the pdfs
 *          see here _build/reports/LeVADocs_ the compared results images
 *  - RECORD=true & GENERATE_EXPECTED_PDF_FILES=true will record and generate new pdf expected files
 *
 */
@Slf4j
class LevaDocUseCaseFunctTest extends Specification {

    private static final boolean RECORD = Boolean.parseBoolean(System.properties["testRecordMode"] as String)
    private static final boolean GENERATE_EXPECTED_PDF_FILES = Boolean.parseBoolean(System.properties["generateExpectedPdfFiles"] as String)
    private static final String SAVED_DOCUMENTS = "build/reports/LeVADocs"

    @Rule
    EnvironmentVariables env = new EnvironmentVariables()

    @Rule
    public TemporaryFolder tempFolder

    private WiremockManager jiraServer
    private WiremockManager docGenServer
    private WiremockManager nexusServer
    private WiremockManager sonarServer

    def setupSpec(){
        new File(SAVED_DOCUMENTS).mkdirs()
    }

    def cleanup() {
        docGenServer?.tearDown()
        jiraServer?.tearDown()
        nexusServer?.tearDown()
        sonarServer?.tearDown()
    }

    @Unroll
    def "create #projectFixture.docType for project: #projectFixture.project"() {
        given: "There's a LeVADocument service"
        PipelineProcess pipeline = buildLevaDocUseCasePipeline(projectFixture)
        Map params = pipeline.buildParams()
        LeVADocumentUseCase useCase = getLevaDocUseCaseFactory(projectFixture).loadProject(params).build()

        when: "the user creates a LeVA document"
        useCase."create${projectFixture.docType}"()

        then: "the generated PDF is as expected"
        pipeline.validatePDF()

        where: "Doctypes creation without params"
        projectFixture << new DocTypeProjectFixture().getProjects()
    }

    @Unroll
    def "create #projectFixture.docType with tests files for project: #projectFixture.project"() {
        given: "There's a LeVADocument service"
        PipelineProcess pipeline = buildLevaDocUseCasePipeline(projectFixture)
        Map params = pipeline.buildParams()
        LeVADocumentUseCase useCase = getLevaDocUseCaseFactory(projectFixture).loadProject(params).build()
        Map data =  pipeline.getAllResults(useCase)

        when: "the user creates a LeVA document"
        useCase."create${projectFixture.docType}"(null, data)

        then: "the generated PDF is as expected"
        pipeline.validatePDF()

        where: "Doctypes creation with data params"
        projectFixture << new DocTypeProjectFixtureWithTestData().getProjects()
    }

    @Unroll
    def "create #projectFixture.docType for component #projectFixture.component and project: #projectFixture.project"() {
        given: "There's a LeVADocument service"
        PipelineProcess pipeline = buildLevaDocUseCasePipeline(projectFixture)
        Map params = pipeline.buildParams()
        LeVADocumentUseCase useCase = getLevaDocUseCaseFactory(projectFixture).loadProject(params).build()
        Map input = pipeline.getInputParamsModule(projectFixture, useCase)

        when: "the user creates a LeVA document"
        useCase."create${projectFixture.docType}"(input, input.data)

        then: "the generated PDF is as expected"
        pipeline.validatePDF()

        where: "Doctypes creation with repo and data params"
        projectFixture << new DocTypeProjectFixtureWithComponent().getProjects()
    }

    /**
     * When creating a new test for a project, this test depends on
     * @return
     */
    @Unroll
    def "create Overall #projectFixture.docType for project: #projectFixture.project"() {
        given: "There's a LeVADocument service"
        PipelineProcess pipeline = buildLevaDocUseCasePipeline(projectFixture)
        Map params = pipeline.buildParams()
        LeVADocumentUseCase useCase = getLevaDocUseCaseFactory(projectFixture).loadProject(params).build()
        pipeline.useExpectedComponentDocs(useCase)

        when: "the user creates a LeVA document"
        useCase."createOverall${projectFixture.docType}"()

        then: "the generated PDF is as expected"
        pipeline.validatePDF()

        where:
        projectFixture << new DocTypeProjectFixturesOverall().getProjects()
    }

    private LevaDocUseCaseFactory getLevaDocUseCaseFactory(ProjectFixture projectFixture) {
        String project = projectFixture.project, doctype = projectFixture.docType
        log.info "Using record Wiremock:${RECORD}"
        log.info "Using GENERATE_EXPECTED_PDF_FILES:${GENERATE_EXPECTED_PDF_FILES}"
        log.info "Using temporal folder:${tempFolder.getRoot()}"
        log.info "Using PROJECT_KEY:${project}"

        String component = (projectFixture.component) ? "/${projectFixture.component}" : ""
        String scenarioPath = "${this.class.simpleName}/${project}${component}/${doctype}/${projectFixture.version}"
        docGenServer = WiremockServers.DOC_GEN.build().withScenario(scenarioPath).startServer(RECORD)
        jiraServer = WiremockServers.JIRA.build().withScenario(scenarioPath).startServer(RECORD)
        nexusServer = WiremockServers.NEXUS.build().withScenario(scenarioPath).startServer(RECORD)
        sonarServer = WiremockServers.SONAR_QU.build().withScenario(scenarioPath).startServer(RECORD)

        // Mocks generation (spock don't let you add this outside a Spec)
        JenkinsService jenkins = Mock(JenkinsService)
        jenkins.unstashFilesIntoPath(_, _, _) >> true
        OpenShiftService openShiftService = Mock(OpenShiftService)
        GitService gitService = Mock(GitService)
        BitbucketTraceabilityUseCase bbT = Spy(new BitbucketTraceabilityUseCase(null, null, null))
        def expectedFile = new FixtureHelper()
            .getResource(BitbucketTraceabilityUseCaseSpec.EXPECTED_BITBUCKET_JSON)
        def jsonSlurper = new JsonSlurper()
        List<Map> expectedData = jsonSlurper.parse(expectedFile)
        def sanitizedData = expectedData.collect { pr ->
            return [
                date: pr.date,
                authorName: sanitize(pr.authorName),
                authorEmail: sanitize(pr.authorEmail),
                reviewers: pr.reviewers.collect { reviewer ->
                    return [
                        reviewerName: sanitize(reviewer.reviewerName),
                        reviewerEmail: sanitize(reviewer.reviewerEmail),
                    ]
                },
                url: sanitize(pr.url),
                commit: pr.commit,
                component: pr.component,
            ]
        }
        bbT.getPRMergeInfo() >> sanitizedData

        return new LevaDocUseCaseFactory(
            jiraServer,
            docGenServer,
            nexusServer,
            sonarServer,
            env,
            tempFolder,
            jenkins,
            openShiftService,
            gitService,
            bbT)
    }

    private PipelineProcess buildLevaDocUseCasePipeline(projectFixture) {
        return new PipelineProcess(
            this.class.simpleName as String,
            projectFixture,
            GENERATE_EXPECTED_PDF_FILES,
            SAVED_DOCUMENTS,
            tempFolder)
    }

    private String sanitize(String s) {
        return s ? StringCleanup.removeCharacters(s, [
            '/': '/\u200B',
            '@': '@\u200B',
        ]) : 'N/A'
    }

}

