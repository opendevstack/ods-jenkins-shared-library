package org.ods.service

@Grab(group="com.konghq", module="unirest-java", version="2.3.08", classifier="standalone")

import java.net.URI

import kong.unirest.Unirest

import org.apache.http.client.utils.URIBuilder

class NexusService implements Serializable {

    String scheme
    String host
    int port

    String username
    String password

    private URI getBaseURI() {
        return new URIBuilder()
            .setScheme(this.scheme)
            .setHost(this.host)
            .setPort(this.port)
            .build()
    }

    def URI storeArtifact(String repository, String directory, String name, byte[] artifact, String contentType) {
        def uri = getBaseURI()

        def response = Unirest.post("${uri}/service/rest/v1/components?repository={repository}")
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

        return uri.resolve("/repository/${repository}/${directory}/${name}")
    }
}
