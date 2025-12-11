package org.ods.services.documents.vault.client

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper

import java.nio.charset.StandardCharsets

/**
 * Sample usage:
 *   def client = new VaultAuthClient()
 *   def sessionId = client.login()
 *   println "Session ID: $sessionId"
 */
class VaultAuthClient {

    // Constants
    private static final String AUTH_URL = 'https://bisbx-vault-quality-development-demands.veevavault.com/api/v25.2/auth'
    private static final String HEADER_CONTENT_TYPE = 'application/x-www-form-urlencoded'
    private static final String HEADER_ACCEPT = 'application/json'
    private static final String HEADER_REFERENCE_ID = 'bi-vqd-integration-edpslc-dev-POC-client'
    private static final String FORM_USERNAME = 'integration.vqd.edpslc-dev@bisbx.com'
    private static final String FORM_PASSWORD = '1aaEipZkkRntf!Ltyp8m8KjrsWzFP1' // Replace with secure handling

    /**
     * Performs login and returns the sessionId from the response.
     * @return sessionId as String
     */
    @NonCPS
    String login() {
        def url = new URL(AUTH_URL)
        def connection = (HttpURLConnection) url.openConnection()
        connection.setRequestMethod('POST')
        connection.setDoOutput(true)

        // Set headers
        connection.setRequestProperty('Content-Type', HEADER_CONTENT_TYPE)
        connection.setRequestProperty('Accept', HEADER_ACCEPT)
        connection.setRequestProperty('X-VaultAPI-ReferenceID', HEADER_REFERENCE_ID)

        // Form data
        def formParams = "username=${URLEncoder.encode(FORM_USERNAME, 'UTF-8')}&password=${URLEncoder.encode(FORM_PASSWORD, 'UTF-8')}"
        def outputStream = connection.getOutputStream()
        outputStream.write(formParams.getBytes(StandardCharsets.UTF_8))
        outputStream.flush()
        outputStream.close()

        // Read response
        def responseStream = connection.getInputStream()
        def responseText = responseStream.getText()
        def json = new JsonSlurper().parseText(responseText)

        if (json.responseStatus != 'SUCCESS') {
            throw new RuntimeException("Vault auth failed: ${json}")
        }

        // Extract sessionId
        return json.sessionId
    }
}

