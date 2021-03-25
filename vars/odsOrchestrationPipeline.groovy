import java.nio.file.Paths

@Grab(group='com.konghq', module='unirest-java', version='2.4.03', classifier='standalone')
import kong.unirest.Unirest

import org.ods.orchestration.util.PipelineUtil
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.orchestration.InitStage
import org.ods.orchestration.BuildStage
import org.ods.orchestration.DeployStage
import org.ods.orchestration.TestStage
import org.ods.orchestration.ReleaseStage
import org.ods.orchestration.FinalizeStage
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.services.GitService
import org.ods.util.Logger
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.PipelineSteps

@SuppressWarnings('AbcMetric')
def call(Map config) {
    Unirest.config()
        .socketTimeout(1200000)
        .connectTimeout(120000)

    def steps = new PipelineSteps(this)

    def debug = config.get('debug', false)
    ServiceRegistry.instance.add(Logger, new Logger(this, debug))
    ILogger logger = ServiceRegistry.instance.get(Logger)
    def git = new GitService(steps, logger)

    def odsImageTag = config.odsImageTag
    if (!odsImageTag) {
        error "You must set 'odsImageTag' in the config map"
    }
    def versionedDevEnvsEnabled = config.get('versionedDevEnvs', false)
    def alwaysPullImage = !!config.get('alwaysPullImage', true)
    boolean startAgentEarly = config.get('startOrchestrationAgentOnInit', true)
    def startAgentStage = startAgentEarly ? MROPipelineUtil.PipelinePhases.INIT : null

    logger.debug ("Start agent stage: ${startAgentStage}")
    Project project = new Project(steps, logger)
    def repos = []

    logger.startClocked('orchestration-master-node')
    node ('master') {
        logger.debugClocked('orchestration-master-node')
        // Clean workspace from previous runs
        [
            PipelineUtil.ARTIFACTS_BASE_DIR,
            PipelineUtil.SONARQUBE_BASE_DIR,
            PipelineUtil.XUNIT_DOCUMENTS_BASE_DIR,
            MROPipelineUtil.REPOS_BASE_DIR,
        ].each { name ->
            logger.debug("Cleaning workspace directory '${name}' from previous runs")
            Paths.get(env.WORKSPACE, name).toFile().deleteDir()
        }

        logger.startClocked('pipeline-git-releasemanager')
        def scmBranches = scm.branches
        def branch = scmBranches[0]?.name
        if (branch && !branch.startsWith('*/')) {
            scmBranches = [[name: "*/${branch}"]]
        }

        // checkout local branch
        git.checkout(
            scmBranches,
            [[$class: 'LocalBranch', localBranch: '**']],
            scm.userRemoteConfigs,
            scm.doGenerateSubmoduleConfigurations
            )
        logger.debugClocked('pipeline-git-releasemanager')

        def envs = Project.getBuildEnvironment(steps, debug, versionedDevEnvsEnabled)

        logger.startClocked('pod-template')
        withPodTemplate(odsImageTag, steps, alwaysPullImage) {
            logger.debugClocked('pod-template')
            withEnv (envs) {
                def result = new InitStage(this, project, repos, startAgentStage).execute()
                if (result) {
                    project = result.project
                    repos = result.repos
                    if (!startAgentStage) {
                        startAgentStage = result.startAgent
                    }
                } else {
                    logger.warn('Skip pipeline as no project/repos computed')
                    return
                }

                new BuildStage(this, project, repos, startAgentStage).execute()

                new DeployStage(this, project, repos, startAgentStage).execute()

                new TestStage(this, project, repos, startAgentStage).execute()

                new ReleaseStage(this, project, repos).execute()

                new FinalizeStage(this, project, repos).execute()
            }
        }
    }
}

@SuppressWarnings('GStringExpressionWithinString')
private withPodTemplate(String odsImageTag, IPipelineSteps steps, boolean alwaysPullImage, Closure block) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    def dockerRegistry = steps.env.DOCKER_REGISTRY ?: 'docker-registry.default.svc:5000'
    def podLabel = "mro-jenkins-agent-${env.BUILD_NUMBER}"
    def odsNamespace = env.ODS_NAMESPACE ?: 'ods'
    if (!OpenShiftService.envExists(steps, odsNamespace)) {
        logger.warn("Could not find ods namespace '${odsNamespace}' - defaulting to legacy namespace: 'cd'!\r" +
            "Please configure 'env.ODS_NAMESPACE' to point to the ODS Openshift namespace")
        odsNamespace = 'cd'
    }
    podTemplate(
        label: podLabel,
        cloud: 'openshift',
        containers: [
            containerTemplate(
                name: 'jnlp',
                image: "${dockerRegistry}/${odsNamespace}/jenkins-agent-base:${odsImageTag}",
                workingDir: '/tmp',
                resourceRequestMemory: '512Mi',
                resourceLimitMemory: '1Gi',
                resourceRequestCpu: '200m',
                resourceLimitCpu: '1',
                alwaysPullImage: "${alwaysPullImage}",
                args: '${computer.jnlpmac} ${computer.name}',
                envVars: []
            )
        ],
        volumes: [],
        serviceAccount: 'jenkins',
        idleMinutes: 10,
    ) {
        logger.startClocked('ods-mro-pipeline')
        try {
            block()
        } finally {
            logger.infoClocked('ods-mro-pipeline', '**** ENDED orchestration pipeline ****')
        }
    }
}

return this
