package org.ods.orchestration

import groovy.transform.TypeChecked

import org.ods.services.ServiceRegistry
import org.ods.services.OpenShiftService
import org.ods.services.GitService
import org.ods.orchestration.phases.DeployOdsComponent
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project

@TypeChecked
class DeployStage extends Stage {

    public final String STAGE_NAME = 'Deploy'

    DeployStage(def script, Project project, List<Set<Map>> repos, String startMROStageName) {
        super(script, project, repos, startMROStageName)
    }

    @SuppressWarnings(['AbcMetric', 'MethodSize'])
    def run() {
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
        def os = ServiceRegistry.instance.get(OpenShiftService)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)
        def git = ServiceRegistry.instance.get(GitService)

        def phase = MROPipelineUtil.PipelinePhases.DEPLOY

        def standardWorkspace = this.steps.env.WORKSPACE
        def agentPodCondition = project.isPromotionMode

        Closure preExecuteRepo = { Map repo ->
            // In case we run the phase on an agent node, we need to make sure that
            // the levaDocScheduler.run is executed on the master node, as it does
            // not work on agent nodes yet.
            if (agentPodCondition) {
                this.steps.node {
                    this.steps.sh "cp -r ${standardWorkspace}/docs ${this.steps.env.WORKSPACE}/docs"
                    levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
                }
            } else {
                levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
            }
        }

        Closure executeRepo = { Map repo ->
            switch (util.repoType(repo)) {
                case MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE:
                case MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SERVICE:
                    if (this.project.isPromotionMode) {
                        new DeployOdsComponent(this.project, this.steps, git, this.logger).run(repo)
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

        Closure postExecuteRepo = { Map repo ->
            // In case we run the phase on an agent node, we need to make sure that
            // the levaDocScheduler.run is executed on the master node, as it does
            // not work on agent nodes yet.
            if (agentPodCondition) {
                this.steps.node {
                    this.steps.sh "cp -r ${standardWorkspace}/docs ${this.steps.env.WORKSPACE}/docs"
                    levaDocScheduler.run(
                        phase,
                        MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO,
                        repo,
                        repo.data as Map
                    )
                }
            } else {
                levaDocScheduler.run(
                    phase,
                    MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO,
                    repo,
                    repo.data as Map
                )
            }
        }

        runOnAgentPod(agentPodCondition) {
            if (project.isPromotionMode) {
                def targetEnvironment = project.buildParams.targetEnvironment
                def targetProject = project.targetProject
                this.logger.info("Deploying project '${project.key}' into environment '${targetEnvironment}'")

                if (project.targetClusterIsExternal) {
                    this.logger.info("Target cluster is external, logging into ${project.openShiftTargetApiUrl}")
                    this.steps.withCredentials([
                        this.steps.usernamePassword(
                            credentialsId: project.environmentConfig.credentialsId,
                            usernameVariable: 'EXTERNAL_OCP_API_SA',
                            passwordVariable: 'EXTERNAL_OCP_API_TOKEN'
                        )
                    ]) {
                        OpenShiftService.loginToExternalCluster(
                            this.steps,
                            project.openShiftTargetApiUrl,
                            this.steps.env.EXTERNAL_OCP_API_TOKEN as String
                        )
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

            Closure generateDocuments = {
                levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START)
            }

            Closure executeRepoGroups = {
                util.executeRepoGroups(repos, executeRepo, preExecuteRepo, postExecuteRepo)
            }

            executeInParallel(executeRepoGroups, generateDocuments)
        }

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)
    }

}
