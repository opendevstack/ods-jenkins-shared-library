package org.ods.service

@Grab(group="com.konghq", module="unirest-java", version="2.3.08", classifier="standalone")

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

import java.net.URI

import kong.unirest.Unirest

import org.apache.http.client.utils.URIBuilder

class DocGenService {

    URI baseURL

    DocGenService(String baseURL) {
        if (!baseURL) {
            throw new IllegalArgumentException("Error: unable to connect to DocGen. 'baseURL' is undefined")
        }

        try {
            this.baseURL = new URIBuilder(baseURL).build()
        } catch (e) {
            throw new IllegalArgumentException("Error: unable to connect to DocGen. '${baseURL}' is not a valid URI")
        }
    }

    private static def decodeBase64(def base64String) {
        return Base64.decoder.decode(base64String)
    }

    def Map createDocument(String type, String version, Map data) {
        def response = Unirest.post("${this.baseURL}/document")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson([
                metadata: [
                    type: type,
                    version: version
                ],
                data: data
            ]))
            .asString()

        response.ifFailure {
            throw new RuntimeException("Error: unable to create document. DocGen responded with code: ${response.getStatus()} and message: ${response.getBody()}")
        }

        def result = new JsonSlurperClassic().parseText(response.getBody())
        result.data = decodeBase64(result.data)
        return result
    }
}
