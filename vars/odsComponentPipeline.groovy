import org.ods.component.Context
import org.ods.component.Pipeline
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

def call(Map config, Closure body) {
    ServiceRegistry registry = ServiceRegistry.instance
    IPipelineSteps steps = registry.get(IPipelineSteps)
    if (!steps) {
        steps = new PipelineSteps(this)
        registry.add(IPipelineSteps, steps)
    }
    def debug = env.DEBUG
    if (debug != null) {
        config.debug = debug
    }

    config.debug = !!config.debug

    ServiceRegistry.instance.add(Logger, new Logger(this, debug))
    ILogger logger = ServiceRegistry.instance.get(Logger)

    def pipeline = new Pipeline(this, logger)
    String processId = "${env.JOB_NAME}/${env.BUILD_NUMBER}"

    try {
        pipeline.execute(config, body)
    } finally {
        if (env.MULTI_REPO_BUILD) {
            logger.debug('-- in RM mode, shutdown skipped --')
        }
        if (!env.MULTI_REPO_BUILD) {
            logger.warn('-- SHUTTING DOWN Component Pipeline (..) --')
            logger.resetStopwatch()
            try {
                // Upload Jenkins logs to Nexus
                uploadJenkinsLogToNexus(registry, logger)
                // use the jenkins INTERNAL cleanupHeap method - attention NOTHING can happen after this method!
                logger.debug("forceClean via jenkins internals....")
                new ClassLoaderCleaner().clean(logger, processId)
                // Force cleanup of the heap to release memory
                Method cleanupHeap = currentBuild.getRawBuild().getExecution().class.getDeclaredMethod("cleanUpHeap")
                cleanupHeap.setAccessible(true)
                cleanupHeap.invoke(currentBuild.getRawBuild().getExecution(), null)
            } catch (Exception e) {
                logger.debug("cleanupHeap err: ${e}")
            }
            logger = null
            ServiceRegistry.removeInstance()
            UnirestConfig.shutdown()
        }
    }
}

private void uploadJenkinsLogToNexus(ServiceRegistry registry, Logger logger) {
    def jenkinsService = registry.get(JenkinsService)
    def nexusService = getNexusService(registry)
    String text = jenkinsService.getCompletedBuildLogAsText()
    String repoName = "leva-documentation"
    String directory = getJenkinsLogsDirectory(repoName)
    logger.warn("Started upload Jenkins logs to Nexus directory: ${repoName}/${directory}")
    nexusService.uploadJenkinsLogsToNexus(text, repoName, directory)
    logger.warn("Successfully uploaded Jenkins logs to Nexus")
}

private String getJenkinsLogsDirectory(String repoName) {
    def context = new Context(null, config, logger)
    String repo = context.getRepoName().replace("${context.getProjectId()}-", "")
    def formattedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    return "${repoName}/${context.getProjectId().toLowerCase()}/${repo}/" +
        "${formattedDate}-${context.getBuildNumber()}/logs"
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
