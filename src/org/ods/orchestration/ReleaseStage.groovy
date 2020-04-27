package org.ods.orchestration

import hudson.Functions

import org.ods.orchestration.scheduler.*
import org.ods.orchestration.service.*
import org.ods.orchestration.util.*

class ReleaseStage extends Stage {
    public final String STAGE_NAME = 'Release'
    
    ReleaseStage(def script, Project project, List<Set<Map>> repos) {
        super(script, project, repos)
    }

    def run() {
        def steps = ServiceRegistry.instance.get(PipelineSteps)
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)

        def phase = MROPipelineUtil.PipelinePhases.RELEASE

        def preExecuteRepo = { steps_, repo ->
            levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
        }

        def postExecuteRepo = { steps_, repo ->
            levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, repo)
        }

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START)

        util.prepareExecutePhaseForReposNamedJob(phase, repos, preExecuteRepo, postExecuteRepo)
            .each { group ->
                group.failFast = true
                script.parallel(group)
            }

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)
    }
}
