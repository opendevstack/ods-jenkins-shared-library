import groovy.json.JsonOutput

import org.ods.util.FileUtil
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

	// Create the report archive
    def archivePath = "${WORKSPACE}/.tmp/artifacts/${id}-${version}.zip"
	def archive = FileUtil.createZipFile(archivePath,
		[
			"raw/report.json": JsonOutput.toJson(data).getBytes(),
			"report.pdf": document
		]
	)

	// Store the report archive as pipeline artifact
    util.archiveArtifact(archivePath, archive)

    // Store the report archive as artifact in Nexus
    def uri = nexusStoreArtifact(metadata,
        metadata.services.nexus.repository.name,
        "${metadata.id.toLowerCase()}-${version}",
        "${id}-${version}.zip",
        archive, "application/zip"
    )

    // Search for the Jira issue for this report
    def query = "project = ${metadata.id} AND labels = LeVA_Doc:TIR"
    def issues = jiraGetIssuesForJQLQuery(metadata, query)
    if (issues.size() != 1) {
        error "Error: Jira query returned ${issues.size()} issues: '${query}'"
    } 

    // Add a comment to the Jira issue with a link to the report
    jiraAppendCommentToIssue(metadata, issues.iterator().next().value.key, "A new ${id} has been generated and is available at: ${uri}.")
}

return this
