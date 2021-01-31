package org.ods.orchestration

import groovy.transform.TypeChecked

import org.ods.services.ServiceRegistry
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project

@TypeChecked
class ReleaseStage extends Stage {

    public final String STAGE_NAME = 'Release'

    ReleaseStage(def script, Project project, List<Set<Map>> repos) {
        super(script, project, repos)
    }

    def run() {
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)

        def phase = MROPipelineUtil.PipelinePhases.RELEASE

        Closure preExecuteRepo = { Map repo ->
            levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
        }

        Closure executeRepo = { Map repo ->
            util.runCustomInstructionsForPhaseOrSkip(phase, repo)
        }

        Closure postExecuteRepo = { Map repo ->
            levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, repo)
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

}
