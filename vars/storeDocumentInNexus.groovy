@Grab("org.apache.httpcomponents:httpclient:4.5.9")
@Grab("org.apache.httpcomponents:httpmime:4.5.9")

import java.nio.charset.StandardCharsets

import org.apache.http.HttpHeaders
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils

// Store a document in Nexus and return a URI to the document
def call(String repository, String directory, String id, String version, byte[] document) {
    if (!env.NEXUS_SCHEME) {
        error "Error: unable to connect to Nexus: env.NEXUS_SCHEME is undefined"
    }

    if (!env.NEXUS_HOST) {
        error "Error: unable to connect to Nexus: env.NEXUS_HOST is undefined"
    }

    if (!env.NEXUS_PORT) {
        error "Error: unable to connect to Nexus: env.NEXUS_PORT is undefined"
    }

    def uri = new URIBuilder()
        .setScheme(env.NEXUS_SCHEME)
        .setHost(env.NEXUS_HOST)
        .setPort(Integer.parseInt(env.NEXUS_PORT))
        .setPath("/service/rest/v1/components")
        .addParameter("repository", repository)
        .build()

    URI result = null
    withCredentials([ usernamePassword(credentialsId: "nexus", passwordVariable: "NEXUS_PASSWORD", usernameVariable: "NEXUS_USERNAME") ]) {
        def builder = MultipartEntityBuilder.create()
            .addTextBody("raw.directory", directory)
            .addTextBody("raw.asset1", "${id}-${version}.pdf")
            .addTextBody("raw.asset1.filename", "${id}-${version}.pdf")
            .addBinaryBody("raw.asset1", document, ContentType.create("application/pdf"), "${id}-${version}.pdf")

        def post = new HttpPost(uri)
        post.setEntity(builder.build())

        def authenticationBase64 = Base64.encoder.encodeToString("${env.NEXUS_USERNAME}:${env.NEXUS_PASSWORD}".getBytes(StandardCharsets.UTF_8))
        post.setHeader(HttpHeaders.AUTHORIZATION, "Basic ${authenticationBase64}")

        def httpClient = HttpClientBuilder.create()
            .build()

        def response = httpClient.execute(post)
        def responseStatusCode = response.getStatusLine().getStatusCode()
        if (responseStatusCode != 204) {
            def responseBody = EntityUtils.toString(response.getEntity())
            error "Error: unable to store document. Nexus responded with code ${responseStatusCode} and message '${responseBody}'"
        }

        result = uri.resolve("/repository/${repository}/${directory}/${id}-${version}.pdf")
    }

    return result.toString()
}

return this
