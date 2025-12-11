package org.ods.services.documents


import org.ods.services.documents.vault.client.VaultAuthClient
import org.ods.services.documents.vault.client.VaultDocumentUploader
import org.ods.services.documents.vault.client.VaultQueryClient
import org.ods.services.documents.vault.client.VaultWorkflowClient
import java.util.stream.Collectors

final String DOCUMENT_PATH = "C:\\Users\\soriaoli\\IdeaProjects\\dev-biscrum\\ods-jenkins-shared-library\\resources\\documents\\himno_de_andalucia.pdf"


// Instantiate clients
def authClient = new VaultAuthClient()
def vaultQueryClient = new VaultQueryClient()
def uploader = new VaultDocumentUploader()
def workflowClient = new VaultWorkflowClient()

def emails = [
    "sergio.soria_olivares.ext@boehringer-ingelheim.com",
    "juan.farre@boehringer-ingelheim.com",
    //"constantin.valeriu_tuguran.ext@boehringer-ingelheim.com",
    ]

try {
    // Step 1: Login
    def sessionId = authClient.login()
    println "✅ Logged in. Session ID: $sessionId"

    // Step 2: Locate the file
    // Load file
    def file = new File(DOCUMENT_PATH)

    if (!file.exists()) {
        throw new FileNotFoundException("File not found at path: ${DOCUMENT_PATH}")
    }


    println "✅ File loaded. file path: $file.absolutePath"

    if (!file.exists()) {
        throw new FileNotFoundException("❌ File not found: ${file.absolutePath}")
    }

    // Step 3: Upload the document
    def documentId = uploader.uploadDocument("Bearer $sessionId", file)
    println "✅ Document uploaded. ID: $documentId"

    // Step 4: Trigger workflow on the uploaded document
    def reviewerIds = emails.stream()
        .map ({ email -> vaultQueryClient.queryUserIdByEmail(email, sessionId) })
        .collect(Collectors.toList())

    println "✅ Reviewer IDs: $reviewerIds"

    def workflowId = workflowClient.triggerWorkflow("Bearer $sessionId", [documentId], reviewerIds)
    println "✅ Workflow triggered. ID: $workflowId"

} catch (Exception e) {
    println "❌ Error: ${e}"
}
