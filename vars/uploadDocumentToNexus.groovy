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

    def nexusURI = new URIBuilder()
            .setScheme("http")
            .setHost("localhost")
            .setPort(8081)
            .setPath("/service/rest/v1/components")
            .addParameter('repository', repository)
            .build()

//http://localhost:8081/repository/maven-releases/

    def httpClient = HttpClientBuilder.create().build()

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

    def auth = 'admin:admin123'
    def encodedAuth = Base64.encoder.encodeToString(auth.getBytes(StandardCharsets.UTF_8))
    String authHeader = "Basic ${encodedAuth}"
    post.setHeader(HttpHeaders.AUTHORIZATION, authHeader)

    def response = httpClient.execute(post)

    if (response.getStatusLine().getStatusCode() != 204) {
        throw new RuntimeException("Cound not upload document. Nexus returned status ${response.getStatusLine().getStatusCode()}")
    }
    def path = "/repository/${repository}/${groupId.replace('.', '/')}/${arifactId}/${version}/${arifactId}-${version}-reports.pdf"
//http://localhost:8081/repository/maven-releases/org/opendevstack/rm/docgen/1.2.3/docgen-1.2.3-reports.pdf
    return nexusURI.resolve(path).toString()
}