import org.ods.parser.JUnitParser
import org.ods.phase.PipelinePhases
import org.ods.service.DocGenService
import org.ods.service.JiraService
import org.ods.service.NexusService
import org.ods.service.ServiceRegistry
import org.ods.usecase.JUnitTestReportsUseCase
import org.ods.usecase.JiraUseCase
import org.ods.usecase.LeVaDocumentUseCase
import org.ods.util.PipelineUtil

def call(Map project, List<Set<Map>> repos) {
    def docGen  = ServiceRegistry.instance.get(DocGenService.class.name)
    def jira    = ServiceRegistry.instance.get(JiraUseCase.class.name)
    def junit   = ServiceRegistry.instance.get(JUnitTestReportsUseCase.class.name)
    def levaDoc = ServiceRegistry.instance.get(LeVaDocumentUseCase.class.name)
    def nexus   = ServiceRegistry.instance.get(NexusService.class.name)
    def util    = ServiceRegistry.instance.get(PipelineUtil.class.name)


    def testResults = [ testsuites: [] ]

    // Execute phase for each repository
    util.prepareExecutePhaseForReposNamedJob(PipelinePhases.TEST_PHASE, repos)
        .each { group ->
            parallel(group)

            group.each { repoId, _ ->
                def repo = project.repositories.find { it.id == repoId }

                def testReportsPath = "junit/${repo.id}"

                // Unstash JUnit test reports into path
                junit.unstashTestReportsIntoPath("test-reports-junit-xml-${repo.id}-${env.BUILD_ID}", "${WORKSPACE}/${testReportsPath}")

                // Report JUnit test reports to Jenkins
                junit.reportTestReportsFromPathToJenkins(testReportsPath)

                // Load JUnit test report files from path
                def testReportFiles = junit.loadTestReportsFromPath("${WORKSPACE}/${testReportsPath}")

                // Parse JUnit test report files into a report
                def testReport = junit.parseTestReportFiles(testReportFiles)

                // Create and store a Development Test Report document
                levaDoc.createDTR("0.1", project, repo, testReport, testReportFiles)

                testResults.testsuites.addAll(testReport.testsuites)
            }
        }

    // Report test results to corresponding test cases in Jira
    jira.reportTestResultsForProject(project.id, testResults)
}

return this
