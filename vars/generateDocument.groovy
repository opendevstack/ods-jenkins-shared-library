@Grab('org.apache.httpcomponents:httpclient:4.5.9')

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

import java.nio.file.Paths

import org.apache.http.client.utils.URIBuilder

// Create a validation document from template type, version, and data, and return the document
def call(String type, String version, Map data) {
    if (!env.DOCGEN_SCHEME) {
        error "Error: unable to connect to DocGen: env.DOCGEN_SCHEME is undefined"
    }

    if (!env.DOCGEN_HOST) {
        error "Error: unable to connect to DocGen: env.DOCGEN_HOST is undefined"
    }

    if (!env.DOCGEN_PORT) {
        error "Error: unable to connect to DocGen: env.DOCGEN_PORT is undefined"
    }

    def uri = new URIBuilder()
        .setScheme(env.DOCGEN_SCHEME)
        .setHost(env.DOCGEN_HOST)
        .setPort(Integer.parseInt(env.DOCGEN_PORT))
        .setPath("/document")
        .build()

    def response = httpRequest url: uri.toString(),
        consoleLogResponseBody: true,
        httpMode: 'POST',
        acceptType: 'APPLICATION_JSON',
        contentType: 'APPLICATION_JSON',
        ignoreSslErrors: true,
        requestBody: JsonOutput.toJson([
            data: data,
            metadata: [
                type: type,
                version: version
            ]
        ])

    def documentBase64 = new JsonSlurperClassic().parseText(response.content).data
    def document = Base64.decoder.decode(documentBase64)

    archiveBinaryArtifact(document, Paths.get("documents"), type, "${version}.pdf")

    return document
}
