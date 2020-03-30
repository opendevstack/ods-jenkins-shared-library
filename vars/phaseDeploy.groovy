import org.ods.scheduler.LeVADocumentScheduler
import org.ods.service.OpenShiftService
import org.ods.service.ServiceRegistry
import org.ods.util.GitUtil
import org.ods.util.MROPipelineUtil
import org.ods.util.Project

def call(Project project, List<Set<Map>> repos) {
    def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
    def os               = ServiceRegistry.instance.get(OpenShiftService)
    def util             = ServiceRegistry.instance.get(MROPipelineUtil)
    def git              = ServiceRegistry.instance.get(GitUtil)

    def phase = MROPipelineUtil.PipelinePhases.DEPLOY

    def standardWorkspace = env.WORKSPACE
    def agentPodCondition = project.isPromotionMode

    def preExecuteRepo = { steps, repo ->
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

    def postExecuteRepo = { steps, repo ->
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

    try {
        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START)

        runOnAgentPod(project, agentPodCondition) {
            if (project.isPromotionMode) {
                def targetEnvironment = project.buildParams.targetEnvironment
                def targetProject = project.targetProject
                steps.echo "Deploying project '${project.key}' into environment '${targetEnvironment}'"

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
                    steps.echo "Skipping tag because it already exists."
                } else {
                    util.tagAndPush(project.targetTag)
                }
            }
        }

        levaDocScheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)
    } catch (e) {
        this.steps.echo(e.message)
        project.reportPipelineStatus(e)
        throw e
    }
}

return this
