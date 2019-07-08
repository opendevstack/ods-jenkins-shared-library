// Demo Scenario: create reports and store in Nexus
def call(List reports, String version, Map projectMetadata) {
    reports.each { report ->
        // Create a report
        def document = docGenCreateDocument(report.id, version, report.data)

        // Store the report in the Pipeline
        archiveBinaryArtifact(document, ".tmp/documents", report.id, "${version}.pdf")

        // Store the report in Nexus
        def uri = nexusStoreArtifact(
            projectMetadata.services.nexus.repository.name,
            projectMetadata.services.nexus.repository.directories.reports,
            report.id, version, document, "application/pdf"
        )

        // Search for the Jira issue for this report
        def issues = jiraGetIssuesForJQLQuery(report.jiraIssueJQL)
        if (issues.isEmpty()) {
            error "Error: Jira query returned 0 issues: '${query}'"
        } else if (issues.size() > 1) {
            error "Error: Jira query returned > 1 issues: '${query}'"
        }

        // Add a comment to the Jira issue with a link to the report
        jiraAppendCommentToIssue(issues[0].key, "A new ${report.id} has been generated and is available at: ${uri}.")
    }
}

return this
