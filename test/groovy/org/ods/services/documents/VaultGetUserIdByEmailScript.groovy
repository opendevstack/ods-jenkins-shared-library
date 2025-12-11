package org.ods.services.documents

import org.ods.services.documents.vault.client.VaultAuthClient
import org.ods.services.documents.vault.client.VaultQueryClient

// def email = "christian.unnerstall@boehringer-ingelheim.com"
def email = "sergio.soria_olivares.ext@boehringer-ingelheim.com"

// Instantiate clients
def authClient = new VaultAuthClient()
def vaultQueryClient = new VaultQueryClient()

try {
    // Step 1: Login
    def sessionId = authClient.login()
    println "✅ Logged in. Session ID: $sessionId"

    def userId = vaultQueryClient.queryUserIdByEmail(email, sessionId)

    // Step 2: Get user ID by email
    println "✅ User ID: ${userId}"
} catch (Exception e) {
    println "❌ Error: ${e.message}"
}
