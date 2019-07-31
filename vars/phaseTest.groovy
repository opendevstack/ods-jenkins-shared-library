import org.ods.parser.JUnitParser
import org.ods.phase.PipelinePhases
import org.ods.service.JiraService
import org.ods.util.FileUtil
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

    repos.each { group ->
        group.each { repo ->
            if (repo.type == 'ods') {
                // Unstash JUnit XML test reports into the local file system
                def testReportsBaseDirPath = "junit/${repo.id}"
                def testReportsBaseDir = FileUtil.createDirectory("${env.WORKSPACE}/${testReportsBaseDirPath}")
                dir(testReportsBaseDirPath) {
                    unstash name: "test-reports-junit-xml-${repo.id}-${env.BUILD_ID}"
                }

                // Parse individual JUnit XML test reports for the current repo
                def testReportFiles = []
                testReportsBaseDir.traverse(nameFilter: ~/.*\.xml$/, type: groovy.io.FileType.FILES) { file ->
                    testReportFiles << file
                }

                // Combine individual JUnit XML test reports into a single report
                def testReport = [ testsuites: [] ]
                testReportFiles.inject(testReport) { current, next ->
                    def testReport_ = JUnitParser.parseJUnitXML(next.text)
                    current.testsuites.addAll(testReport_.testsuites)
                }


                // Transform the parsed JUnit XML report into a simple result format
                def testReportSimple = JUnitParser.Helper.toSimpleFormat(testReport)

                // Create Jira bugs for erroneous tests
                def errors = JUnitParser.Helper.toSimpleErrorsFormat(testReportSimple)
                jiraCreateBugsForTestFailures(metadata, errors, jiraIssues)

                // Create Jira bugs for failed tests
                def failures = JUnitParser.Helper.toSimpleFailuresFormat(testReportSimple)
                jiraCreateBugsForTestFailures(metadata, failures, jiraIssues)

                // Mark Jira issues according to test results
                jiraMarkIssuesForTestResults(metadata, testReportSimple, jiraIssues)

                // Provide Junit XML reports to Jenkins
                junit "${testReportsBaseDirPath}/**/*.xml"


                createDevelopmentTestReport(metadata, repo, testReport, testReportFiles)
            }
        }
    }
}

return this
