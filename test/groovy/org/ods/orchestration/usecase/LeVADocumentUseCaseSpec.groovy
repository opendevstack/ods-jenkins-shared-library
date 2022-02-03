package org.ods.orchestration.usecase

import au.com.dius.pact.consumer.groovy.PactBuilder
import groovyx.net.http.RESTClient
import spock.lang.Specification

class LeVADocumentUseCaseSpec extends Specification {

    def "CreateCSD"() {
        expect:
        new PactBuilder()
            .with {
                serviceConsumer "createCSD"
                hasPactWith "LevaDocController"
                port 8080

                uponReceiving('buildDocument CSD')
                withAttributes(method: 'post', path: "/levaDoc/projectId/build/CSD")
                withBody {
                    keyLike "build",  {
                        targetEnvironment string("dev")
                        projectKey string("TEST_PROJECT_ID")
                        targetEnvironment string("dev")
                        targetEnvironmentToken string("D")
                        version string("WIP")
                        configItem string("BI-IT-DEVSTACK")
                        changeDescription string("changeDescription")
                        changeId string("changeId")
                        rePromote asBoolean("false")
                        releaseStatusJiraIssueKey numeric(1)
                        runDisplayUrl  string("changeId")
                        releaseParamVersion "3.0"
                        BUILD_NUMBER "666"
                        BUILD_URL "https//jenkins-sample"
                        JOB_NAME "ofi2004-cd/ofi2004-cd-release-master"
                        BUILD_ID "1"
                    }
                }
                willRespondWith(
                    status: 200,
                    headers: ['Content-Type': 'application/json'],
                )
                withBody {
                    nexusURL string("http://lalala")
                }

                runTestAndVerify {
                    def client = new RESTClient('http://localhost:8080/')
                    def response = client.post(
                        path:  "/levaDoc/projectId/build/CSD",
                        body: [
                            build: [targetEnvironment: "dev"]
                        ],
                        requestContentType: 'application/json'
                    )
                    assert response.status == 200
                    assert response.contentType == 'application/json'
                    assert response.data == ['nexusURL': "http://lalala"]
                }
            }
    }

    def "CreateDTP"() {
    }

    def "CreateDTR"() {
    }

    def "CreateOverallDTR"() {
    }

    def "CreateDIL"() {
    }

    def "CreateCFTP"() {
    }

    def "CreateCFTR"() {
    }

    def "CreateRA"() {
    }

    def "CreateIVP"() {
    }

    def "CreateIVR"() {
    }

    def "CreateTCR"() {
    }

    def "CreateTCP"() {
    }

    def "CreateSSDS"() {
    }

    def "CreateTIP"() {
    }

    def "CreateTIR"() {
    }

    def "CreateOverallTIR"() {
    }

    def "CreateTRC"() {
    }

    private Map buildParams() {
        Map projectFixture = [:]
        projectFixture.project = "project"
        projectFixture.docType = "docType"
        projectFixture.version = "version"
        projectFixture.releaseKey = "releaseKey"
        return buildFixtureData(projectFixture)
    }

    Map buildFixtureData(Map projectFixture){
        Map data = [:]
        data.projectBuild =  "${projectFixture.project}-1"
        data.documentType = projectFixture.docType
        data.jobParams = buildJobParams(projectFixture)
        data.env = getEnvVariables()
        data.git =  buildGitData()
        data.openshift = [targetApiUrl:"https://openshift-sample"]

        return data
    }

    private Map<String, String> buildJobParams(Map projectFixture){
        return  [
            projectKey: projectFixture.project,
            targetEnvironment: "dev",
            targetEnvironmentToken: "D",
            version: "${projectFixture.version}",
            configItem: "BI-IT-DEVSTACK",
            changeDescription: "changeDescription",
            changeId: "changeId",
            rePromote: "false",
            releaseStatusJiraIssueKey: projectFixture.releaseKey
        ]
    }

    private Map<String, String> buildGitData() {
        return  [
            commit: "1e84b5100e09d9b6c5ea1b6c2ccee8957391beec",
            url: "https://bitbucket/scm/ofi2004/ofi2004-release.git", //  new GitService().getOriginUrl()
            baseTag: "ods-generated-v3.0-3.0-0b11-D",
            targetTag: "ods-generated-v3.0-3.0-0b11-D",
            author: "s2o",
            message: "Swingin' The Bottle",
            time: "2021-04-20T14:58:31.042152",
        ]
    }
}
