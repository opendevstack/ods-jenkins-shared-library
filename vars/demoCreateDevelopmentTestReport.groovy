import org.ods.util.MultiRepoOrchestrationPipelineUtil
import org.ods.parser.JUnitParser

// Create and store an DevelopmentTestReport
def call(Map metadata) {

	def testXml = '''<testsuites duration="50.5">
	    <testsuite failures="0" name="Untitled suite in /Users/niko/Sites/casperjs/tests/suites/casper/agent.js" package="tests/suites/casper/agent" tests="3" time="0.256">
	        <testcase classname="tests/suites/casper/agent" name="Default user agent matches /CasperJS/" time="0.103"/>
	        <testcase classname="tests/suites/casper/agent" name="Default user agent matches /plop/" time="0.146"/>
	        <testcase classname="tests/suites/casper/agent" name="Default user agent matches /plop/" time="0.007"/>
	    </testsuite>
	    <testsuite failures="0" name="Untitled suite in /Users/niko/Sites/casperjs/tests/suites/casper/alert.js" package="tests/suites/casper/alert" tests="1" time="0.449">
	        <testcase classname="tests/suites/casper/alert" name="alert event has been intercepted" time="0.449"/>
	    </testsuite>
	    <testsuite failures="0" name="Untitled suite in /Users/niko/Sites/casperjs/tests/suites/casper/auth.js" package="tests/suites/casper/auth" tests="8" time="0.101">
	        <testcase classname="tests/suites/casper/auth" name="Subject equals the expected value" time="0.1"/>
	        <testcase classname="tests/suites/casper/auth" name="Subject equals the expected value" time="0"/>
	        <testcase classname="tests/suites/casper/auth" name="Subject equals the expected value" time="0"/>
	        <testcase classname="tests/suites/casper/auth" name="Subject equals the expected value" time="0.001"/>
	        <testcase classname="tests/suites/casper/auth" name="Subject equals the expected value" time="0"/>
	        <testcase classname="tests/suites/casper/auth" name="Subject equals the expected value" time="0"/>
	        <testcase classname="tests/suites/casper/auth" name="Subject equals the expected value" time="0"/>
	        <testcase classname="tests/suites/casper/auth" name="Subject equals the expected value" time="0">
	        </testcase>
	    </testsuite>
	</testsuites>'''

	// Configuration data
	def id = "DevelopmentTestReport"
	def version = "0.1"
	def data = [
		metadata: [
			name: metadata.name,
			description: metadata.description,
			version: version,
			date_created: java.time.LocalDateTime.now().toString(),
			type: id
		],
		data: [
			testsuites: new JUnitParser().parseJUnitXML(testXml)
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
	def query = "project = ${metadata.id} AND labels = LeVA_Doc:DTR"
    def issues = jiraGetIssuesForJQLQuery(metadata, query)
    if (issues.size() != 1) {
        error "Error: Jira query returned ${issues.size()} issues: '${query}'"
    } 

    // Add a comment to the Jira issue with a link to the report
    jiraAppendCommentToIssue(metadata, issues.iterator().next().value.key, "A new ${id} has been generated and is available at: ${uri}.")
}

return this
