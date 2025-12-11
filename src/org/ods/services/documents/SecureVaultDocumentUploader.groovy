package org.ods.services.documents

import com.cloudbees.groovy.cps.NonCPS
import org.ods.orchestration.util.Project
import org.ods.services.documents.snow.client.SNowCmdbClient
import org.ods.services.documents.vault.client.VaultAuthClient
import org.ods.services.documents.vault.client.VaultDocumentUploader
import org.ods.services.documents.vault.client.VaultQueryClient
import org.ods.services.documents.vault.client.VaultWorkflowClient

import java.util.stream.Collectors

class SecureVaultDocumentUploader {

    // Instantiate clients
    private final SNowCmdbClient sNowCmdbClient
    private final VaultAuthClient authClient = new VaultAuthClient()
    private final VaultQueryClient vaultQueryClient = new VaultQueryClient()
    private final VaultDocumentUploader uploader = new VaultDocumentUploader()
    private final VaultWorkflowClient workflowClient = new VaultWorkflowClient()

    SecureVaultDocumentUploader(Project project) {
        sNowCmdbClient = new SNowCmdbClient(project)
    }

    @NonCPS
    def upload(String filename, List<byte[]> documentBytes) {
        def emails = sNowCmdbClient.getDocumentSigners()
        def sessionId = authClient.login()
        def documentIds = documentBytes.stream()
            .map { bytes -> uploader.uploadDocument("Bearer $sessionId", filename, bytes) }
            .collect(Collectors.toList())
        return triggerWorkflow(sessionId, documentIds, emails)
    }

    @NonCPS
    def upload(String filename, byte[] bytes) {
        def sessionId = authClient.login()
        return uploader.uploadDocument("Bearer $sessionId", filename, bytes)
    }

    @NonCPS
    def triggerWorkflow(List<Integer> documentIds) {
        def emails = sNowCmdbClient.getDocumentSigners()
        def sessionId = authClient.login()
        return triggerWorkflow(sessionId, documentIds, emails)
    }

    @NonCPS
    def triggerWorkflow(String sessionId, List<Integer> documentIds, List<String> emails) {
        def reviewerIds = emails.stream()
            .map ({ email -> vaultQueryClient.queryUserIdByEmail(email, sessionId) })
            .collect(Collectors.toList())
        return workflowClient.triggerWorkflow("Bearer $sessionId", documentIds, reviewerIds)
    }

}
