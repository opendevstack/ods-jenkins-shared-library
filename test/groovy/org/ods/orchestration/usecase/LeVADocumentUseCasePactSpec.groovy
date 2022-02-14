package org.ods.orchestration.usecase

import au.com.dius.pact.consumer.groovy.PactBuilder
import groovy.util.logging.Slf4j
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.ods.core.test.usecase.LevaDocUseCaseFactory
import org.ods.core.test.usecase.levadoc.fixture.DocTypeProjectFixture
import org.ods.core.test.usecase.levadoc.fixture.ProjectFixture
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
                withBody([prettyPrint:true], defaultBodyParams())
                willRespondWith(status: 200, headers: ['Content-Type': 'application/json'])
                withBody {
                    nexusURL  url("http://lalala", urlReturnFile)
                }
                runTestAndVerify {  context ->
                    String response = executeLeVADocumentUseCaseMethod(projectFixture, context.url)
                    assert response == "http://lalala/${urlReturnFile}"
                }
            }

        where:
        projectFixture << new DocTypeProjectFixture().getProjects()
    }

    private String executeLeVADocumentUseCaseMethod(ProjectFixture projectFixture, String wiremockURL) {
        LeVADocumentUseCase useCase = getLevaDocUseCaseFactory(projectFixture).loadProject(projectFixture).build(wiremockURL)
        return useCase."create${projectFixture.docType}"()
    }

    private Closure defaultBodyParams(){
        return {
            keyLike "build", {
                targetEnvironment string("dev")
                targetEnvironmentToken string("D")
                version string("WIP")
                configItem string("BI-IT-DEVSTACK")
                changeDescription string("changeDescription")
                changeId string("changeId")
                rePromote bool(false)
                releaseStatusJiraIssueKey string("FRML24113-230")
                runDisplayUrl url("https://jenkins-sample/blabla")
                releaseParamVersion string("3.0")
                buildId string("2022-01-22_23-59-59")
                buildURL url("https//jenkins-sample")
                jobName string("ofi2004-cd/ofi2004-cd-release-master")
            }
            keyLike "git", {
                commit string("1e84b5100e09d9b6c5ea1b6c2ccee8957391beec")
                repoURL url("https://bitbucket/scm/ofi2004/ofi2004-release.git")
                releaseManagerBranch string("refs/tags/CHG0066328")
                baseTag string("ods-generated-v3.0-3.0-0b11-D")
                targetTag string("ods-generated-v3.0-3.0-0b11-D")
                author string("s2o")
                message string("Swingin' The Bottle")
                commitTime string("2021-04-20T14:58:31.042152")
                // commitTime timestamp(FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm.ssZZXX").pattern, "2021-04-20T14:58:31.042152")
            }
            keyLike "openshift", {
                targetApiUrl url("https://openshift-sample")
            }
        }
    }

    Map projectData(docType){
        return [project:"FRML24113", buildNumber:"666", version: "WIP", docType: docType]
    }

    private LevaDocUseCaseFactory getLevaDocUseCaseFactory(ProjectFixture projectFixture) {
        levaDocWiremock = new LevaDocWiremock()
        levaDocWiremock.setUpWireMock(projectFixture, tempFolder.root)

        // Mocks generation (spock don't let you add this outside a Spec)
        JenkinsService jenkins = Mock(JenkinsService)
        jenkins.unstashFilesIntoPath(_, _, _) >> true
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
