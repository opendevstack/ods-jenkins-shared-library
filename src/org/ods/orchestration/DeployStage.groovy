package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.services.OpenShiftService
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.orchestration.util.PipelinePhaseLifecycleStage

import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import org.ods.util.ILogger
import groovy.json.JsonOutput

class DeployStage extends Stage {

    public final String STAGE_NAME = 'Deploy'

    DeployStage(def script, Project project, List<Set<Map>> repos, String startMROStageName) {
        super(script, project, repos, startMROStageName)
    }

    @SuppressWarnings(['ParameterName', 'AbcMetric', 'MethodSize', 'LineLength'])
    def run() {
        def steps = ServiceRegistry.instance.get(IPipelineSteps)
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
        def os = ServiceRegistry.instance.get(OpenShiftService)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)
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
                    script.sh "cp -r ${standardWorkspace}/docs ${script.env.WORKSPACE} | true"
                    script.sh "cp -r ${standardWorkspace}/projectData ${script.env.WORKSPACE} | true"
                    levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
                }
            } else {
                levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
            }
        }

        def postExecuteRepo = { steps_, repo ->
            // In case we run the phase on an agent node, we need to make sure that
            // the levaDocScheduler.run is executed on the master node, as it does
            // not work on agent nodes yet.
            if (agentPodCondition) {
                script.node {
                    script.sh "cp -r ${standardWorkspace}/docs ${script.env.WORKSPACE} | true"
                    script.sh "cp -r ${standardWorkspace}/projectData ${script.env.WORKSPACE} | true"
                    if (repo.type?.toLowerCase() == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_INFRA) {
                        logger.info("Loading ODS Infrastructure/Configuration Management component type data for '${repo.id}' - on agent")
                        loadOdsInfraTypeData(repo)
                    }

                    levaDocScheduler.run(
                        phase,
                        PipelinePhaseLifecycleStage.POST_EXECUTE_REPO,
                        repo,
                        repo.data
                    )
                }
            } else {
                if (repo.type?.toLowerCase() == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_INFRA) {
                    logger.info("Loading ODS Infrastructure/Configuration Management component type data for '${repo.id}' - on master")
                    loadOdsInfraTypeData(repo)
                }

                levaDocScheduler.run(
                    phase,
                    PipelinePhaseLifecycleStage.POST_EXECUTE_REPO,
                    repo,
                    repo.data
                )
            }
        }

        runOnAgentPod(agentPodCondition) {
            if (project.isPromotionMode) {
                def targetEnvironment = project.buildParams.targetEnvironment
                def targetProject = project.targetProject
                def installableRepos = this.project.repositories.findAll { repo ->
                    if (repo.type?.toLowerCase() != MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_INFRA) {
                        MROPipelineUtil.PipelineConfig.INSTALLABLE_REPO_TYPES.contains(repo.type)
                    }
                }
                logger.info("Deploying project '${project.key}' into environment '${targetEnvironment}'" +
                    " installable repos? ${installableRepos.size()}")

                if (installableRepos?.size() > 0) {
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
                    if (!os.envExists(targetProject)) {
                        throw new RuntimeException(
                            "Error: target environment '${targetProject}' does not exist " +
                            "in ${project.openShiftTargetApiUrl}."
                        )
                    }
                }
            }

            Closure generateDocuments = {
                levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.POST_START)
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
        }

        levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.PRE_END)
    }

    private void loadOdsInfraTypeData (Map repo) {
        ILogger logger = ServiceRegistry.instance.get(Logger)
        if (repo.include) {
            if (repo.data == null) {
                repo.data = [:]
            }
            def steps = ServiceRegistry.instance.get(IPipelineSteps)

            // collect test results
            if (repo.data.tests == null) {
                repo.data.tests = [:]
            }
            repo.data.tests << [installation: getTestResults(steps, repo, Project.TestType.INSTALLATION)]

            // collect log data
            if (repo.data.logs == null) {
                repo.data.logs = [:]
            }
            repo.data.logs << [created: getLogReports(steps, repo, Project.LogReportType.CHANGES)]
            repo.data.logs << [target: getLogReports(steps, repo, Project.LogReportType.TARGET)]
            repo.data.logs << [state: getLogReports(steps, repo, Project.LogReportType.STATE)]
            if (repo.data.logs.state.content) {
                repo.data.logs.state.content = JsonOutput.prettyPrint(repo.data.logs.state.content[0])
            } else {
                logger.warn("No log state for ${repo.data} found!")
            }
        } else {
            logger.debug("Include flag is set to false so not loading ODS Infrastructure/Configuration Management " +
                "component type data for '${repo.id}'")
        }
    }

}
