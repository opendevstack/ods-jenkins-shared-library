import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def call(data = null, reportType = 'InstallationReport', reportVersion = '1.0', importToItems = false) {
    def host = "${env.DOC_GEN_HOST}" ?: 'docgen.pltf'
    def port = "${env.DOC_GEN_PORT}" ?: '8181'

    def requestData = [:]
    requestData.metadata = [:]
    requestData.metadata.type = reportType
    requestData.metadata.version = reportVersion
    requestData.data = data
    requestData.settings = [:]
    requestData.settings.importToItems = false
    def payload = JsonOutput.toJson(requestData)
    println payload
    def docGenSvcUrl = "http://${host}:${port}/document"
    def response = httpRequest url: docGenSvcUrl,
            consoleLogResponseBody: true,
            httpMode: 'POST',
            acceptType: 'APPLICATION_JSON',
            contentType: 'APPLICATION_JSON',
            ignoreSslErrors: true,
            requestBody: payload

    JsonSlurper slurper = new JsonSlurper()
    return Base64.decoder.decode(slurper.parseText(response.content).data)
}