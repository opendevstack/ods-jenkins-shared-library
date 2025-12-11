package org.ods.services.documents.vault.client

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper

import java.nio.charset.StandardCharsets
import java.util.stream.Collectors


class VaultWorkflowClient {

    // Constants
    private static final String WORKFLOW_URL = 'https://bisbx-vault-quality-development-demands.veevavault.com/api/v25.2/objects/documents/actions/Objectworkflow.review1__c'
    private static final String HEADER_ACCEPT = 'application/json'
    private static final String HEADER_CONTENT_TYPE = 'application/x-www-form-urlencoded'
    private static final String HEADER_REFERENCE_ID = 'bi-vqd-integration-edpslc-dev-POC-client'

    // Form field constants
    private static final String DESCRIPTION = 'API Approval Test'
    //private static final String REVIEWERS = 'user:3923957'
    private static final String DUE_DATE = '2026-06-13'

    /**
     * Triggers a workflow and returns the workflow ID.
     * Throws an exception if responseStatus is not SUCCESS.
     * @param sessionId Authorization token
     * @param documentIds List of document IDs to include in the workflow
     * @return workflow ID
     */
    @NonCPS
    String triggerWorkflow(String sessionId, List<Integer> documentIds, List<String> reviewerIds) {
        def url = new URL(WORKFLOW_URL)
        def connection = (HttpURLConnection) url.openConnection()
        connection.setRequestMethod('POST')
        connection.setDoOutput(true)

        // Set headers
        connection.setRequestProperty('Authorization', sessionId)
        connection.setRequestProperty('Accept', HEADER_ACCEPT)
        connection.setRequestProperty('Content-Type', HEADER_CONTENT_TYPE)
        connection.setRequestProperty('X-VaultAPI-ReferenceID', HEADER_REFERENCE_ID)

        // Build contents__sys from document IDs
        def contents = documentIds.collect { "Document:$it" }.join(',')

        // Form data
        def reviewers = composeReviewerUserIds(reviewerIds).join(',')

        def formParams = [
                "description__sys=${URLEncoder.encode(DESCRIPTION, 'UTF-8')}",
                "contents__sys=${URLEncoder.encode(contents, 'UTF-8')}",
                "reviewers__c=${URLEncoder.encode(reviewers, 'UTF-8')}",
                "workflow_due_date__c=${URLEncoder.encode(DUE_DATE, 'UTF-8')}"
        ].join('&')

        def outputStream = connection.getOutputStream()
        outputStream.write(formParams.getBytes(StandardCharsets.UTF_8))
        outputStream.flush()
        outputStream.close()

        // Read and parse response
        def responseStream = connection.getInputStream()
        def responseText = responseStream.getText()
        def json = new JsonSlurper().parseText(responseText)

        if (json.responseStatus != 'SUCCESS') {
            throw new RuntimeException("Vault workflow trigger failed: $json")
        }

        return json.data.workflow_id
    }

    @NonCPS
    List<String> composeReviewerUserIds(List<String> userIds) {
        def reviewers = userIds.stream()
            .map ({ userId -> "user:$userId" })
            .collect(Collectors.toList())

        println "Composed reviewers: $reviewers"

        return reviewers
    }
}
