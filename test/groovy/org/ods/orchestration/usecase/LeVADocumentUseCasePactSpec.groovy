package org.ods.orchestration.usecase

import au.com.dius.pact.consumer.groovy.PactBuilder
import groovy.util.logging.Slf4j
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.ods.core.test.usecase.LevaDocUseCaseFactory
import org.ods.core.test.usecase.levadoc.fixture.*
import org.ods.services.GitService
import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import spock.lang.Specification
import spock.lang.Unroll
import util.FixtureHelper

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
@Slf4j
class LeVADocumentUseCasePactSpec extends Specification {

    @Rule
    public TemporaryFolder tempFolder

    LevaDocWiremock levaDocWiremock

    def cleanup() {
        levaDocWiremock?.tearDownWiremock()
    }

    @Unroll
    def "Consumer contract test docType:#projectFixture.docType (docType with default params)"() {
        given:
        String docTypeGroup = "defaultParams"
        String docType = projectFixture.getDocType()
        Map params = projectData(docType)
        String generatedFile = "${docType}-ORDGP-WIP-2022-01-22_23-59-59.zip"
        String urlReturnFile = "repository/leva-documentation/${params.project.toLowerCase()}-${params.version}/${generatedFile}"

        expect: "Build the contract and execute against the generated wiremock"
        new PactBuilder()
            .with {
                serviceConsumer "buildDocument.${docTypeGroup}"
                hasPactWith "createDoc.${docTypeGroup}"
                given("project with data:", params)
                uponReceiving("a request for /buildDocument ${docType}")
                withAttributes(method: 'post', path: "/levaDoc/${params.project}/${params.buildNumber}/${docType}")
                withBody([prettyPrint: true], defaultBodyParams())
                willRespondWith(status: 200, headers: ['Content-Type': 'application/json'])
                withBody([prettyPrint: true], defaultDocGenerationResponse(urlReturnFile))
                runTestAndVerify { context ->
                    String response = executeLeVADocumentUseCaseMethod(projectFixture, context.url)
                    assert response == "http://lalala/${urlReturnFile}"
                }
            }

        where:
        projectFixture << new DocTypeProjectFixture().getProjects()
    }

    @Unroll
    def "Consumer contract test docType:#projectFixture.docType (for #projectFixture.component and project: #projectFixture.project) with test data"() {
        given:
        String docTypeGroup = "testData"
        String docType = projectFixture.getDocType()
        Map params = projectData(docType)
        String generatedFile = "${docType}-FRML24113-WIP-2022-01-22_23-59-59.zip"
        String urlReturnFile = "repository/leva-documentation/${params.project.toLowerCase()}-${params.version}/${generatedFile}"

        expect: "Build the contract and execute against the generated wiremock"
        new PactBuilder()
            .with {
                serviceConsumer "buildDocument.${docTypeGroup}"
                hasPactWith "createDoc.${docTypeGroup}"
                given("project with data:", params)
                uponReceiving("a request for /buildDocument ${docType}")
                withAttributes(method: 'post', path: "/levaDoc/${params.project}/${params.buildNumber}/${docType}")
                withBody([prettyPrint: true], defaultBodyParamsWithTests())
                willRespondWith(status: 200, headers: ['Content-Type': 'application/json'])
                withBody([prettyPrint: true], defaultDocGenerationResponse(urlReturnFile))
                runTestAndVerify { context ->
                    String response = executeLeVADocumentUseCaseMethodWithTestData(projectFixture, context.url)
                    assert response == "http://lalala/${urlReturnFile}"
                }
            }

        where:
        projectFixture << new DocTypeProjectFixtureWithTestData().getProjects()
    }

    @Unroll
    def "Consumer contract test docType:#projectFixture.docType (for component #projectFixture.component and project: #projectFixture.project) with test data and component"() {
        given:
        String docTypeGroup = "component"
        String docType = projectFixture.getDocType()
        Map params = projectData(docType)
        String generatedFile = "${docType}-ORDGP-WIP-2022-01-22_23-59-59.zip"
        String urlReturnFile = "repository/leva-documentation/${params.project.toLowerCase()}-${params.version}/${generatedFile}"

        expect: "Build the contract and execute against the generated wiremock"
        new PactBuilder()
            .with {
                serviceConsumer "buildDocument.${docTypeGroup}"
                hasPactWith "createDoc.${docTypeGroup}"
                given("project with data:", params)
                uponReceiving("a request for /buildDocument ${docType}")
                withAttributes(method: 'post', path: "/levaDoc/${params.project}/${params.buildNumber}/${docType}")
                withBody([prettyPrint: true], defaultBodyParamsWithComponent(projectFixture.getComponent()))
                willRespondWith(status: 200, headers: ['Content-Type': 'application/json'])
                withBody([prettyPrint: true], defaultDocGenerationResponse(urlReturnFile))
                runTestAndVerify { context ->
                    String response = executeLeVADocumentUseCaseMethodWithComponent(projectFixture, context.url)
                    assert response == "http://lalala/${urlReturnFile}"
                }
            }

        where:
        projectFixture << new DocTypeProjectFixtureWithComponent().getProjects()
    }

    @Unroll
    def "Consumer contract test docType:OVERALL_#projectFixture.docType (docType -overall- with default params)"() {
        given:
        String docTypeGroup = "overall"
        String docType = "OVERALL_${projectFixture.getDocType()}"
        Map params = projectData(docType)
        String generatedFile = "${docType}-ORDGP-WIP-2022-01-22_23-59-59.zip"
        String urlReturnFile = "repository/leva-documentation/${params.project.toLowerCase()}-${params.version}/${generatedFile}"

        expect: "Build the contract and execute against the generated wiremock"
        new PactBuilder()
            .with {
                serviceConsumer "buildDocument.${docTypeGroup}"
                hasPactWith "createDoc.${docTypeGroup}"
                given("project with data:", params)
                uponReceiving("a request for /buildDocument ${docType}")
                withAttributes(method: 'post', path: "/levaDoc/${params.project}/${params.buildNumber}/${docType}")
                withBody([prettyPrint: true], defaultBodyParams())
                willRespondWith(status: 200, headers: ['Content-Type': 'application/json'])
                withBody([prettyPrint: true], defaultDocGenerationResponse(urlReturnFile))
                runTestAndVerify { context ->
                    String response = executeLeVADocumentUseCaseOverallMethod(projectFixture, context.url)
                    assert response == "http://lalala/${urlReturnFile}"
                }
            }

        where:
        projectFixture << new DocTypeProjectFixturesOverall().getProjects()
    }

    private String executeLeVADocumentUseCaseMethod(ProjectFixture projectFixture, String wiremockURL) {
        LeVADocumentUseCase useCase = getLevaDocUseCaseFactory(projectFixture).loadProject(projectFixture).build(wiremockURL)
        return useCase."create${projectFixture.docType}"()
    }

    private String executeLeVADocumentUseCaseOverallMethod(ProjectFixture projectFixture, String wiremockURL) {
        LeVADocumentUseCase useCase = getLevaDocUseCaseFactory(projectFixture).loadProject(projectFixture).build(wiremockURL)
        return useCase."createOverall${projectFixture.docType}"()
    }

    private String executeLeVADocumentUseCaseMethodWithTestData(ProjectFixture projectFixture, String wiremockURL) {
        LeVADocumentUseCase useCase = getLevaDocUseCaseFactory(projectFixture).loadProject(projectFixture).build(wiremockURL)
        LevaDocDataFixture fixture = new LevaDocDataFixture(tempFolder.getRoot())
        Map repo = fixture.getInputParamsModule(projectFixture, useCase)
        Map tests = repo.data.tests
        return useCase."create${projectFixture.docType}"(null, tests)
    }

    private String executeLeVADocumentUseCaseMethodWithComponent(ProjectFixture projectFixture, String wiremockURL) {
        LeVADocumentUseCase useCase = getLevaDocUseCaseFactory(projectFixture).loadProject(projectFixture).build(wiremockURL)
        LevaDocDataFixture fixture = new LevaDocDataFixture(tempFolder.getRoot())
        Map repo = fixture.getInputParamsModule(projectFixture, useCase)
        Map tests = repo.data.tests
        repo.data.remove('tests')
        return useCase."create${projectFixture.docType}"(repo, tests)
    }

    private Closure defaultBodyParams() {
        return {
            keyLike "build", {
                targetEnvironment string("dev")
                targetEnvironmentToken string("D")
                version string("WIP")
                configItem string("BI-IT-DEVSTACK")
                changeDescription string("changeDescription")
                changeId string("changeId")
                rePromote bool(false)
                releaseStatusJiraIssueKey string("ORDGP-123")
                runDisplayUrl url("https://jenkins-sample/blabla")
                releaseParamVersion string("1.0")
                buildId string("2022-01-22_23-59-59")
                buildURL url("https//jenkins-sample")
                jobName string("ordgp-cd/ordgp-cd-release-master")
                testResultsURLs {
                    keyLike 'unit', url("https//nexus-sample")
                }
            }
            keyLike "git", {
                commit string("1e84b5100e09d9b6c5ea1b6c2ccee8957391beec")
                repoURL url("https://bitbucket-dev.biscrum.com/scm/ordgp/ordgp-releasemanager.git")
                releaseManagerBranch string("refs/heads/master")
                releaseManagerRepo string("ordgp-releasemanager")
                baseTag string("ods-generated-v3.0-3.0-0b11-D")
                targetTag string("ods-generated-v3.0-3.0-0b11-D")
                author string("s2o")
                message string("Swingin' The Bottle")
                time string("2021-04-20T14:58:31.042152")
                // commitTime timestamp(FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm.ssZZXX").pattern, "2021-04-20T14:58:31.042152")
            }
            keyLike "openshift", {
                targetApiUrl url("https://openshift-sample")
            }
        }
    }

    Closure defaultBodyParamsWithTests() {
        return {
            keyLike "tests", {
                keyLike "unit", {
                    testReportFiles eachLike() {
                        parent string("/tmp/junit12071942610173190818/workspace/xunit/thefirst/unit/build/test-results/test")
                        name string("TEST-com.boehringer.frml24113.thefirst.ThefirstApplicationTests.xml")
                        path string("/tmp/junit12071942610173190818/workspace/xunit/thefirst/unit/build/test-results/test/TEST-com.boehringer.frml24113.thefirst.ThefirstApplicationTests.xml")
                        absolutePath string("/tmp/junit12071942610173190818/workspace/xunit/thefirst/unit/build/test-results/test/TEST-com.boehringer.frml24113.thefirst.ThefirstApplicationTests.xml")
                        totalSpace numeric(53660876800)
                        hidden bool(false)
                        usableSpace numeric(12417183744)
                        canonicalPath string("/tmp/junit12071942610173190818/workspace/xunit/thefirst/unit/build/test-results/test/TEST-com.boehringer.frml24113.thefirst.ThefirstApplicationTests.xml")
                        invalid bool(false)
                        freeSpace numeric(12417183744)
                        file bool(true)
                        freeSpace numeric(12417183744)
                        file bool(true)
                        absolute bool(true)
                        prefixLength numeric(1)
                        directory bool(false)
                    }
                    keyLike "testResults", {
                        testsuites eachLike() {
                            name string("com.boehringer.frml24113.thefirst.ThefirstApplicationTests")
                            hostname string("pod-78fd40da-2e86-47bd-b56f-0dabb3231971-mw77k-tv93m")
                            timestamp string("2021-12-07T12:07:56")
                            systemErr string("")
                            testcases eachLike() {
                                name string("ORDGP163_workingunittest()")
                                timestamp string("2021-12-07T12:07:56")
                                systemErr string("")
                                systemOut string("")
                                skipped bool(false)
                                time string("0.612")
                                classname string("com.boehringer.frml24113.thefirst.ThefirstApplicationTests")
                            }
                            tests string("2")
                            systemOut string("12:07:42.161 [Test worker] DEBUG org.springframework.test.context.BootstrapUtils")
                            skipped string("0")
                            errors string("0")
                            'properties' eachLike([])
                            failures string("0")
                            time string("0.677")
                        }
                    }
                }
            }
        } << defaultBodyParams()
    }

    Closure defaultBodyParamsWithComponent(component) {
        return {
            keyLike "repo", {
                id string("theFirst")
                type string("ods")
                keyLike "data", {
                    keyLike "openshift", {
                        keyLike "builds", {
                            keyLike "${component}", {
                                buildId string("theFirst-3")
                                image string("172.30.1.1:5000/ordgp-cd/teFirst@sha256:f6bc9aaed8a842a8e0a4f7e69b044a12c69e057333cd81906c08fd94be044ac4")
                            }
                        }
                        keyLike "deployments", {
                            keyLike "${component}", {
                                podName string("theFirst-3")
                                podNamespace string("foi2004-dev")
                                podMetaDataCreationTimestamp string("2021-11-21T22:31:04Z")
                                deploymentId string("theFirst-3")
                                podNode string("localhost")
                                podIp string("172.17.0.39")
                                podStatus string("Running")
                                podStartupTimeStamp string("2021-11-21T22:31:04Z")
                                podIp string("172.17.0.39")
                                keyLike "containers", {
                                    "${component}" string("172.30.1.1:5000/ordgp-cd/therFirst@sha256:f6bc9aaed8a842a8e0a4f7e69b044a12c69e057333cd81906c08fd94be044ac4")
                                }
                            }
                        }
                        sonarqubeScanStashPath string("scrr-report-theFirst-1")
                        'SCRR' string("SCRR-ordgp-theFirst.docx")
                        'SCRR-MD' string("SCRR-ordgp-theFirst.md")
                        testResultsFolder string("build/test-results/test")
                        testResults string("1")
                        xunitTestResultsStashPath string("test-reports-junit-xml-theFirst-1")
                        'CREATED_BY_BUILD' string("WIP/1")
                    }
                    keyLike "documents", {}
                    keyLike "git", {
                        branch string("master")
                        commit string("")
                        previousCommit nullValue()
                        previousSucessfulCommit nullValue()
                        url url("http://bitbucket.odsbox.lan:7990/scm/ordgp/ordgp-theFirst.git")
                        baseTag string("")
                        targetTag string("")
                    }
                }
                url url("http://bitbucket.odsbox.lan:7990/scm/ordgp/ordgp-theFirst.git")
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
        } << defaultBodyParamsWithTests()
    }

    Closure defaultDocGenerationResponse(String urlReturnFile) {
        return {
            nexusURL eachLike(){ url("http://lalala", urlReturnFile) }
        }
    }

    Map projectData(docType) {
        return [project: "ORDGP", buildNumber: "666", version: "WIP", docType: docType]
    }

    private LevaDocUseCaseFactory getLevaDocUseCaseFactory(ProjectFixture projectFixture) {
        levaDocWiremock = new LevaDocWiremock()
        levaDocWiremock.setUpWireMock(projectFixture, tempFolder.root)

        // Mocks generation (spock don't let you add this outside a Spec)
        JenkinsService jenkins = Mock(JenkinsService)
        jenkins.unstashFilesIntoPath(_, _, _) >> true
        jenkins.getCurrentBuildLogInputStream() >> new ByteArrayInputStream()
        OpenShiftService openShiftService = Mock(OpenShiftService)
        GitService gitService = Mock(GitService)
        BitbucketTraceabilityUseCase bbT = Spy(new BitbucketTraceabilityUseCase(null, null, null))
        bbT.generateSourceCodeReviewFile() >> new FixtureHelper()
            .getResource(BitbucketTraceabilityUseCaseSpec.EXPECTED_BITBUCKET_CSV).getAbsolutePath()

        return new LevaDocUseCaseFactory(
            levaDocWiremock,
            gitService,
            tempFolder,
            jenkins,
            openShiftService,
            bbT)
    }

}
