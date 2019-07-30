import groovy.json.JsonOutput

import org.ods.parser.JUnitParser
import org.ods.util.FileUtil
import org.ods.util.MultiRepoOrchestrationPipelineUtil

// Create and store an DevelopmentTestReport
def call(Map metadata, Map repo, Map testReport, List testReportFiles) {

	// Configuration data
	def id = "DevelopmentTestReport"
	def version = "0.1"

	def data = [
		metadata: [
			name: "${metadata.name} / ${repo.id}",
			description: metadata.description,
			version: version,
			date_created: java.time.LocalDateTime.now().toString(),
			type: id
		],
		data: [
			testsuites: testReport
		]
	]

    def util = new MultiRepoOrchestrationPipelineUtil(this)

    // Create the report
    def document = docGenCreateDocument(metadata, id, version, data)

	// Create the report archive
	def archivePath = "${WORKSPACE}/.tmp/artifacts/${id}-${repo.id}-${version}-${env.BUILD_ID}.zip"
	def archive = FileUtil.createZipFile(archivePath,
		[
			"report.pdf": document,
			"raw/report.json": JsonOutput.toJson(data).getBytes()
		] << testReportFiles.collectEntries { file ->
			[ "raw/${file.name}", file.getBytes() ]
		}
	)

	// Store the report archive as pipeline artifact
    util.archiveArtifact(archivePath, archive)

    // Store the report archive as artifact in Nexus
    def uri = nexusStoreArtifact(metadata,
        metadata.services.nexus.repository.name,
        "${metadata.id.toLowerCase()}-${version}",
        "${id}-${repo.id}-${version}.zip",
        archive, "application/zip"
    )

    // Search for the Jira issue for this report
	def query = "project = ${metadata.id} AND labels = LeVA_Doc:DTR"
    def issues = jiraGetIssuesForJQLQuery(metadata, query)
    if (issues.size() != 1) {
        error "Error: Jira query returned ${issues.size()} issues: '${query}'"
    } 

    // Add a comment to the Jira issue with a link to the report
    jiraAppendCommentToIssue(metadata, issues.iterator().next().value.key, "A new ${id} has been generated and is available at: ${uri}.")
}

return this
