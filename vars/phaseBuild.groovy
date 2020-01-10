import org.ods.scheduler.LeVADocumentScheduler
import org.ods.service.JenkinsService
import org.ods.service.ServiceRegistry
import org.ods.usecase.JUnitTestReportsUseCase
import org.ods.usecase.JiraUseCase
import org.ods.util.MROPipelineUtil
import org.ods.util.PipelineUtil

def call(Map project, List<Set<Map>> repos) {
    def jira             = ServiceRegistry.instance.get(JiraUseCase.class.name)
    def util             = ServiceRegistry.instance.get(PipelineUtil.class.name)
    def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler.class.name)

    def phase = MROPipelineUtil.PipelinePhases.BUILD

    def preExecuteRepo = { steps, repo ->
        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, project, repo)
    }

    def postExecuteRepo = { steps, repo ->
        // FIXME: we are mixing a generic scheduler capability with a data dependency and an explicit repository constraint.
        // We should turn the last argument 'data' of the scheduler into a closure that return data.
        if (repo.type?.toLowerCase() == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE) {
            def data = getTestData(steps, repo)

            levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, project, repo, data)

            // Report test results to corresponding test cases in Jira
            jira.reportTestResultsForComponent(project.id, "Technology-${repo.id}", "UnitTest", data.testResults)
        }
    }

    levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START, project)

    // Execute phase for each repository
    util.prepareExecutePhaseForReposNamedJob(phase, repos, preExecuteRepo, postExecuteRepo)
        .each { group ->
            parallel(group)
        }

    levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, project)
}

private Map getTestData(def steps, Map repo) {
    def jenkins = ServiceRegistry.instance.get(JenkinsService.class.name)
    def junit   = ServiceRegistry.instance.get(JUnitTestReportsUseCase.class.name)

    def testReportsPath = "junit/${repo.id}"

    echo "Collecting JUnit XML Reports for ${repo.id}"
    def testReportsStashName = "test-reports-junit-xml-${repo.id}-${steps.env.BUILD_ID}"
    def testReportsUnstashPath = "${steps.env.WORKSPACE}/${testReportsPath}"
    def hasStashedTestReports = jenkins.unstashFilesIntoPath(testReportsStashName, testReportsUnstashPath, "JUnit XML Report")
    if (!hasStashedTestReports) {
        throw new RuntimeException("Error: unable to unstash JUnit XML reports for repo '${repo.id}' from stash '${testReportsStashName}'.")
    }

    def testReportFiles = junit.loadTestReportsFromPath(testReportsUnstashPath)

    return [
        // Load JUnit test report files from path
        testReportFiles: testReportFiles,
        // Parse JUnit test report files into a report
        testResults: junit.parseTestReportFiles(testReportFiles)
    ]
}

return this
