import org.ods.component.Context
import org.ods.component.Pipeline
import org.ods.orchestration.util.Project
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.Logger

import org.ods.services.ServiceRegistry
import org.ods.util.ClassLoaderCleaner
import org.ods.util.PipelineSteps
import org.ods.util.UnirestConfig
import java.lang.reflect.Method
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

def call(Map config, Closure body) {

    def debug = env.DEBUG
    if (debug != null) {
        config.debug = debug
    }

    config.debug = !!config.debug

    def logger = new Logger(this, config.debug)
    def pipeline = new Pipeline(this, logger)
    String processId = "${env.JOB_NAME}/${env.BUILD_NUMBER}"

    def result
    try {
        result = pipeline.execute(config, body)
        uploadJenkinsLogToNexus(config, logger)
    } finally {
        if (env.MULTI_REPO_BUILD) {
            logger.debug('-- in RM mode, shutdown skipped --')
        }
        if (!env.MULTI_REPO_BUILD) {
            logger.warn('-- SHUTTING DOWN Component Pipeline (..) --')
            logger.resetStopwatch()
            ServiceRegistry.removeInstance()
            UnirestConfig.shutdown()
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
            logger = null
        }
    }
    return result
}

private void uploadJenkinsLogToNexus(Map config, Logger logger) {
    ServiceRegistry registry = ServiceRegistry.instance
    IPipelineSteps steps = registry.get(IPipelineSteps)
    if (!steps) {
        steps = new PipelineSteps(this)
        registry.add(IPipelineSteps, steps)
    }

    NexusService nexusService = registry.get(NexusService)
    def context = new Context(null, config, logger)
    String repo = context.getRepoName().replace("${context.getProjectId()}-", "")

    if (!nexusService) {
        nexusService = new NexusService(context.nexusUrl, steps, context.credentialsId)
        registry.add(NexusService, nexusService)
    }
    def now = new Date()
    final FORMATTED_DATE = now.format("yyyy-MM-dd-HH-mm-ss")
    JenkinsService jenkinsService = registry.get(JenkinsService)
    def text = jenkinsService.getCompletedBuildLogAsText()

    if (steps.currentBuild.result) {
        text += "STATUS ${steps.currentBuild.result}"
    } else {
        text += "STATUS: SUCCESS"
    }

    nexusService.storeArtifact(
        "leva-documentation",
        "${context.getProjectId().toLowerCase()}/${repo}/" +
            "${context.buildTime.format('yyyy-MM-dd_mm-ss')}-${context.buildNumber}/logs",
        "jenkins.log",
        text.bytes,
        "application/text"
    )
    logger.debug("Successfully uploaded Jenkins logs to Nexus: leva-documentation/" +
        "${context.getProjectId().toLowerCase()}/${repo}/${FORMATTED_DATE}-${context.getBuildNumber()}/logs")
}
