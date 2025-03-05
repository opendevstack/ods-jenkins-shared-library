package org.ods.orchestration

import org.ods.orchestration.util.PipelinePhaseLifecycleStage
import org.ods.services.ServiceRegistry
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.usecase.JUnitTestReportsUseCase
import org.ods.orchestration.util.Project
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.util.IPipelineSteps

@SuppressWarnings(['AbcMetric'])
class TestStage extends Stage {

    public final String STAGE_NAME = 'Test'

    TestStage(def script, Project project, List<Set<Map>> repos, String startMROStageName) {
        super(script, project, repos, startMROStageName)
    }

    @SuppressWarnings(['AbcMetric', 'ParameterName'])
    def run() {
        def steps = ServiceRegistry.instance.get(IPipelineSteps)
        def jira = ServiceRegistry.instance.get(JiraUseCase)
        def junit = ServiceRegistry.instance.get(JUnitTestReportsUseCase)
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)
        def phase = MROPipelineUtil.PipelinePhases.TEST
        def globalData = getTestDataStructure()

        def preExecuteRepo = { steps_, repo ->
            levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
        }

        def postExecuteRepo = { steps_, repo ->
            if (repo.type?.toLowerCase() == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST) {
                def data = getTestDataStructure()
                // create local test Data with same structure and
                // fill up with tests results for every type of test
                data.tests.each {
                    Map testResult = getTestResults(steps, repo, it.key.capitalize())
                    it.value.testReportFiles = testResult.testReportFiles
                    it.value.testResults = testResult.testResults
                }

                levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, repo)

                // Add the test results of component to global data to maintain several e2e
                globalData.tests.each {
                    it.value.testReportFiles.addAll(data.tests[it.key].testReportFiles as List)
                    it.value.testResults.testsuites.addAll(
                        data.tests[it.key].testResults.testsuites as List)
                }
            }
        }

        Closure generateDocuments = {
            levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.POST_START)
        }

        // Execute phase for each repository
        Closure executeRepos = {
            util.prepareExecutePhaseForReposNamedJob(phase, repos, preExecuteRepo, postExecuteRepo)
                .each { group ->
                    group.failFast = true
                    script.parallel(group)
                }
        }
        executeInParallel(executeRepos, generateDocuments)

        globalData.tests.each {
            // Update Jira issues with global data test results for every type of test
            jira.reportTestResultsForProject(
                [it.key.capitalize()],
                it.value.testResults as Map
            )
            // Parse again all test report files into a single data structure for one type of test
            it.value.testResults = junit.parseTestReportFiles(it.value.testReportFiles as List<File>)
        }

        levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.PRE_END, [:], globalData)
    }

    private Map getTestDataStructure() {
        Map testData = [
            tests: [
                (Project.TestType.ACCEPTANCE.uncapitalize()): [:],
                (Project.TestType.INSTALLATION.uncapitalize()): [:],
                (Project.TestType.INTEGRATION.uncapitalize()): [:],
            ]
        ]
        testData.tests.each {
            it.value.testReportFiles = []
            it.value.testResults = [ testsuites: [] ]
        }
        return testData
    }

}
