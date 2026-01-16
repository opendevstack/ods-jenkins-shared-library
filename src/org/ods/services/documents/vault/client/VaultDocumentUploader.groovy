package org.ods.services.documents.vault.client

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper

/**
 * Sample usage:
 *   def uploader = new VaultDocumentUploader()
 *   def file = new File('/path/to/document.docx')
 *   try {
 *     def documentId = uploader.uploadDocument("Bearer YOUR_SESSION_ID", file)
 *     println "Document uploaded successfully. ID: $documentId"
 *   } catch (Exception e) {
 *     println "Upload failed: ${e.message}"
 *   }

 */
class VaultDocumentUploader {

    // Constants
    private static final String UPLOAD_URL = 'https://bisbx-vault-quality-development-demands.veevavault.com/api/v25.2/objects/documents'
    private static final String HEADER_ACCEPT = 'application/json'
    private static final String HEADER_REFERENCE_ID = 'bi-vqd-integration-edpslc-dev-POC-client'

    // Form field constants
    private static final String NAME = 'Test EDP SLC Documentation to VQD (Document 1)'
    private static final String TYPE = 'Validation and Qualification'
    private static final String SUBTYPE = 'Computer System'
    private static final String CLASSIFICATION = 'e-Document'
    private static final String LIFECYCLE = 'Draft to Effective Lifecycle'
    private static final String TITLE = 'Test EDP SLC Documentation to VQD (Document 1)'
    private static final String DOCUMENT_UNIT = 'V4X000000001006'
    private static final String RETENTION_CATEGORY = 'V5X000000001056'
    private static final String OWNING_DIVISION = 'V4W000000001012'
    private static final String PROCESS = 'V4Z000000001083'
    private static final String NATURE_OF_DOCUMENT = 'N/A-Covered By Type/Subtype/Classification'
    private static final String DOCUMENT_SCOPE = 'Global'
    private static final String MANUFACTURING_INSTRUCTION_TYPE = 'gmp__c'

    /**
     * Uploads a document to Vault and returns the document ID.
     * Throws an exception if responseStatus is not SUCCESS.
     * @param sessionId Authorization token
     * @param file File to upload
     * @return document ID
     */
    @NonCPS
    Integer uploadDocument(String sessionId, String filename, byte[] bytes) {
        def boundary = "----VaultBoundary${System.currentTimeMillis()}"
        def url = new URL(UPLOAD_URL)
        def connection = (HttpURLConnection) url.openConnection()
        connection.setRequestMethod('POST')
        connection.setDoOutput(true)
        connection.setRequestProperty('Authorization', sessionId)
        connection.setRequestProperty('Accept', HEADER_ACCEPT)
        connection.setRequestProperty('X-VaultAPI-ReferenceID', HEADER_REFERENCE_ID)
        connection.setRequestProperty('Content-Type', "multipart/form-data; boundary=$boundary")

        def output = new DataOutputStream(connection.getOutputStream())

        // Helper to write form fields
        def writeFormField = { String name, String value ->
            output.writeBytes("--$boundary\r\n")
            output.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
            output.writeBytes("$value\r\n")
        }

        // Write file field (no redeclaration of `file`)
        output.writeBytes("--$boundary\r\n")
        output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${filename}\"\r\n")
        output.writeBytes("Content-Type: application/pdf\r\n\r\n")
        output.write(bytes)
        output.writeBytes("\r\n")

        // Write all other form fields
        writeFormField('name__v', filename)
        writeFormField('type__v', TYPE)
        writeFormField('subtype__v', SUBTYPE)
        writeFormField('classification__v', CLASSIFICATION)
        writeFormField('lifecycle__v', LIFECYCLE)
        writeFormField('title__v', TITLE)
        writeFormField('document_unit__c', DOCUMENT_UNIT)
        writeFormField('retention_record_category__c', RETENTION_CATEGORY)
        writeFormField('owning_division__c', OWNING_DIVISION)
        writeFormField('process__c', PROCESS)
        writeFormField('nature_of_document__c', NATURE_OF_DOCUMENT)
        writeFormField('document_scope__c', DOCUMENT_SCOPE)
        writeFormField('manufacturing_instruction_type1__c', MANUFACTURING_INSTRUCTION_TYPE)

        output.writeBytes("--$boundary--\r\n")
        output.flush()
        output.close()

        // Read and parse response
        def responseStream = connection.getInputStream()
        def responseText = responseStream.getText()
        def json = new JsonSlurper().parseText(responseText)

        if (json.responseStatus != 'SUCCESS') {
            throw new RuntimeException("Vault upload failed: ${json}")
        }

        return json.id as int
    }
}


