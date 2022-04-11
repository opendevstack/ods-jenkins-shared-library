package org.ods.orchestration.usecase

import au.com.dius.pact.consumer.groovy.PactBuilder
import groovy.util.logging.Slf4j
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.ods.core.test.usecase.LevaDocUseCaseFactory

import org.ods.core.test.usecase.RepoDataBuilder
import org.ods.core.test.usecase.levadoc.fixture.*
import org.ods.orchestration.util.DocumentHistoryEntry
import org.ods.services.BitbucketService
import org.ods.services.GitService
import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.util.UnirestConfig
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import util.FixtureHelper

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Creates Consumer Contract Testing and validate LeVADocumentUse
 *
 * The generated contract is in target/pacts/ and if you change the contract
 *  you should copy it to https://github.com/opendevstack/ods-document-generation-svc
 *  path: src/test/resources/pacts/
 *
 * TIP:
 *  BUILD_ID: The current build id, such as "2005-08-22_23-59-59" (YYYY-MM-DD_hh-mm-ss)
 *  BUILD_NUMBER: The current build number, such as `153`
 *
 */

@Ignore
@Slf4j
class LeVADocumentUseCasePactSpec extends Specification {

    @Rule
    public TemporaryFolder tempFolder

    LevaDocWiremock levaDocWiremock

    @Shared
    private DocTypeProjectFixtureBase docTypeProjectFixtureBase = new DocTypeProjectFixtureBase()

    def setup() {
        UnirestConfig.init()
    }

    def cleanup() {
        levaDocWiremock?.tearDownWiremock()
    }

    @Unroll
    def "docType:#projectFixture.docType (docType with default params)"() {
        given:
        String docTypeGroup = "defaultParams"
        String docType = projectFixture.getDocType()
        Map projectDataMap = projectFixtureToProjectDataMap(projectFixture)

        LevaDocUseCaseFactory levaDocUseCaseFactory = getLevaDocUseCaseFactory(projectFixture)
        LeVADocumentUseCase useCase = levaDocUseCaseFactory.build(projectFixture)

        expect: "Build the contract and execute against the generated wiremock"
        new PactBuilder()
            .with {
                serviceConsumer "buildDocument.${docTypeGroup}"
                hasPactWith "createDoc.${docTypeGroup}"
                given("project with data:", projectDataMap)
                uponReceiving("a request for /buildDocument ${docType}")
                withAttributes(method: 'post', path: "/levaDoc/${projectDataMap.project}/${projectDataMap.buildNumber}/${docType}")
                withBody([prettyPrint: true], defaultBodyParams(projectFixture))
                willRespondWith(status: 200, headers: ['Content-Type': 'application/json'])
                withBody([prettyPrint: true], defaultDocGenerationResponse(projectFixture))
                runTestAndVerify { context ->
                    String wiremockURL = context.url as String
                    levaDocUseCaseFactory.changeDocGenUrlForPactTesting(wiremockURL)

                    useCase.createDocument("${projectFixture.docType}")
                }
            }

        where:
        projectFixture << docTypeProjectFixtureBase.getProjects(DocTypeProjectFixtureBase.DOC_TYPES_BASIC)
    }

    @Unroll
    def "docType:#projectFixture.docType (for #projectFixture.component and project: #projectFixture.project) with test data"() {
        given:
        String docTypeGroup = "testData"
        String docType = projectFixture.getDocType()
        Map projectDataMap = projectFixtureToProjectDataMap(projectFixture)

        LevaDocUseCaseFactory levaDocUseCaseFactory = getLevaDocUseCaseFactory(projectFixture)
        LeVADocumentUseCase useCase = levaDocUseCaseFactory.build(projectFixture)

        expect: "Build the contract and execute against the generated wiremock"
        new PactBuilder()
            .with {
                serviceConsumer "buildDocument.${docTypeGroup}"
                hasPactWith "createDoc.${docTypeGroup}"
                given("project with data:", projectDataMap)
                uponReceiving("a request for /buildDocument ${docType}")
                withAttributes(method: 'post', path: "/levaDoc/${projectDataMap.project}/${projectDataMap.buildNumber}/${docType}")
                withBody([prettyPrint: true], defaultBodyParams(projectFixture))
                willRespondWith(status: 200, headers: ['Content-Type': 'application/json'])
                withBody([prettyPrint: true], defaultDocGenerationResponse(projectFixture))
                runTestAndVerify { context ->
                    String wiremockURL = context.url as String
                    levaDocUseCaseFactory.changeDocGenUrlForPactTesting(wiremockURL)

                    Map repoAndTestsData = getRepoAndTestsData(projectFixture, false)
                    Map repo = repoAndTestsData.repoData
                    Map data = repoAndTestsData.testsData
                    useCase.createDocument("${projectFixture.docType}", repo, data)
                }
            }

        where:
        projectFixture << docTypeProjectFixtureBase.getProjects(DocTypeProjectFixtureBase.DOC_TYPES_WITH_TEST_DATA)
    }

    @Unroll
    def "docType:#projectFixture.docType (for component #projectFixture.component and project: #projectFixture.project) with test data and component"() {
        given:
        String docTypeGroup = "component"
        String docType = projectFixture.getDocType()
        Map projectDataMap = projectFixtureToProjectDataMap(projectFixture)

        LevaDocUseCaseFactory levaDocUseCaseFactory = getLevaDocUseCaseFactory(projectFixture)
        LeVADocumentUseCase useCase = levaDocUseCaseFactory.build(projectFixture)

        expect: "Build the contract and execute against the generated wiremock"
        new PactBuilder()
            .with {
                serviceConsumer "buildDocument.${docTypeGroup}"
                hasPactWith "createDoc.${docTypeGroup}"
                given("project with data:", projectDataMap)
                uponReceiving("a request for /buildDocument ${docType}")
                withAttributes(method: 'post', path: "/levaDoc/${projectDataMap.project}/${projectDataMap.buildNumber}/${docType}")
                withBody([prettyPrint: true], defaultBodyParamsWithComponent(projectFixture, projectFixture.getComponent()))
                willRespondWith(status: 200, headers: ['Content-Type': 'application/json'])
                withBody([prettyPrint: true], defaultDocGenerationResponse(projectFixture))
                runTestAndVerify { context ->
                    String wiremockURL = context.url as String
                    levaDocUseCaseFactory.changeDocGenUrlForPactTesting(wiremockURL)

                    Map repoAndTestsData = getRepoAndTestsData(projectFixture, true)
                    Map repo = repoAndTestsData.repoData
                    Map data = repoAndTestsData.testsData
                    useCase.createDocument("${projectFixture.docType}", repo, data)
                }
            }

        where:
        projectFixture << docTypeProjectFixtureBase.getProjects(DocTypeProjectFixtureBase.DOC_TYPES_WITH_COMPONENT)
    }

    @Unroll
    def "docType:OVERALL_#projectFixture.docType (docType -overall- with default params)"() {
        given:
        String docTypeGroup = "overall"
        String docType = "OVERALL_${projectFixture.getDocType()}"
        Map projectDataMap = projectFixtureToProjectDataMap(projectFixture)

        LevaDocUseCaseFactory levaDocUseCaseFactory = getLevaDocUseCaseFactory(projectFixture)
        LeVADocumentUseCase useCase = levaDocUseCaseFactory.build(projectFixture)

        expect: "Build the contract and execute against the generated wiremock"
        new PactBuilder()
            .with {
                serviceConsumer "buildDocument.${docTypeGroup}"
                hasPactWith "createDoc.${docTypeGroup}"
                given("project with data:", projectDataMap)
                uponReceiving("a request for /buildDocument ${docType}")
                withAttributes(method: 'post', path: "/levaDoc/${projectDataMap.project}/${projectDataMap.buildNumber}/${docType}")
                withBody([prettyPrint: true], defaultBodyParams(projectFixture))
                willRespondWith(status: 200, headers: ['Content-Type': 'application/json'])
                withBody({})
                runTestAndVerify { context ->
                    String wiremockURL = context.url as String
                    levaDocUseCaseFactory.changeDocGenUrlForPactTesting(wiremockURL)
                    useCase.createDocument("OVERALL_${projectFixture.docType}")
                }
            }

        where:
        projectFixture << docTypeProjectFixtureBase.getProjects(DocTypeProjectFixtureBase.DOC_TYPES_OVERALL)
    }

    private void executeLeVADocumentUseCaseMethod(ProjectFixture projectFixture, String wiremockURL) {
        LeVADocumentUseCase useCase = getLevaDocUseCaseFactory(projectFixture).build(projectFixture)
        useCase.createDocument("${projectFixture.docType}")
    }

    private Map getRepoAndTestsData(ProjectFixture projectFixture, boolean isForComponent) {

        Map repoData = RepoDataBuilder.getRepoForComponent(projectFixture.component)
        Map testsData = repoData.data.tests

        if (isForComponent) {
            repoData.data.remove('tests')
        }

        return [
            repoData: repoData,
            testsData: testsData,
        ]
    }

    private Closure defaultBodyParams(ProjectFixture projectFixture) {
        return {
            build {
                targetEnvironment string("dev")
                targetEnvironmentToken string("D")
                version string("WIP")
                configItem string("BI-IT-DEVSTACK")
                changeDescription string("${projectFixture.getChangeDescription()}")
                changeId string("1.0")
                rePromote string("false")
                releaseStatusJiraIssueKey string("${projectFixture.releaseKey}")
                runDisplayUrl string(LevaDocDataFixture.getJENKINS_URL_RUN_DISPLAY())
                releaseParamVersion string("3.0")
                buildId string("2022-01-22_23-59-59")
                buildURL string(LevaDocDataFixture.getJENKINS_URL_JOB_BUILD())
                jobName string("${projectFixture.getJobName()}")
                keyLike "testResultsURLs", {
                    'unit-backend' string("${projectFixture.getTestResultsUrls()['unit-backend']}")
                    'unit-frontend' string("${projectFixture.getTestResultsUrls()['unit-frontend']}")
                    'acceptance' string("${projectFixture.getTestResultsUrls()['acceptance']}")
                    'installation' string("${projectFixture.getTestResultsUrls()['installation']}")
                    'integration' string("${projectFixture.getTestResultsUrls()['integration']}")
                }
                jenkinsLog string("${projectFixture.getJenkinsLogUrl()}")
            }
            git {
                commit string("1e84b5100e09d9b6c5ea1b6c2ccee8957391beec")
                releaseManagerRepo string("${projectFixture.releaseManagerRepo}")
                releaseManagerBranch string("${projectFixture.releaseManagerBranch}")
                baseTag string("ods-generated-v3.0-3.0-0b11-D")
                targetTag string("ods-generated-v3.0-3.0-0b11-D")
                author string("ODS Jenkins Shared Library System User (undefined)")
                message string("Swingin' The Bottle")
                time string("2021-04-20T14:58:31.042152")
                // commitTime timestamp(FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm.ssZZXX").pattern, "2021-04-20T14:58:31.042152")
            }
            openshift {
                targetApiUrl string("${projectFixture.getOpenshiftData()["targetApiUrl"]}")
            }
        }
    }

    Closure defaultBodyParamsWithComponent(ProjectFixture projectFixture, String component) {
        return defaultBodyParams(projectFixture) << {
            keyLike "repo", {
                id string("${component}")
                type string("ods")
                keyLike "data", {
                    keyLike "openshift", {
                        keyLike "builds", {
                            keyLike "${component}", {
                                buildId string("${component}-3")
                                image string("172.30.1.1:5000/ordgp-cd/${component}@sha256:f6bc9aaed8a842a8e0a4f7e69b044a12c69e057333cd81906c08fd94be044ac4")
                            }
                        }
                        keyLike "deployments", {
                            keyLike "${component}", {
                                podName includesStr("${component}-3", "${component}-3-dshjl")
                                podNamespace string("${projectFixture.project}-dev")
                                podMetaDataCreationTimestamp string("2021-11-21T22:31:04Z")
                                deploymentId string("${component}-3")
                                podNode string("localhost")
                                podIp string("172.17.0.39")
                                podStatus string("Running")
                                podStartupTimeStamp string("2021-11-21T22:31:04Z")
                                podIp string("172.17.0.39")
                                keyLike "containers", {
                                    "${component}" string("172.30.1.1:5000/ordgp-cd/${component}@sha256:f6bc9aaed8a842a8e0a4f7e69b044a12c69e057333cd81906c08fd94be044ac4")
                                }
                            }
                        }
                        sonarqubeScanStashPath string("scrr-report-${component}-1")
                        'SCRR' string("SCRR-ordgp-${component}.docx")
                        'SCRR-MD' string("SCRR-ordgp-${component}.md")
                        testResultsFolder string("build/test-results/test")
                        testResults string("1")
                        xunitTestResultsStashPath string("test-reports-junit-xml-${component}-1")
                        'CREATED_BY_BUILD' string("WIP/1")
                    }
                    keyLike "documents", {}
                    keyLike "git", {
                        branch string("master")
                        commit regexp(~/\w+/, '46a05fce73c811e74f4f96d8f418daa4246ace09')
                        previousCommit nullValue()
                        previousSucessfulCommit nullValue()
                        url string("http://bitbucket.odsbox.lan:7990/scm/${projectFixture.project}/${projectFixture.project}-${component}.git")
                        baseTag string("")
                        targetTag string("")
                    }
                }
                url string("http://bitbucket.odsbox.lan:7990/scm/${projectFixture.project}/${projectFixture.project}-${component}.git")
                branch string("master")
                keyLike "pipelineConfig", {
                    dependencies eachLike([])
                }
                keyLike "metadata", {
                    name string("OpenJDK")
                    description string("OpenJDK is a free and open-source implementation of the Java Platform, Standard Edition. Technologies: Spring Boot 2.1, OpenJDK 11, supplier:https://adoptopenjdk.net")
                    version string("3.x")
                    type string("ods")
                }
            }
        }
    }

    Map projectFixtureToProjectDataMap(ProjectFixture projectFixture) {
        return [
            project: projectFixture.getProject(),
            buildNumber: projectFixture.getBuildNumber(),
            version: projectFixture.getVersion(),
            docType: projectFixture.getDocType(),
        ]
    }

    private LevaDocUseCaseFactory getLevaDocUseCaseFactory(ProjectFixture projectFixture) {
        levaDocWiremock = new LevaDocWiremock()
        levaDocWiremock.setUpWireMock(projectFixture, tempFolder.root)

        // Mocks generation (spock don't let you add this outside a Spec)
        JenkinsService jenkins = Mock(JenkinsService)
        jenkins.unstashFilesIntoPath(_, _, _) >> true
        jenkins.storeCurrentBuildLogInFile(_, _, _) >> {
            String workspace, String buildFolder, String jenkinsLogFileName ->
                Path filePath = Paths.get(workspace, buildFolder, jenkinsLogFileName)
                filePath.getParent().toFile().mkdirs()
                filePath.toFile() << "Jenkins log example file"
        }
        OpenShiftService openShiftService = Mock(OpenShiftService)
        GitService gitService = Mock(GitService)
        BitbucketService bitbucketService = Mock(BitbucketService)
        BitbucketTraceabilityUseCase bbT = Spy(new BitbucketTraceabilityUseCase(bitbucketService, null, null))
        bbT.generateSourceCodeReviewFile() >> new FixtureHelper()
            .getResource(BitbucketTraceabilityUseCaseSpec.EXPECTED_BITBUCKET_CSV).getAbsolutePath()

        return new LevaDocUseCaseFactory(
            levaDocWiremock,
            gitService,
            tempFolder,
            jenkins,
            openShiftService,
            bbT,
            bitbucketService)
    }

    private List<DocumentHistoryEntry> buildExpectedResponse(){
        Map map = [bugs: [], components: [], epics: [key: "ORDGP-124", action: "add"]]
        DocumentHistoryEntry historyEntry = new DocumentHistoryEntry(
            map,
            1,
            "1.0",
            "",
            "Initial document version.")
        return [historyEntry]
    }

    Closure defaultDocGenerationResponse(ProjectFixture projectFixture) {
        return {
            eachLike() {
                keyLike "components", { }
                keyLike "requirements", {
                    requirement eachLike() {
                        "action" string("add")
                        "key" string()
                    }
                }
                keyLike "epics", {
                    requirement eachLike() {
                        "action" string("add")
                        "key" string()
                    }
                }
                keyLike "mitigations", {

                }
                entryId identifier()
                keyLike "bugs", {

                }
                keyLike "risks", {

                }
                keyLike "tests", {

                }
                keyLike "docs", {
                    doc eachLike() {
                        "number" string("3.2")
                        keyLike "documents", {
                            document eachLike([])
                        }
                        "heading" string("Definitions")
                        "action" string("add")
                        "key" string("ordgp-69")
                    }
                }
                previousProjectVersion string("")
                keyLike "techSpecs", {
                    techSpec eachLike([])
                }
                rational string("Initial document version.")
                projectVersion string("1.0")
            }

        }
    }

}
