package org.ods.services.documents.vault.client

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper

class VaultQueryClient {

    @NonCPS
    String queryUserIdByEmail(String email, String token) {
        String urlStr = "https://bisbx-vault-quality-development-demands.veevavault.com/api/v25.2/query"
        String query = "SELECT id, name__v FROM user__sys WHERE federated_id__sys = '${email}'"

        String encodedQuery = URLEncoder.encode(query, "UTF-8")

        URL url = new URL(urlStr)
        HttpURLConnection connection = (HttpURLConnection) url.openConnection()
        connection.setRequestMethod("POST")
        connection.setRequestProperty("Authorization", token)
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("X-VaultAPI-DescribeQuery", "true")
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.setDoOutput(true)

        String postData = "q=${encodedQuery}"
        connection.outputStream.withWriter("UTF-8") { it.write(postData) }

        def responseText = connection.inputStream.text
        def json = new JsonSlurper().parseText(responseText)

        if (json?.responseStatus != "SUCCESS") {
            throw new RuntimeException("Vault API query failed: ${json}")
        }

        def clientId = json?.data?.getAt(0)?.id

        if (!clientId) {
            throw new RuntimeException("No user found with email: ${email}")
        }

        return clientId
    }
}



