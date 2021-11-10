package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.usecase.JUnitTestReportsUseCase
import org.ods.orchestration.util.Project
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.util.PipelineSteps

@SuppressWarnings(['AbcMetric'])
class TestStage extends Stage {

    public final String STAGE_NAME = 'Test'

    TestStage(def script, Project project, List<Set<Map>> repos, String startMROStageName) {
        super(script, project, repos, startMROStageName)
    }

    @SuppressWarnings(['AbcMetric', 'ParameterName'])
    def run() {
        def steps = ServiceRegistry.instance.get(PipelineSteps)
        def jira = ServiceRegistry.instance.get(JiraUseCase)
        def junit = ServiceRegistry.instance.get(JUnitTestReportsUseCase)
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)

        def phase = MROPipelineUtil.PipelinePhases.TEST

        def globalStructure = [
            testReportFiles: [],
            testResults: [
                testsuites: []
            ],
        ]
        def globalData = [
            tests: [
                acceptance: globalStructure,
                installation: globalStructure,
                integration: globalStructure,
            ]
        ]

        def preExecuteRepo = { steps_, repo ->
            levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
        }

        def postExecuteRepo = { steps_, repo ->
            if (repo.type?.toLowerCase() == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST) {
                def data = [
                    tests: [
                        acceptance: getTestResults(steps, repo, Project.TestType.ACCEPTANCE),
                        installation: getTestResults(steps, repo, Project.TestType.INSTALLATION),
                        integration: getTestResults(steps, repo, Project.TestType.INTEGRATION),
                    ]
                ]

                levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, repo)

                // Add the info of component to global data to maintain several e2e
                globalData.tests.acceptance.testReportFiles.addAll(data.tests.acceptance.testReportFiles)
                globalData.tests.installation.testReportFiles.addAll(data.tests.installation.testReportFiles)
                globalData.tests.integration.testReportFiles.addAll(data.tests.integration.testReportFiles)

                globalData.tests.acceptance.testResults.testsuites.addAll(
                    data.tests.acceptance.testResults.testsuites)
                globalData.tests.installation.testResults.testsuites.addAll(
                    data.tests.installation.testResults.testsuites)
                globalData.tests.integration.testResults.testsuites.addAll(
                    data.tests.integration.testResults.testsuites)
            }
        }

        Closure generateDocuments = {
            levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START)
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

        // Update Jira issues with global data
        jira.reportTestResultsForProject(
            [Project.TestType.INSTALLATION],
            globalData.tests.installation.testResults
        )

        jira.reportTestResultsForProject(
            [Project.TestType.INTEGRATION],
            globalData.tests.integration.testResults
        )

        jira.reportTestResultsForProject(
            [Project.TestType.ACCEPTANCE],
            globalData.tests.acceptance.testResults
        )

        // Parse all test report files into a single data structure
        globalData.tests.acceptance.testResults = junit.parseTestReportFiles(
            globalData.tests.acceptance.testReportFiles
        )
        globalData.tests.installation.testResults = junit.parseTestReportFiles(
            globalData.tests.installation.testReportFiles
        )
        globalData.tests.integration.testResults = junit.parseTestReportFiles(
            globalData.tests.integration.testReportFiles
        )

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, [:], globalData)
    }

}
