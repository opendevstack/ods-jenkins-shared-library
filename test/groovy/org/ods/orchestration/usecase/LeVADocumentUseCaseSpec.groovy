package org.ods.orchestration.usecase

import au.com.dius.pact.consumer.PactVerificationResult
import au.com.dius.pact.consumer.dsl.DslPart
import au.com.dius.pact.consumer.dsl.PactDslJsonBody
import au.com.dius.pact.consumer.groovy.PactBuilder
import groovy.util.logging.Slf4j
import groovyx.net.http.RESTClient
import spock.lang.Specification
import spock.lang.Unroll

/**
 * BUILD_ID: The current build id, such as "2005-08-22_23-59-59" (YYYY-MM-DD_hh-mm-ss)
 * BUILD_NUMBER: The current build number, such as `153`
 */
@Slf4j
class LeVADocumentUseCaseSpec extends Specification {

    @Unroll
    def "create #docType with default params"() {
        given:
        PactBuilder docGenLevaDoc = contractDefinition()
        createInteraction(docGenLevaDoc, docType)
        docGenLevaDoc {
            withBody([prettyPrint:true], defaultBodyParams())
        }
        createResponse(docGenLevaDoc)

        when:
        PactVerificationResult result = executeTest(docGenLevaDoc, docType)

        then:
        result instanceof PactVerificationResult.Ok

        where:
        docType << ["CSD", "DIL", "DTP", "RA", "CFTP", "IVP", "SSDS", "TCP", "TIP", "TRC"]
    }

    private Object executeTest(PactBuilder docGenLevaDoc, docType) {
        docGenLevaDoc.runTest {  context ->
            Object response = callLeVADocumentUseCaseMethod(docType, context.url)
            assert response.status == 200
            assert response.contentType == 'application/json'
            assert response.data == [nexusURL: "http://lalala"]
        }
    }

    private Object createResponse(PactBuilder docGenLevaDoc) {
        docGenLevaDoc {
            willRespondWith(
                status: 200,
                headers: ['Content-Type': 'application/json'],
                body: [nexusURL: "http://lalala"]
            )
        }
    }

    private Object createInteraction(PactBuilder docGenLevaDoc, docType) {
        docGenLevaDoc {
            given('a docGen state')
            uponReceiving("createDocument ${docType}")
            withAttributes(method: 'post', path: "/levaDoc/projectId/build/${docType}")
        }
    }

    private Object contractDefinition() {
        new PactBuilder().build {
            serviceConsumer "buildDocument.defaultParams"
            hasPactWith "createDoc.defaultParams"
        }
    }

    private Object callLeVADocumentUseCaseMethod(String docType, wiremockURL) {
        // Replace this code to a call LeVADocumentUseCase....
        // We need to generate the data using sharedLib functions not as in this example
        // leVADocumentService."create${docType}"(data)
        def client = new RESTClient(wiremockURL)
        def response = client.post(
            path: "/levaDoc/projectId/build/${docType}",
            body: buildFixtureData(),
            requestContentType: 'application/json'
        )
        return response
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
            releaseStatusJiraIssueKey: 9999,
            runDisplayUrl : "",
            releaseParamVersion : "3.0",
            buildId : "2005-08-22_23-59-59",
            buildURL : "https://jenkins-sample",
            jobName : "ofi2004-cd/ofi2004-cd-release-master"
        ]
    }

    private Map<String, String> buildGitData() {
        return  [
            commit: "1e84b5100e09d9b6c5ea1b6c2ccee8957391beec",
            repoURL: "https://bitbucket/scm/ofi2004/ofi2004-release.git", //  new GitService().getOriginUrl()
            baseTag: "ods-generated-v3.0-3.0-0b11-D",
            targetTag: "ods-generated-v3.0-3.0-0b11-D",
            author: "s2o",
            message: "Swingin' The Bottle",
            commitTime: "2021-04-20T14:58:31.042152",
        ]
    }

    private Closure defaultBodyParams(){
        Closure bodyParams = {
            keyLike "build", {
                targetEnvironment string("dev")
                targetEnvironmentToken string("D")
                version string("WIP")
                configItem string("BI-IT-DEVSTACK")
                changeDescription string("changeDescription")
                changeId string("changeId")
                rePromote bool(false)
                releaseStatusJiraIssueKey numeric(1)
                runDisplayUrl string("changeId")
                releaseParamVersion string("3.0")
                buildId string("2005-08-22_23-59-59")
                buildURL url("https//jenkins-sample")
                jobName string("ofi2004-cd/ofi2004-cd-release-master")
            }
            keyLike "git", {
                commit string("1e84b5100e09d9b6c5ea1b6c2ccee8957391beec")
                repoURL url("https://bitbucket/scm/ofi2004/ofi2004-release.git")
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
        return bodyParams
    }
}
