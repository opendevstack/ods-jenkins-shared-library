import org.ods.scheduler.LeVADocumentScheduler
import org.ods.service.JenkinsService
import org.ods.service.ServiceRegistry
import org.ods.usecase.JUnitTestReportsUseCase
import org.ods.usecase.JiraUseCase
import org.ods.util.MROPipelineUtil
import org.ods.util.PipelineUtil
import org.ods.util.Project

def call(Project project, List<Set<Map>> repos) {
    def jira             = ServiceRegistry.instance.get(JiraUseCase)
    def junit            = ServiceRegistry.instance.get(JUnitTestReportsUseCase)
    def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
    def util             = ServiceRegistry.instance.get(MROPipelineUtil)

    def phase = MROPipelineUtil.PipelinePhases.TEST

    def globalData = [
        tests: [
            acceptance: [
                testReportFiles: [],
                testResults: [:]
            ],
            installation: [
                testReportFiles: [],
                testResults: [:]
            ],
            integration: [
                testReportFiles: [],
                testResults: [:]
            ]
        ]
    ]

    def preExecuteRepo = { steps, repo ->
        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
    }

    def postExecuteRepo = { steps, repo ->
        if (repo.type?.toLowerCase() == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST) {
            def data = [
                tests: [
                    acceptance: getAcceptanceTestResults(steps, repo),
                    installation: getInstallationTestResults(steps, repo),
                    integration: getIntegrationTestResults(steps, repo)
                ]
            ]

            project.repositories.each { repo_ ->
                if (repo_.type?.toLowerCase() != MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST) {
                    echo "Reporting installation test results to corresponding test cases in Jira for ${repo_.id}"
                    jira.reportTestResultsForComponent("Technology-${repo_.id}", ["InstallationTest"], data.tests.installation.testResults)

                    echo "Reporting integration test results to corresponding test cases in Jira for ${repo_.id}"
                    jira.reportTestResultsForComponent("Technology-${repo_.id}", ["IntegrationTest"], data.tests.integration.testResults)

                    echo "Reporting acceptance test results to corresponding test cases in Jira for ${repo_.id}"
                    jira.reportTestResultsForComponent("Technology-${repo_.id}", ["AcceptanceTest"], data.tests.acceptance.testResults)
                }
            }

            levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, repo)

            globalData.tests.acceptance.testReportFiles.addAll(data.tests.acceptance.testReportFiles)
            globalData.tests.installation.testReportFiles.addAll(data.tests.installation.testReportFiles)
            globalData.tests.integration.testReportFiles.addAll(data.tests.integration.testReportFiles)
        }
    }

    levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START)

    // Execute phase for each repository
    util.prepareExecutePhaseForReposNamedJob(phase, repos, preExecuteRepo, postExecuteRepo)
        .each { group ->
            parallel(group)
        }

    // Parse all test report files into a single data structure
    globalData.tests.acceptance.testResults = junit.parseTestReportFiles(globalData.tests.acceptance.testReportFiles)
    globalData.tests.installation.testResults = junit.parseTestReportFiles(globalData.tests.installation.testReportFiles)
    globalData.tests.integration.testResults = junit.parseTestReportFiles(globalData.tests.integration.testReportFiles)

    levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, [:], globalData)

    // Warn the build in case of failing tests
    junit.warnBuildIfTestResultsContainFailure(globalData.tests.installation.testResults)
    junit.warnBuildIfTestResultsContainFailure(globalData.tests.integration.testResults)
    junit.warnBuildIfTestResultsContainFailure(globalData.tests.acceptance.testResults)
}

private List getAcceptanceTestResults(def steps, Map repo) {
    return this.getTestResults(steps, repo, "acceptance")
}

private List getInstallationTestResults(def steps, Map repo) {
    return this.getTestResults(steps, repo, "installation")
}

private List getIntegrationTestResults(def steps, Map repo) {
    return this.getTestResults(steps, repo, "integration")
}

private List getTestResults(def steps, Map repo, String type) {
    def jenkins = ServiceRegistry.instance.get(JenkinsService)
    def junit   = ServiceRegistry.instance.get(JUnitTestReportsUseCase)

    def testReportsPath = "${PipelineUtil.XUNIT_DOCUMENTS_BASE_DIR}/${repo.id}/${type}"

    echo "Collecting JUnit XML Reports for ${repo.id}"
    def testReportsStashName = "${type}-test-reports-junit-xml-${repo.id}-${steps.env.BUILD_ID}"
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
