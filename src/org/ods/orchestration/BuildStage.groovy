package org.ods.orchestration

import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PipelinePhaseLifecycleStage
import org.ods.orchestration.util.Project
import org.ods.services.ServiceRegistry
import org.ods.util.PipelineSteps
import org.ods.util.Logger
import org.ods.util.ILogger

class BuildStage extends Stage {

    public final String STAGE_NAME = 'Build'

    BuildStage(def script, Project project, List<Set<Map>> repos, String startMROStageName) {
        super(script, project, repos, startMROStageName)
    }

    @SuppressWarnings(['ParameterName', 'AbcMetric'])
    def run() {
        def steps = ServiceRegistry.instance.get(PipelineSteps)
        def jira = ServiceRegistry.instance.get(JiraUseCase)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
        ILogger logger = ServiceRegistry.instance.get(Logger)

        def phase = MROPipelineUtil.PipelinePhases.BUILD

        def preExecuteRepo = { steps_, repo ->
            levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
        }

        def postExecuteRepo = { steps_, repo ->
            // FIXME: we are mixing a generic scheduler capability with a data
            // dependency and an explicit repository constraint.
            // We should turn the last argument 'data' of the scheduler into a
            // closure that return data.
            if (project.isAssembleMode
                && repo.type?.toLowerCase() == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE) {
                def data = [ : ]
                def resultsResurrected = !!repo.data.openshift.resurrectedBuild
                if (resultsResurrected) {
                    logger.info("[${repo.id}] Resurrected tests from run " +
                        "${repo.data.openshift.resurrectedBuild} " +
                        "- no unit tests results will be reported")
                } else {
                    data << [tests: [unit: getTestResults(steps, repo) ]]
                    jira.reportTestResultsForComponent(
                        "Technology-${repo.id}",
                        [Project.TestType.UNIT],
                        data.tests.unit.testResults
                    )
                    // we check in any case ... (largely because the above call will
                    // return immediatly when no jira adapter is configured).
                    // this  will set failedTests if any xunit tests have failed
                    util.warnBuildIfTestResultsContainFailure(data.tests.unit.testResults)
                }

                logger.info("levaDocScheduler.run start")
                levaDocScheduler.run(
                    phase,
                    PipelinePhaseLifecycleStage.POST_EXECUTE_REPO,
                    repo,
                    data
                )
                logger.info("levaDocScheduler.run end")
            }
        }

//        Closure generateDocuments = {
            levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.POST_START)
//        }

        // Execute phase for each repository
 //       Closure executeRepos = {
            util.prepareExecutePhaseForReposNamedJob(phase, repos, preExecuteRepo, postExecuteRepo)
                .each { group ->
                    // FailFast only if not WIP
                    group.failFast = !project.isWorkInProgress
                    script.parallel(group)
                }
 //       }

//        executeInParallel(executeRepos, generateDocuments)
        levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.PRE_END)

        // in case of WIP we fail AFTER all pieces have been executed - so we can report as many
        // failed unit tests as possible
        // - this will only apply in case of WIP! - otherwise failfast is configured, and hence
        // the build will have failed beforehand
        def failedRepos = repos.flatten().findAll { it.data?.failedStage }
        if (project.hasFailingTests() || failedRepos.size > 0) {
            def errMessage = "Failing build as repositories contain errors!\nFailed: ${failedRepos}"
            util.failBuild(errMessage)
            // If we are not in Developer Preview raise a exception
            if (!project.isWorkInProgress) {
                throw new IllegalStateException(errMessage)
            }
        }
    }

}
