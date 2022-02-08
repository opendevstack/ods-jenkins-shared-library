package org.ods.orchestration.usecase

import au.com.dius.pact.consumer.PactVerificationResult
import au.com.dius.pact.consumer.groovy.PactBuilder
import groovy.util.logging.Slf4j
import groovyx.net.http.RESTClient
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
/**
 * BUILD_ID: The current build id, such as "2005-08-22_23-59-59" (YYYY-MM-DD_hh-mm-ss)
 * BUILD_NUMBER: The current build number, such as `153`
 *
 * Sample to develop a new contract with the help of IntelliJ:
 *   /* new PactBuilder().with{
 withBody {
 status url("OK")
 }
 }
 */
@Slf4j
class LeVADocumentUseCaseSpec extends Specification {

    def "create with default params"() {
        given:
        Map params = projectData(docType)
        String generatedFile = "${docType}-FRML24113-WIP-2022-01-22_23-59-59.zip"
        String urlReturnFile = "repository/leva-documentation/${params.project.toLowerCase()}-${params.version}/${generatedFile}"

        expect:
        new PactBuilder()
            .with {
                serviceConsumer "buildDocument.defaultParams"
                hasPactWith "createDoc.defaultParams"
                given("project with data:", params)
                uponReceiving("a request for /buildDocument ${docType}")
                withAttributes(method: 'post', path: "/levaDoc/${params.project}/${params.buildNumber}/${docType}")
                withBody([prettyPrint:true], defaultBodyParams())
                willRespondWith(status: 200, headers: ['Content-Type': 'application/json'])
                withBody {
                    nexusURL  url("http://lalala", urlReturnFile)
                }

                runTestAndVerify {  context ->
                    Object response = callLeVADocumentUseCaseMethod(docType, context.url)
                    assert response.status == 200
                    assert response.contentType == 'application/json'
                    assert response.data == [nexusURL: "http://lalala/${urlReturnFile}"]
                }
            }

        where:
        docType << ["CSD", "DIL", "DTP", "RA", "CFTP", "IVP", "SSDS", "TCP", "TIP", "TRC"]
    }

    private Object contractDefinition() {
        new PactBuilder().build {
            serviceConsumer "buildDocument.defaultParams"
            hasPactWith "createDoc.defaultParams"
        }
    }

    private  void createInteractions(PactBuilder docGenLevaDoc, List<String> docTypes) {
        Map params = projectData()
        for (docType in docTypes){
            createInteraction(docGenLevaDoc, params, docType)
        }
    }

    private void createInteraction(PactBuilder docGenLevaDoc, Map params, String docType) {
        String urlRetunFile = "repository/leva-documentation/frml24113-WIP/${docType}-FRML24113-WIP-2022-01-22_23-59-59.zip"
        docGenLevaDoc {
            params.docType = docType
            given("project with data:", params)
            uponReceiving("a request for /buildDocument for document ${docType}")
            withAttributes(method: 'post', path: "/levaDoc/${params.project}/${params.buildNumber}/${docType}")
            withBody([prettyPrint: true], defaultBodyParams())
            willRespondWith(status: 200, headers: ['Content-Type': 'application/json'])
            withBody {
                nexusURL url("http://lalala", urlRetunFile)
            }
        }
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
                runDisplayUrl string("changeId")
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

    private def executeTest(PactBuilder docGenLevaDoc, List<String> docTypes) {
        return docGenLevaDoc.runTest {  wiremockServer ->
            for (docType in docTypes){
                String urlReturnPath = "repository/leva-documentation/frml24113-WIP/${docType}-FRML24113-WIP-2022-01-22_23-59-59.zip"
                Object response = callLeVADocumentUseCaseMethod(docType, wiremockServer.url)
                assert response.status == 200
                assert response.contentType == 'application/json'
                assert response.data == [nexusURL: "http://lalala/${urlReturnPath}"]
            }
        }
    }

    private Object callLeVADocumentUseCaseMethod(String docType, wiremockURL) {
        // Replace this code to a call LeVADocumentUseCase....
        // We need to generate the data using sharedLib functions not as in this example
        // leVADocumentUseCase"create${docType}"(data)
        def client = new RESTClient(wiremockURL)
        def data = projectData()
        def response = client.post(
            path: "/levaDoc/${data.project}/${data.buildNumber}/${docType}",
            body: buildFixtureData(),
            requestContentType: 'application/json'
        )
        return response
    }

    Map projectData(docType){
        return [project:"FRML24113", buildNumber:"666", version: "WIP", docType: docType]
    }

    Map buildFixtureData(){
        Map data = [:]
        data.build = buildParams()
        data.git =  buildGitData()
        data.openshift = [targetApiUrl:"https://openshift-sample"]
        return data
    }

    private Map buildParams() {
        return  [
            targetEnvironment: "dev",
            targetEnvironmentToken: "D",
            version: "1",
            configItem: "BI-IT-DEVSTACK",
            changeDescription: "changeDescription",
            changeId: "changeId",
            rePromote: false,
            releaseStatusJiraIssueKey: "FRML24113-230",
            runDisplayUrl : "",
            releaseParamVersion : "3.0",
            buildId : "2022-01-22_23-59-59",
            buildURL : "https://jenkins-sample",
            jobName : "ofi2004-cd/ofi2004-cd-release-master"
        ]
    }

    private Map<String, String> buildGitData() {
        return  [
            commit: "1e84b5100e09d9b6c5ea1b6c2ccee8957391beec",
            repoURL: "https://bitbucket/scm/ofi2004/ofi2004-release.git", //  new GitService().getOriginUrl()
            releaseManagerBranch: "refs/tags/CHG0066328",
            baseTag: "ods-generated-v3.0-3.0-0b11-D",
            targetTag: "ods-generated-v3.0-3.0-0b11-D",
            author: "s2o",
            message: "Swingin' The Bottle",
            commitTime: "2021-04-20T14:58:31.042152",
        ]
    }

}
