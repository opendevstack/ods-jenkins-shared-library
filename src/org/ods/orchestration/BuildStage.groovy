package org.ods.orchestration

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.services.ServiceRegistry

@TypeChecked
class BuildStage extends Stage {

    public final String STAGE_NAME = 'Build'

    BuildStage(def script, Project project, List<Set<Map>> repos, String startMROStageName) {
        super(script, project, repos, startMROStageName)
    }

    def run() {
        def util = ServiceRegistry.instance.get(MROPipelineUtil)
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)

        def phase = MROPipelineUtil.PipelinePhases.BUILD

        def preExecuteRepo = { Map repo ->
            levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
        }

        Closure executeRepo = { Map repo ->
            switch (util.repoType(repo)) {
                case MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE:
                case MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SERVICE:
                    if (this.project.isAssembleMode) {
                        util.executeODSComponent(repo)
                    } else {
                        util.logRepoSkip(phase, repo)
                    }
                    break
                case MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST:
                    util.logRepoSkip(phase, repo)
                    break
                default:
                    util.runCustomInstructionsForPhaseOrSkip(phase, repo)
                    break
            }
        }

        def postExecuteRepo = { Map repo ->
            runPostExecuteRepo(util, levaDocScheduler, repo)
        }

        Closure generateDocuments = {
            levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START)
        }

        Closure executeRepoGroups = {
            util.executeRepoGroups(repos, executeRepo, preExecuteRepo, postExecuteRepo)
        }

        executeInParallel(executeRepoGroups, generateDocuments)

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private runPostExecuteRepo(MROPipelineUtil util, LeVADocumentScheduler levaDocScheduler, Map repo) {
        def jira = ServiceRegistry.instance.get(JiraUseCase)
        // FIXME: we are mixing a generic scheduler capability with a data
        // dependency and an explicit repository constraint.
        // We should turn the last argument 'data' of the scheduler into a
        // closure that return data.
        if (this.project.isAssembleMode
            && util.repoType(repo) == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE) {
            def data = [ : ]
            def resultsResurrected = !!repo.data.openshift.resurrectedBuild
            if (resultsResurrected) {
                this.logger.info("[${repo.id}] Resurrected tests from run " +
                    "${repo.data.openshift.resurrectedBuild} " +
                    "- no unit tests results will be reported")
            } else {
                data << [tests: [unit: getTestResults(this.steps, repo) ]]
                jira.reportTestResultsForComponent(
                    "Technology-${repo.id}".toString(),
                    [Project.TestType.UNIT],
                    data.tests.unit.testResults as Map
                )
            }

            levaDocScheduler.run(
                MROPipelineUtil.PipelinePhases.BUILD,
                MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO,
                repo,
                data
            )
        }
    }

}
