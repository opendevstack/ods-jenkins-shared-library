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
        def reviewerIds = [] as List<String>
        for (String email: emails) {
            def reviewerId = vaultQueryClient.queryUserIdByEmail(email, sessionId)
            if (!reviewerId) {
                throw new RuntimeException("The user with email ${email} isn't allowed to sign the documents. Please contact support.")
            }
            reviewerIds << reviewerId
        }
        return workflowClient.triggerWorkflow("Bearer $sessionId", documentIds, reviewerIds)
    }

}
