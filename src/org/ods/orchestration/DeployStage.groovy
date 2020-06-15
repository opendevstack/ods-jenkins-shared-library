package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.services.GitService
import org.ods.services.OpenShiftService
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.util.PipelineSteps
import org.ods.util.Logger
import org.ods.util.ILogger

class DeployStage extends Stage {

    public final String STAGE_NAME = 'Deploy'

    DeployStage(def script, Project project, List<Set<Map>> repos, String startMROStageName) {
        super(script, project, repos, startMROStageName)
    }

    @SuppressWarnings(['ParameterName', 'AbcMetric', 'MethodSize'])
    def run() {
        def steps = ServiceRegistry.instance.get(PipelineSteps)
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
        def os = ServiceRegistry.instance.get(OpenShiftService)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)
        def git = ServiceRegistry.instance.get(GitService)
        ILogger logger = ServiceRegistry.instance.get(Logger)

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

        runOnAgentPod(agentPodCondition) {
            if (project.isPromotionMode) {
                def targetEnvironment = project.buildParams.targetEnvironment
                def targetProject = project.targetProject
                logger.info("Deploying project '${project.key}' into environment '${targetEnvironment}'")

                if (project.targetClusterIsExternal) {
                    logger.info("Target cluster is external, logging into ${project.openShiftTargetApiUrl}")
                    script.withCredentials([
                        script.usernamePassword(
                            credentialsId: project.environmentConfig.credentialsId,
                            usernameVariable: 'EXTERNAL_OCP_API_SA',
                            passwordVariable: 'EXTERNAL_OCP_API_TOKEN'
                        )
                    ]) {
                        OpenShiftService.loginToExternalCluster(
                            steps,
                            project.openShiftTargetApiUrl,
                            script.EXTERNAL_OCP_API_TOKEN
                        )
                    }
                }

                // Check if the target environment exists in OpenShift
                if (!os.envExists()) {
                    throw new RuntimeException(
                        "Error: target environment '${targetProject}' does not exist " +
                        "in ${project.openShiftTargetApiUrl}."
                    )
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

            // record release manager repo state
            if (project.isPromotionMode) {
                if (git.remoteTagExists(project.targetTag)) {
                    logger.debug('Skipping tag because it already exists.')
                } else {
                    util.tagAndPush(project.targetTag)
                }
            }
        }

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)
    }

}
