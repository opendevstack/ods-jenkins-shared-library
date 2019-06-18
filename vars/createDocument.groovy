import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def call(data = null, reportType = 'InstallationReport', reportVersion = '1.0', importToItems = false) {
    def host = "${env.DOC_GEN_HOST}" ?: 'localhost'
    def port = "${env.DOC_GEN_PORT}" ?: '8082'

    def requestData = [:]
    requestData.metadata = [:]
    requestData.metadata.type = reportType
    requestData.metadata.version = reportVersion
    requestData.data = data
    requestData.settings = [:]
    requestData.settings.importToItems = false
    def payload = JsonOutput.toJson(requestData)
    println payload
    def docGenSvcUrl = 'http://localhost:8082/document'
    def post = new URL(docGenSvcUrl).openConnection()
    post.setRequestMethod("POST")
    post.setDoOutput(true)
    post.setRequestProperty("Content-Type", "application/json")
    post.getOutputStream().write(payload.getBytes("UTF-8"))
    def postRC = post.getResponseCode()
    if (postRC == 200) {
        JsonSlurper slurper = new JsonSlurper()
        def response = slurper.parse(post.inputStream)
        println JsonOutput.toJson(response)
        return Base64.decoder.decode(response.data)
    } else {
        throw new RuntimeException("Cound not create document. Service returned status ${postRC}")
    }
}