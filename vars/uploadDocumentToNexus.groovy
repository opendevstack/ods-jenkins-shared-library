import org.apache.http.HttpHeaders
import org.apache.http.client.methods.HttpPost
@Grapes([
        @Grab(group = 'org.apache.httpcomponents', module = 'httpmime', version = '4.5.9'),
        @Grab('org.apache.httpcomponents:httpclient:4.5.9')])
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClientBuilder

import java.nio.charset.StandardCharsets

def call(byte[] documentData, groupId = 'org.opendevstack.rm', arifactId = 'docgen', version = '0.0.1') {

    def repository = 'maven-releases'

    def host = "${env.NEXUS_HOST}" ?: 'nexus.pltf'
    def port = "${env.NEXUS_PORT}" ?: '8082'
    def nexusURI = new URIBuilder()
            .setScheme("http")
            .setHost(host)
            .setPort(Integer.parseInt(port))
            .setPath("/service/rest/v1/components")
            .addParameter('repository', repository)
            .build()
    String result = null;
    withCredentials([usernamePassword(credentialsId: 'nexus', passwordVariable: 'NEXUS_PWD', usernameVariable: 'NEXUS_USER')]) {
        def auth = "${env.NEXUS_USER}:${env.NEXUS_PWD}"
        def encodedAuth = Base64.encoder.encodeToString(auth.getBytes(StandardCharsets.UTF_8))
        uploadDocument(nexusURI, groupId, arifactId, version, documentData, encodedAuth)
        def path = "/repository/${repository}/${groupId.replace('.', '/')}/${arifactId}/${version}/${arifactId}-${version}-reports.pdf"
        result = nexusURI.resolve(path).toString()
    }
    return result

}

static def uploadDocument(URI nexusURI, groupId, arifactId, version, byte[] documentData, encodedAuth) {
    HttpPost post = new HttpPost(nexusURI)
    MultipartEntityBuilder builder = MultipartEntityBuilder.create()
    builder.addTextBody('maven2.groupId', groupId)
    builder.addTextBody('maven2.artifactId', arifactId)
    builder.addTextBody('maven2.version', version)
    builder.addTextBody('maven2.packaging', 'pdf')
    builder.addBinaryBody('maven2.asset1', documentData, ContentType.create("application/pdf"), 'report.pdf')
    builder.addTextBody('maven2.asset1.classifier', 'reports')
    builder.addTextBody('maven2.asset1.extension', 'pdf')
    post.setEntity(builder.build())
    String authHeader = "Basic ${encodedAuth}"
    post.setHeader(HttpHeaders.AUTHORIZATION, authHeader)
    def httpClient = HttpClientBuilder.create().build()
    def response = httpClient.execute(post)
    if (response.getStatusLine().getStatusCode() != 204) {
        throw new RuntimeException("Cound not upload document. Nexus returned status ${response.getStatusLine().getStatusCode()}")
    }
}