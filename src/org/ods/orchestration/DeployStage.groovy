package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.orchestration.scheduler.*
import org.ods.orchestration.service.*
import org.ods.orchestration.usecase.*
import org.ods.orchestration.util.*

class DeployStage extends Stage {

    public final String STAGE_NAME = 'Deploy'

    DeployStage(def script, Project project, List<Set<Map>> repos) {
        super(script, project, repos)
    }

    @SuppressWarnings(['ParameterName', 'AbcMetric'])
    def run() {
        def steps = ServiceRegistry.instance.get(PipelineSteps)
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
        def os = ServiceRegistry.instance.get(OpenShiftService)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)
        def git = ServiceRegistry.instance.get(GitService)

        def phase = MROPipelineUtil.PipelinePhases.DEPLOY

        def standardWorkspace = script.env.WORKSPACE
        def agentPodCondition = project.isPromotionMode

        def preExecuteRepo = { steps_, repo ->
            // In case we run the phase on an agent node, we need to make sure that
            // the levaDocScheduler.run is executed on the master node, as it does
            // not work on agent nodes yet.
            if (agentPodCondition) {
                script.node {
                    script.sh "cp -r ${standardWorkspace}/docs ${script.env.WORKSPACE}/docs"
                    levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
                }
            } else {
                levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
            }
        }

        def postExecuteRepo = { steps_, repo ->
            // In case we run the phase on an agent node, we need to make sure that
            // the levaDocScheduler.run is executed on the master node, as it does
            // not work on agent nodes yet.
            if (agentPodCondition) {
                script.node {
                    script.sh "cp -r ${standardWorkspace}/docs ${script.env.WORKSPACE}/docs"
                    levaDocScheduler.run(
                        phase,
                        MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO,
                        repo,
                        repo.data
                    )
                }
            } else {
                levaDocScheduler.run(
                    phase,
                    MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO,
                    repo,
                    repo.data
                )
            }
        }

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START)

        runOnAgentPod(project, agentPodCondition) {
            if (project.isPromotionMode) {
                def targetEnvironment = project.buildParams.targetEnvironment
                def targetProject = project.targetProject
                steps.echo("Deploying project '${project.key}' into environment '${targetEnvironment}'")

                if (project.targetClusterIsExternal) {
                    script.withCredentials([
                        script.usernamePassword(
                            credentialsId: project.environmentConfig.credentialsId,
                            usernameVariable: 'EXTERNAL_OCP_API_SA',
                            passwordVariable: 'EXTERNAL_OCP_API_TOKEN'
                        )
                    ]) {
                        os.loginToExternalCluster(project.openShiftTargetApiUrl, script.EXTERNAL_OCP_API_TOKEN)
                    }
                }

                // Check if the target environment exists in OpenShift
                if (!os.envExists(targetProject)) {
                    throw new RuntimeException(
                        "Error: target environment '${targetProject}' does not exist " +
                        "in ${project.openShiftTargetApiUrl}."
                    )
                }
            }

            // Execute phase for each repository
            util.prepareExecutePhaseForReposNamedJob(phase, repos, preExecuteRepo, postExecuteRepo)
                .each { group ->
                    group.failFast = true
                    script.parallel(group)
                }

            // record release manager repo state
            if (project.isPromotionMode) {
                if (git.remoteTagExists(project.targetTag)) {
                    steps.echo("Skipping tag because it already exists.")
                } else {
                    util.tagAndPush(project.targetTag)
                }
            }
        }

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)
    }

}
