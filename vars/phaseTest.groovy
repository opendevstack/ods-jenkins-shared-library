import org.ods.parser.JUnitParser
import org.ods.phase.PipelinePhases
import org.ods.util.MultiRepoOrchestrationPipelineUtil
import org.ods.service.DocGenService
import org.ods.service.JiraService
import org.ods.service.NexusService
import org.ods.usecase.JUnitTestReportsUseCase
import org.ods.usecase.JiraUseCase
import org.ods.usecase.LeVaDocumentUseCase

def call(Map project, List<Set<Map>> repos) {
    def pipelineUtil = new MultiRepoOrchestrationPipelineUtil(this)

    def docGen = new DocGenService(env.DOCGEN_URL)
    def nexus = new NexusService(env.NEXUS_URL, env.NEXUS_USERNAME, env.NEXUS_PASSWORD)

    def jira
    def jiraService
    withCredentials([ usernamePassword(credentialsId: project.services.jira.credentials.id, usernameVariable: "JIRA_USERNAME", passwordVariable: "JIRA_PASSWORD") ]) {
        jiraService = new JiraService(env.JIRA_URL, env.JIRA_USERNAME, env.JIRA_PASSWORD)
        jira = new JiraUseCase(this, jiraService)
    }

    def junit = new JUnitTestReportsUseCase(this)
    def levaDocs = new LeVaDocumentUseCase(this, docGen, jiraService, nexus, pipelineUtil)

    def testResults = [ testsuites: [] ]

    // Execute phase for each repository
    pipelineUtil.prepareExecutePhaseForReposNamedJob(PipelinePhases.TEST_PHASE, repos)
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
                levaDocs.createDTR("0.1", project, repo, testReport, testReportFiles)

                testResults.testsuites.addAll(testReport.testsuites)
            }
        }

    // Report test results to corresponding test cases in Jira
    jira.reportTestResultsForProject(project.id, testResults)
}

return this
