package org.ods.orchestration

import org.ods.orchestration.scheduler.*
import org.ods.orchestration.service.*
import org.ods.orchestration.usecase.*
import org.ods.orchestration.util.*
import org.ods.util.PipelineSteps
import org.ods.services.ServiceRegistry
import org.ods.services.JenkinsService

class BuildStage extends Stage {

    public final String STAGE_NAME = 'Build'

    BuildStage(def script, Project project, List<Set<Map>> repos) {
        super(script, project, repos)
    }

    @SuppressWarnings('ParameterName')
    def run() {
        def steps = ServiceRegistry.instance.get(PipelineSteps)
        def jira = ServiceRegistry.instance.get(JiraUseCase)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)

        def phase = MROPipelineUtil.PipelinePhases.BUILD

        def preExecuteRepo = { steps_, repo ->
            levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
        }

        def postExecuteRepo = { steps_, repo ->
            // FIXME: we are mixing a generic scheduler capability with a data
            // dependency and an explicit repository constraint.
            // We should turn the last argument 'data' of the scheduler into a
            // closure that return data.
            if (project.isAssembleMode
                && repo.type?.toLowerCase() == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE) {

                def data = [ : ]
                def resultsResurrected = !!repo.data?.odsBuildArtifacts?.resurrected
                if (!resultsResurrected) {
                    data << [tests: [unit: getUnitTestResults(steps, repo) ]]
                }

                levaDocScheduler.run(
                    phase,
                    MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO,
                    repo,
                    data
                )

                if (!resultsResurrected) {
                    jira.reportTestResultsForComponent(
                        "Technology-${repo.id}",
                        [Project.TestType.UNIT],
                        data.tests.unit.testResults
                    )
                } else {
                    steps.echo("Resurrected tests - no tests results will be reported to JIRA")
                }
            }
        }

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START)

        // Execute phase for each repository
        util.prepareExecutePhaseForReposNamedJob(phase, repos, preExecuteRepo, postExecuteRepo)
            .each { group ->
                group.failFast = true
                script.parallel(group)
            }

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)
    }

    private List getUnitTestResults(def steps, Map repo) {
        def jenkins = ServiceRegistry.instance.get(JenkinsService)
        def junit = ServiceRegistry.instance.get(JUnitTestReportsUseCase)

        def testReportsPath = "${PipelineUtil.XUNIT_DOCUMENTS_BASE_DIR}/${repo.id}/unit"

        steps.echo("Collecting JUnit XML Reports for ${repo.id}")
        def testReportsStashName = "test-reports-junit-xml-${repo.id}-${steps.env.BUILD_ID}"
        def testReportsUnstashPath = "${steps.env.WORKSPACE}/${testReportsPath}"
        def hasStashedTestReports = jenkins.unstashFilesIntoPath(
            testReportsStashName,
            testReportsUnstashPath,
            'JUnit XML Report'
        )
        if (!hasStashedTestReports) {
            throw new RuntimeException(
                "Error: unable to unstash JUnit XML reports for repo '${repo.id}' " +
                    "from stash '${testReportsStashName}'."
            )
        }

        def testReportFiles = junit.loadTestReportsFromPath(testReportsUnstashPath)

        return [
            // Load JUnit test report files from path
            testReportFiles: testReportFiles,
            // Parse JUnit test report files into a report
            testResults: junit.parseTestReportFiles(testReportFiles),
        ]
    }

}
