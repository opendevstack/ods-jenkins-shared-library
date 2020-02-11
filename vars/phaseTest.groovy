import org.ods.scheduler.LeVADocumentScheduler
import org.ods.service.JenkinsService
import org.ods.service.ServiceRegistry
import org.ods.usecase.JUnitTestReportsUseCase
import org.ods.usecase.JiraUseCase
import org.ods.util.MROPipelineUtil
import org.ods.util.PipelineUtil

import groovy.json.JsonOutput

def call(Map project, List<Set<Map>> repos) {
    def jira             = ServiceRegistry.instance.get(JiraUseCase.class.name)
    def junit            = ServiceRegistry.instance.get(JUnitTestReportsUseCase.class.name)
    def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler.class.name)
    def util             = ServiceRegistry.instance.get(PipelineUtil.class.name)

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
        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, project, repo)
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
                    jira.reportTestResultsForComponent(project.id, "Technology-${repo_.id}", ["InstallationTest"], data.tests.installation.testResults)

                    echo "Reporting integration test results to corresponding test cases in Jira for ${repo_.id}"
                    jira.reportTestResultsForComponent(project.id, "Technology-${repo_.id}", ["IntegrationTest"], data.tests.integration.testResults)

                    echo "Reporting acceptance test results to corresponding test cases in Jira for ${repo_.id}"
                    jira.reportTestResultsForComponent(project.id, "Technology-${repo_.id}", ["AcceptanceTest"], data.tests.acceptance.testResults)
                }
            }

            levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, project, repo)

            globalData.tests.acceptance.testReportFiles.addAll(data.tests.acceptance.testReportFiles)
            globalData.tests.installation.testReportFiles.addAll(data.tests.installation.testReportFiles)
            globalData.tests.integration.testReportFiles.addAll(data.tests.integration.testReportFiles)
        }
    }

    levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START, project)

    // Execute phase for each repository
    util.prepareExecutePhaseForReposNamedJob(phase, repos, preExecuteRepo, postExecuteRepo)
        .each { group ->
            parallel(group)
        }

    // Parse all test report files into a single data structure
    globalData.tests.acceptance.testResults = junit.parseTestReportFiles(globalData.tests.acceptance.testReportFiles)
    globalData.tests.installation.testResults = junit.parseTestReportFiles(globalData.tests.installation.testReportFiles)
    globalData.tests.integration.testResults = junit.parseTestReportFiles(globalData.tests.integration.testReportFiles)

    levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, project, [:], globalData)

    // Fail the pipeline in case of failing tests
    junit.failIfTestResultsContainFailure(globalData.tests.installation.testResults)
    junit.failIfTestResultsContainFailure(globalData.tests.integration.testResults)
    junit.failIfTestResultsContainFailure(globalData.tests.acceptance.testResults)
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
    def jenkins = ServiceRegistry.instance.get(JenkinsService.class.name)
    def junit   = ServiceRegistry.instance.get(JUnitTestReportsUseCase.class.name)

    def testReportsPath = "junit/${repo.id}/${type}"

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
