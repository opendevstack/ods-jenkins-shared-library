package org.ods.orchestration

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import org.ods.services.ServiceRegistry
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.usecase.JUnitTestReportsUseCase
import org.ods.orchestration.util.Project
import org.ods.orchestration.util.MROPipelineUtil

@TypeChecked
class TestStage extends Stage {

    public final String STAGE_NAME = 'Test'

    TestStage(def script, Project project, List<Set<Map>> repos, String startMROStageName) {
        super(script, project, repos, startMROStageName)
    }

    def run() {
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)

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

        Closure preExecuteRepo = { Map repo ->
            levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
        }

        Closure executeRepo = { Map repo ->
            switch (util.repoType(repo)) {
                case MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST:
                    util.executeODSComponent(repo)
                    break
                case MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE:
                case MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SERVICE:
                    util.logRepoSkip(phase, repo)
                    break
                default:
                    util.runCustomInstructionsForPhaseOrSkip(phase, repo)
                    break
            }
        }

        Closure postExecuteRepo = { Map repo ->
            runPostExecuteRepo(levaDocScheduler, repo, globalData)
        }

        Closure generateDocuments = {
            levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START)
        }

        Closure executeRepoGroups = {
            util.executeRepoGroups(repos, executeRepo, preExecuteRepo, postExecuteRepo)
        }

        executeInParallel(executeRepoGroups, generateDocuments)

        parseTestReportFiles(globalData)

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, [:], globalData)
    }

    // Parse all test report files into a single data structure
    @TypeChecked(TypeCheckingMode.SKIP)
    private parseTestReportFiles(Map globalData) {
        def junit = ServiceRegistry.instance.get(JUnitTestReportsUseCase)
        globalData.tests.acceptance.testResults = junit.parseTestReportFiles(
            globalData.tests.acceptance.testReportFiles
        )
        globalData.tests.installation.testResults = junit.parseTestReportFiles(
            globalData.tests.installation.testReportFiles
        )
        globalData.tests.integration.testResults = junit.parseTestReportFiles(
            globalData.tests.integration.testReportFiles
        )
    }

    @SuppressWarnings('AbcMetric')
    @TypeChecked(TypeCheckingMode.SKIP)
    private runPostExecuteRepo(LeVADocumentScheduler levaDocScheduler, Map repo, Map globalData) {
        def jira = ServiceRegistry.instance.get(JiraUseCase)
        if (repo.type?.toLowerCase() == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST) {
            def data = [
                tests: [
                    acceptance: getTestResults(this.steps, repo, Project.TestType.ACCEPTANCE),
                    installation: getTestResults(this.steps, repo, Project.TestType.INSTALLATION),
                    integration: getTestResults(this.steps, repo, Project.TestType.INTEGRATION),
                ]
            ]

            jira.reportTestResultsForProject(
                [Project.TestType.INSTALLATION],
                data.tests.installation.testResults
            )

            jira.reportTestResultsForProject(
                [Project.TestType.INTEGRATION],
                data.tests.integration.testResults
            )

            jira.reportTestResultsForProject(
                [Project.TestType.ACCEPTANCE],
                data.tests.acceptance.testResults
            )

            levaDocScheduler.run(
                MROPipelineUtil.PipelinePhases.TEST,
                MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO,
                repo,
            )

            globalData.tests.acceptance.testReportFiles.addAll(data.tests.acceptance.testReportFiles)
            globalData.tests.installation.testReportFiles.addAll(data.tests.installation.testReportFiles)
            globalData.tests.integration.testReportFiles.addAll(data.tests.integration.testReportFiles)
        }
    }

}
