import org.ods.orchestration.BuildStage
import org.ods.orchestration.DeployStage
import org.ods.orchestration.FinalizeStage
import org.ods.services.JenkinsService
import org.ods.services.NexusService
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
import org.ods.util.Logger
import org.ods.util.ILogger
import org.ods.component.Context
import org.ods.util.PipelineSteps
import org.ods.util.IPipelineSteps
import org.ods.util.UnirestConfig

import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@SuppressWarnings('AbcMetric')
def call(Map config) {
    String processId = "${env.JOB_NAME}/${env.BUILD_NUMBER}"
    UnirestConfig.init()
    IPipelineSteps steps = new PipelineSteps(this)

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
            uploadResourcesToNexus(config, steps, project, logger)
        }
    } catch (Exception e) {
        uploadResourcesToNexus(config, steps, project, logger)
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
            // Force cleanup of the heap to release memory
            Method cleanupHeap = currentBuild.getRawBuild().getExecution().class.getDeclaredMethod("cleanUpHeap")
            cleanupHeap.setAccessible(true)
            cleanupHeap.invoke(currentBuild.getRawBuild().getExecution(), null)
        } catch (Exception e) {
            logger.debug("cleanupHeap err: ${e}")
        }
    }
}

private void uploadTestReportToNexus(IPipelineSteps steps, Project project, Logger logger) {
    node('master') {
        NexusService nexusService = getNexusService(ServiceRegistry.instance)
        def xunitDir = "${PipelineUtil.XUNIT_DOCUMENTS_BASE_DIR}"
        def testDir = "${this.env.WORKSPACE}/${xunitDir}"

        if (!steps.fileExists(testDir)) {
            logger.warn("uploadTestReportToNexus - No xUnit test reports found, skipping upload")
            return
        }

        def now = new Date()
        final FORMATTED_DATE = now.format("yyyy-MM-dd_HH-mm-ss")
        def name = "xunit-${project.buildParams.version}-${env.BUILD_NUMBER}-${FORMATTED_DATE}.zip"
        Path zipFile = nexusService.buildXunitZipFile(steps, testDir, name)
        def directory = "${project.key.toLowerCase()}-${project.buildParams.version}/xunit"

        nexusService.storeArtifact(
            "leva-documentation",
            directory,
            name,
            Files.readAllBytes(zipFile),
            "application/zip"
        )
        logger.debug("Uploaded Test Reports ${name} to Nexus repository leva-documentation/${directory} ")
    }
}

private void uploadJenkinsLogToNexus(def steps, Project project, Logger logger) {
    NexusService nexusService = getNexusService(ServiceRegistry.instance)
    JenkinsService jenkinsService = ServiceRegistry.instance.get(JenkinsService)
    String text = jenkinsService.getCompletedBuildLogAsText()
    def repoName = project.services.nexus.repository.name
    def directory = "${project.key.toLowerCase()}-${project.buildParams.version}/logs"

    def now = new Date()
    final FORMATTED_DATE = now.format("yyyy-MM-dd_HH-mm-ss")

    String name = "jenkins-${project.buildParams.version}-${env.BUILD_NUMBER}-${FORMATTED_DATE}.log"
    if (!steps.currentBuild.result) {
        text += "STATUS: SUCCESS"
    } else {
        text += "STATUS ${steps.currentBuild.result}"
    }

    nexusService.storeArtifact(
        repoName,
        directory,
        name,
        text.getBytes(StandardCharsets.UTF_8),
        "application/text"
    )
    logger.debug("Uploaded Jenkins logs ${name} to Nexus repository ${repo-name}/${directory}")
}

private String getStartAgent(String startAgentStage, result) {
    def resStartAgentStage = startAgentStage

    if (!startAgentStage) {
        resStartAgentStage = result.startAgent
    }
    return resStartAgentStage
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

private void uploadResourcesToNexus(Map config, IPipelineSteps steps, Project project, ILogger logger) {
    try {
        // upload resources to nexus
        def registry = ServiceRegistry.instance
        NexusService nexusService = registry.get(NexusService)
        def context = new Context(null, config, logger)

        if (!nexusService) {
            nexusService = new NexusService(context.nexusUrl, steps, context.credentialsId)
            registry.add(NexusService, nexusService)
        }
        uploadTestReportToNexus(steps, project, logger)
        uploadJenkinsLogToNexus(steps, project, logger)
    } catch (Exception e) {
        logger.error("Error during upload of test report or Jenkins log: ${e.message}")
    }
}

private NexusService getNexusService(def registry) {
    NexusService nexusService = registry.get(NexusService)
    if (!nexusService) {
        nexusService = new NexusService(context.nexusUrl, steps, context.credentialsId)
        registry.add(NexusService, nexusService)
    }
    return nexusService
}

return this
