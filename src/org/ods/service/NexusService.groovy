package org.ods.service

@Grab(group="com.konghq", module="unirest-java", version="2.3.08", classifier="standalone")

import java.net.URI

import kong.unirest.Unirest

import org.apache.http.client.utils.URIBuilder

class NexusService {

    URI baseURL

    String username
    String password

    NexusService(String baseURL, String username, String password) {
        if (!baseURL) {
            throw new IllegalArgumentException("Error: unable to connect to Nexus. 'baseURL' is undefined")
        }

        if (!username) {
            throw new IllegalArgumentException("Error: unable to connect to Nexus. 'username' is undefined")
        }

        if (!password) {
            throw new IllegalArgumentException("Error: unable to connect to Nexus. 'password' is undefined")
        }

        try {
            this.baseURL = new URIBuilder(baseURL).build()
        } catch (e) {
            throw new IllegalArgumentException("Error: unable to connect to Nexus. '${baseURL}' is not a valid URI")
        }

        this.username = username
        this.password = password
    }

    def URI storeArtifact(String repository, String directory, String name, byte[] artifact, String contentType) {
        def response = Unirest.post("${this.baseURL}/service/rest/v1/components?repository={repository}")
            .routeParam("repository", repository)
            .basicAuth(this.username, this.password)            
            .field("raw.directory", directory)
            .field("raw.asset1", new ByteArrayInputStream(artifact), contentType)
            .field("raw.asset1.filename", name)
            .asString()

        response.ifSuccess {
            if (response.getStatus() != 204) {
                throw new RuntimeException("Error: unable to store artifact. Nexus responded with code: ${response.getStatus()} and message: ${response.getBody()}")
            }
        }

        response.ifFailure {
            throw new RuntimeException("Error: unable to store artifact. Nexus responded with code: ${response.getStatus()} and message: ${response.getBody()}")
        }

        return this.baseURL.resolve("/repository/${repository}/${directory}/${name}")
    }
}
