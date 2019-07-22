import org.ods.parser.JUnitParser
import org.ods.phase.PipelinePhases
import org.ods.service.JiraService
import org.ods.util.MultiRepoOrchestrationPipelineUtil

def call(Map metadata, List<Set<Map>> repos) {
    // Get automated test scenarios from Jira
    def jiraIssues = jiraGetIssuesForJQLQuery(metadata, "project = ${metadata.id} AND labels = AutomatedTest AND issuetype = sub-task")

    // Execute phase for each repository
    def util = new MultiRepoOrchestrationPipelineUtil(this)
    util.prepareExecutePhaseForReposNamedJob(PipelinePhases.TEST_PHASE, repos)
        .each { group ->
            parallel(group)
        }

    def testResultsString = """
<testsuites>
    <testsuite name="Create Feature X" tests="1" skipped="0" failures="1" errors="0" timestamp="2019-06-25T18:12:36" hostname="surfer-172-29-1-61-hotspot.internet-for-guests.com" time="1.458">
        <properties/>
        <testcase name="Test Scenario A" classname="app.DocGenSpec" time="0.033">
            <failure message="java.io.FileNotFoundException: /Users/metmajer/Dropbox/IT%20Platform/demo/pltf-doc-gen/build/resources/test/templates.zip (No such file or directory)" type="java.io.FileNotFoundException">java.io.FileNotFoundException: /Users/metmajer/Dropbox/IT%20Platform/demo/pltf-doc-gen/build/resources/test/templates.zip (No such file or directory)
            at java.base/java.io.FileInputStream.open(FileInputStream.java:219)
            at java.base/java.io.FileInputStream.&lt;init&gt;(FileInputStream.java:157)
            at app.SpecHelper.mockTemplatesZipArchiveDownload(SpecHelper.groovy:49)
            at app.DocGenSpec.generate(DocGenSpec.groovy:59)
            </failure>
        </testcase>
        <system-out><![CDATA[[2019-06-25 20:12:36,150]-[Test worker] INFO  app.App - [test@netty]: Server started in 710ms

        POST /document    [application/json]     [application/json]    (/anonymous)

        listening on:
        http://localhost:9000/

        [2019-06-25 20:12:37,616]-[Test worker] INFO  app.App - Stopped
        ]]></system-out>
        <system-err><![CDATA[]]></system-err>
    </testsuite>
    <testsuite name="Create Feature Y" tests="2" skipped="0" failures="0" errors="0" timestamp="2019-06-25T18:12:42" hostname="surfer-172-29-1-61-hotspot.internet-for-guests.com" time="1.458">
        <properties/>
        <testcase name="Test Scenario B" classname="app.DocGenSpec" time="1.311"/>
        <testcase name="Test Scenario C" classname="app.DocGenSpec" time="0.113"/>
        <system-out><![CDATA[[2019-06-25 20:12:36,150]-[Test worker] INFO  app.App - [test@netty]: Server started in 710ms

        POST /document    [application/json]     [application/json]    (/anonymous)

        listening on:
        http://localhost:9000/

        [2019-06-25 20:12:37,616]-[Test worker] INFO  app.App - Stopped
        ]]></system-out>
        <system-err><![CDATA[]]></system-err>
    </testsuite>
</testsuites>
    """

    def testResults = JUnitParser.parseJUnitXML(testResultsString)

    // Transform the JUnit XML parser's results into a simple format
    def testResultsSimple = JUnitParser.Helper.toSimpleFormat(testResults)

    // Transform the JUnit XML parser's results into a simple failures format
    def testFailures = JUnitParser.Helper.toSimpleFailuresFormat(testResults)

    // Create Jira bugs for test failures
    jiraCreateBugsForTestFailures(metadata, testFailures, jiraIssues)

    // Demarcate Jira issues according to test results
    jiraMarkIssuesForTestResults(metadata, testResultsSimple, jiraIssues)

    // Provide Junit XML reports to Jenkins
    writeFile file: ".tmp/JUnitReport.xml", text: testResultsString
    junit ".tmp/*.xml"

    // Create and store a demo DevelopmentTestReport
    demoCreateDevelopmentTestReport(metadata)
}

return this
