import org.ods.orchestration.BuildStage
import org.ods.orchestration.DeployStage
import org.ods.orchestration.FinalizeStage
import org.ods.orchestration.InitStage
import org.ods.orchestration.ReleaseStage
import org.ods.orchestration.TestStage
import org.ods.orchestration.usecase.OpenIssuesException
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PipelineUtil
import org.ods.orchestration.util.Project
import org.ods.services.GitService
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.ClassLoaderCleaner
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import org.ods.util.PipelineSteps
import org.ods.util.UnirestConfig

import java.lang.reflect.Method
import java.nio.file.Paths

@SuppressWarnings('AbcMetric')
def call(Map config) {
    String processId = "${env.JOB_NAME}/${env.BUILD_NUMBER}"
    UnirestConfig.init()
    def steps = new PipelineSteps(this)

    def debug = config.get('debug', false)
    ServiceRegistry.instance.add(Logger, new Logger(this, debug))
    ILogger logger = ServiceRegistry.instance.get(Logger)
    logger.dumpCurrentStopwatchSize()
    def git = new GitService(steps, logger)

    def odsImageTag = config.odsImageTag
    if (!odsImageTag) {
        error "You must set 'odsImageTag' in the config map"
    }
    def versionedDevEnvsEnabled = config.get('versionedDevEnvs', false)
    def alwaysPullImage = !!config.get('alwaysPullImage', true)
    boolean startAgentEarly = config.get('startOrchestrationAgentOnInit', true)
    def startAgentStage = startAgentEarly ? MROPipelineUtil.PipelinePhases.INIT : null

    resourceLimitMemory = config.get('mroAgentMemoryLimit', '1Gi')

    logger.debug("Start agent stage: ${startAgentStage}")
    Project project = new Project(steps, logger)
    def repos = []

    logger.startClocked('orchestration-master-node')
    try {
        node('master') {
            logger.debugClocked('orchestration-master-node')
            cleanWorkspace(logger)

            logger.startClocked('pipeline-git-releasemanager')
            checkOutLocalBranch(git, scm, logger)

            logger.startClocked('pod-template')
            def envs = Project.getBuildEnvironment(steps, debug, versionedDevEnvsEnabled)
            withPodTemplate(odsImageTag, steps, alwaysPullImage, resourceLimitMemory) {
                logger.debugClocked('pod-template')
                withEnv(envs) {
                    def result
                    def cannotContinueAsHasOpenIssuesInClosingRelease = false
                    try {
                        result = new InitStage(this, project, repos, startAgentStage).execute()
                    } catch (OpenIssuesException ex) {
                        cannotContinueAsHasOpenIssuesInClosingRelease = true
                    }
                    if (cannotContinueAsHasOpenIssuesInClosingRelease) {
                        logger.warn('Cannot continue as it has open issues in the release.')
                        return
                    }
                    if (result) {
                        project = result.project
                        repos = result.repos
                        startAgentStage = getStartAgent(startAgentStage, result)
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
    } finally {
        logger.resetStopwatch()
        project.clear()
        ServiceRegistry.removeInstance()
        UnirestConfig.shutdown()
        project = null
        git = null
        repos = null
        steps = null
        try {
            new ClassLoaderCleaner().clean(logger, processId)
            // use the jenkins INTERNAL cleanupHeap method - attention NOTHING can happen after this method!
            logger.debug("forceClean via jenkins internals....")
            Method cleanupHeap = currentBuild.getRawBuild().getExecution().class.getDeclaredMethod("cleanUpHeap")
            cleanupHeap.setAccessible(true)
            cleanupHeap.invoke(currentBuild.getRawBuild().getExecution(), null)
        } catch (Exception e) {
            logger.debug("cleanupHeap err: ${e}")
        }
    }
}

private String getStartAgent(String startAgentStage, result) {
    if (!startAgentStage) {
        startAgentStage = result.startAgent
    }
    return startAgentStage
}

// Clean workspace from previous runs
private Object cleanWorkspace(logger) {
    return [
        PipelineUtil.ARTIFACTS_BASE_DIR,
        PipelineUtil.SONARQUBE_BASE_DIR,
        PipelineUtil.XUNIT_DOCUMENTS_BASE_DIR,
        MROPipelineUtil.REPOS_BASE_DIR,
    ].each { name ->
        logger.debug("Cleaning workspace directory '${name}' from previous runs")
        Paths.get(env.WORKSPACE, name).toFile().deleteDir()
    }
}

private void checkOutLocalBranch(GitService git, scm, ILogger logger) {
    def scmBranches = scm.branches
    def branch = scmBranches[0]?.name
    if (branch && !branch.startsWith('*/')) {
        scmBranches = [[name: "*/${branch}"]]
    }
    git.checkout(
        scmBranches,
        [[$class: 'LocalBranch', localBranch: '**']],
        scm.userRemoteConfigs,
        scm.doGenerateSubmoduleConfigurations
    )
    logger.debugClocked('pipeline-git-releasemanager')
}

@SuppressWarnings('GStringExpressionWithinString')
private withPodTemplate(String odsImageTag, IPipelineSteps steps, boolean alwaysPullImage,
    String mroAgentLimit, Closure block) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    def dockerRegistry = steps.env.DOCKER_REGISTRY ?: 'image-registry.openshift-image-registry.svc:5000'
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
                resourceLimitMemory: "${mroAgentLimit}",
                resourceRequestCpu: '200m',
                resourceLimitCpu: '1',
                alwaysPullImage: "${alwaysPullImage}",
                args: '${computer.jnlpmac} ${computer.name}',
                envVars: []
            )
        ],
        volumes: [],
        serviceAccount: 'jenkins',
        annotations: [
            podAnnotation(
                key: 'cluster-autoscaler.kubernetes.io/safe-to-evict', value: 'false'
            )
        ],
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
