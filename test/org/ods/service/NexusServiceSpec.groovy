package org.ods.service

import org.apache.http.client.utils.URIBuilder

import spock.lang.*

import static com.github.tomakehurst.wiremock.client.WireMock.*

import util.*

class NexusServiceSpec extends SpecHelper {

    def "store artifact"() {
        given:
        def requestParams = [
            baseURL:     new URIBuilder("http://localhost:9090").build(),
            username:    "username",
            password:    "password",
            repository:  "myRepository",
            directory:   "myDirectory",
            name:        "myName",
            artifact:    [0] as byte[],
            contentType: "application/octet-stream"
        ]

        def service = new NexusService(
            requestParams.baseURL.toString(),
            requestParams.username,
            requestParams.password
        )

        when:
        startWireMockServer(requestParams.baseURL).stubFor(
            post(urlPathMatching("/service/rest/v1/components"))
                .withBasicAuth(
                    requestParams.username,
                    requestParams.password
                )
                .withMultipartRequestBody(aMultipart()
                    .withName("raw.directory")
                    .withBody(equalTo(requestParams.directory))
                )
                .withMultipartRequestBody(aMultipart()
                    .withName("raw.asset1")
                    .withBody(binaryEqualTo(requestParams.artifact))
                )
                .withMultipartRequestBody(aMultipart()
                    .withName("raw.asset1.filename")
                    .withBody(equalTo(requestParams.name))
                )
                .willReturn(aResponse()
                    .withStatus(204)
                )
        )

        then:
        service.storeArtifact(
            requestParams.repository,
            requestParams.directory,
            requestParams.name,
            requestParams.artifact,
            requestParams.contentType
        ) == new URIBuilder("${requestParams.baseURL}/repository/${requestParams.repository}/${requestParams.directory}/${requestParams.name}").build()
    }
}
