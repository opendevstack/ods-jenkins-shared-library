import org.ods.util.MultiRepoOrchestrationPipelineUtil

// Create and store an InstallationReport
def call(Map metadata) {
    // Configuration data
    def id = "InstallationReport"
    def version = "0.1"
    def data = [
        metadata: [
            name: metadata.name,
            description: metadata.description,
            version: version,
            date_created: java.time.LocalDateTime.now().toString()
        ]
    ]

    def util = new MultiRepoOrchestrationPipelineUtil(this)

    // Create the report
    def document = docGenCreateDocument(metadata, id, version, data)

    // Store the report as pipeline artifact
    util.archiveArtifact(".tmp/documents", id, "${version}.pdf", document)

    // Store the report as artifact in Nexus
    def uri = nexusStoreArtifact(metadata,
        metadata.services.nexus.repository.name,
        "${metadata.id.toLowerCase()}-${version}",
        id, version, document, "application/pdf"
    )

    // Search for the Jira issue for this report
    def query = "project = ${metadata.id} AND labels = LeVA_Doc:TIR"
    def issues = jiraGetIssuesForJQLQuery(metadata, query)
    if (issues.isEmpty()) {
        error "Error: Jira query returned 0 issues: '${query}'"
    } else if (issues.size() > 1) {
        error "Error: Jira query returned > 1 issues: '${query}'"
    }

    // Add a comment to the Jira issue with a link to the report
    jiraAppendCommentToIssue(metadata, issues[0].key, "A new ${id} has been generated and is available at: ${uri}.")
}

return this
