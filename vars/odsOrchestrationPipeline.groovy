import java.nio.file.Paths

@Grab(group='com.konghq', module='unirest-java', version='2.4.03', classifier='standalone')
import kong.unirest.Unirest

import org.ods.orchestration.util.PipelineUtil
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.orchestration.Stage
import org.ods.orchestration.InitStage
import org.ods.orchestration.BuildStage
import org.ods.orchestration.DeployStage
import org.ods.orchestration.TestStage
import org.ods.orchestration.ReleaseStage
import org.ods.orchestration.FinalizeStage
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.PipelineSteps

def call(Map config) {
    Unirest.config()
        .socketTimeout(1200000)
        .connectTimeout(120000)

    def steps = new PipelineSteps(this)
    Project project = new Project(steps)
    def repos = []

    def debug = config.get('debug', false)
    ServiceRegistry.instance.add(Logger, new Logger(this, debug))
    ILogger logger = ServiceRegistry.instance.get(Logger)

    def odsImageTag = config.odsImageTag
    if (!odsImageTag) {
        error "You must set 'odsImageTag' in the config map"
    }
    def versionedDevEnvsEnabled = config.get('versionedDevEnvs', false)
    def alwaysPullImage = !!config.get('alwaysPullImage', true)
    boolean startMROSlaveEarly = config.get('startOrchestrationSlaveOnInit', true)
    def startMROStage = startMROSlaveEarly ? MROPipelineUtil.PipelinePhases.INIT : null

    node ('master') {
        // Clean workspace from previous runs
        [
            PipelineUtil.ARTIFACTS_BASE_DIR,
            PipelineUtil.SONARQUBE_BASE_DIR,
            PipelineUtil.XUNIT_DOCUMENTS_BASE_DIR,
            MROPipelineUtil.REPOS_BASE_DIR,
        ].each { name ->
            logger.info("Cleaning workspace directory '${name}' from previous runs")
            Paths.get(env.WORKSPACE, name).toFile().deleteDir()
        }

        def scmBranches = scm.branches
        def branch = scmBranches[0]?.name
        if (branch && !branch.startsWith('*/')) {
            scmBranches = [[name: "*/${branch}"]]
        }

        // checkout local branch
        checkout([
            $class: 'GitSCM',
            branches: scmBranches,
            doGenerateSubmoduleConfigurations: scm.doGenerateSubmoduleConfigurations,
            extensions: [[$class: 'LocalBranch', localBranch: '**']],
            userRemoteConfigs: scm.userRemoteConfigs,
        ])

        def envs = Project.getBuildEnvironment(steps, debug, versionedDevEnvsEnabled)

        logger.startClocked('pod-template')
        withPodTemplate(odsImageTag, steps, alwaysPullImage) {
            logger.debugClocked('pod-template')
            withEnv (envs) {
                def result = executeWithMROSlaveBootstrap(
                    new InitStage(this, project, repos), startMROStage)
                if (result) {
                    project = result.project
                    repos = result.repos
                    if (!startMROStage) {
                        startMROStage = result.startMROSlave
                    }
                } else {
                    logger.info('Skip pipeline as no project/repos computed')
                    return
                }

                executeWithMROSlaveBootstrap(
                    new BuildStage(this, project, repos), startMROStage)

                executeWithMROSlaveBootstrap(
                    new DeployStage(this, project, repos), startMROStage)

                executeWithMROSlaveBootstrap(
                    new TestStage(this, project, repos), startMROStage)

                new ReleaseStage(this, project, repos).execute()

                new FinalizeStage(this, project, repos).execute()
            }
        }
    }
}

private withPodTemplate(String odsImageTag, IPipelineSteps steps, boolean alwaysPullImage, Closure block) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    def podLabel = "mro-jenkins-agent-${env.BUILD_NUMBER}"
    def odsNamespace = env.ODS_NAMESPACE ?: 'ods'
    if (!OpenShiftService.envExists(steps, odsNamespace)) {
        logger.info("Could not find ods namespace '${odsNamespace}' - defaulting to legacy namespace: 'cd'!\r" +
            "Please configure 'env.ODS_NAMESPACE' to point to the ODS Openshift namespace")
        odsNamespace = 'cd'
    }
    podTemplate(
        label: podLabel,
        cloud: 'openshift',
        containers: [
            containerTemplate(
                name: 'jnlp',
                image: "${env.DOCKER_REGISTRY}/${odsNamespace}/jenkins-slave-base:${odsImageTag}",
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
        def startTime = System.currentTimeMillis()
        logger.startClocked('ods-mro-pipeline')
        try {
            block()
        } finally {
            logger.infoClocked('**** ENDED orchestration pipeline ****')
        }
    }
}

@SuppressWarnings('GStringAsMapKey')
private Map executeWithMROSlaveBootstrap (Stage stage, String startMROStage) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    logger.debug("Stage to start mro slave: '${startMROStage ?: calculated}'" +
        " current: '${stage.STAGE_NAME}'")
    if (!startMROStage || !startMROStage.equalsIgnoreCase(stage.STAGE_NAME)) {
        return stage.execute()
    }
    Map result
    Map executors = [
        "${stage.STAGE_NAME}": {
            if (startMROStage == MROPipelineUtil.PipelinePhases.INIT) {
                result = stage.execute()
            } else {
                stage.execute()
            }
        },
        'boot mro slave': {
            def podLabel = "mro-jenkins-agent-${env.BUILD_NUMBER}"
            logger.debugClocked(podLabel)
            node (podLabel) {
                logger.debugClocked(podLabel)
            }
        },
    ]
    executors.failFast = true
    parallel (executors)
    return result
}

return this
