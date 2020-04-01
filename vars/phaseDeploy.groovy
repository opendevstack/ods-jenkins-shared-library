import hudson.Functions

import org.ods.scheduler.*
import org.ods.service.*
import org.ods.util.*

def call(Project project, List<Set<Map>> repos) {
    try {
        def steps = ServiceRegistry.instance.get(PipelineSteps)
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
        def os = ServiceRegistry.instance.get(OpenShiftService)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)
        def git = ServiceRegistry.instance.get(GitUtil)

        def phase = MROPipelineUtil.PipelinePhases.DEPLOY

        def standardWorkspace = env.WORKSPACE
        def agentPodCondition = project.isPromotionMode

        def preExecuteRepo = { steps_, repo ->
            // In case we run the phase on an agent node, we need to make sure that
            // the levaDocScheduler.run is executed on the master node, as it does
            // not work on agent nodes yet.
            if (agentPodCondition) {
                node {
                    sh "cp -r ${standardWorkspace}/docs ${env.WORKSPACE}/docs"
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
                node {
                    sh "cp -r ${standardWorkspace}/docs ${env.WORKSPACE}/docs"
                    levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, repo, repo.data)
                }
            } else {
                levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, repo, repo.data)
            }
        }

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START)

        runOnAgentPod(project, agentPodCondition) {
            if (project.isPromotionMode) {
                def targetEnvironment = project.buildParams.targetEnvironment
                def targetProject = project.targetProject
                steps.echo("Deploying project '${project.key}' into environment '${targetEnvironment}'")

                if (project.targetClusterIsExternal) {
                    withCredentials([
                        usernamePassword(
                            credentialsId: project.environmentConfig.credentialsId,
                            usernameVariable: "EXTERNAL_OCP_API_SA",
                            passwordVariable: "EXTERNAL_OCP_API_TOKEN"
                        )
                    ]) {
                        os.loginToExternalCluster(project.openShiftTargetApiUrl, EXTERNAL_OCP_API_TOKEN)
                    }
                }

                // Check if the target environment exists in OpenShift
                if (!os.envExists(targetProject)) {
                    throw new RuntimeException("Error: target environment '${targetProject}' does not exist in ${project.openShiftTargetApiUrl}.")
                }
            }

            // Execute phase for each repository
            util.prepareExecutePhaseForReposNamedJob(phase, repos, preExecuteRepo, postExecuteRepo)
                .each { group ->
                    parallel(group)
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
    } catch (e) {
        // Check for random null references which occur after a Jenkins restart
        if (ServiceRegistry.instance == null || ServiceRegistry.instance.get(PipelineSteps) == null) {
            e = new IllegalStateException("Error: invalid references have been detected for critical pipeline services. Most likely, your Jenkins instance has been recycled. Please re-run the pipeline!").initCause(e)
        }

        echo(e.message)

        try {
            project.reportPipelineStatus(e)
        } catch (reportError) {
            echo("Error: unable to report pipeline status because of: ${reportError.message}.")
            reportError.initCause(e)
            throw reportError
        }

        throw e
    }
}

return this
