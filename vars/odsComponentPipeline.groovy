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
                uploadJenkinsLogsToNexus(registry, config, logger, steps)
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
            ServiceRegistry.removeInstance()
            UnirestConfig.shutdown()
        }
    }
}

private void uploadJenkinsLogsToNexus(ServiceRegistry registry, Map config, Logger logger, IPipelineSteps steps) {
    String defaultNexusRepository = "leva-documentation"

    NexusService nexusService = registry.get(NexusService)
    def context = new Context(null, config, logger)

    String repo = context.getRepoName().replace("${context.getProjectId()}-", "")

    def formattedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    if (!nexusService) {
        nexusService = new NexusService(context.nexusUrl, steps, context.credentialsId)
        registry.add(NexusService, nexusService)
    }
    JenkinsService jenkinsService = registry.get(JenkinsService)
    String text = jenkinsService.currentBuildLogAsText
    String directory = "${defaultNexusRepository}/${context.getProjectId().toLowerCase()}/${repo}/" +
        "${formattedDate}-${context.getBuildNumber()}/logs"
    nexusService.storeArtifact(
        defaultNexusRepository,
        directory,
        "jenkins_logs",
        text.bytes,
        "application/text"
    )
    logger.debug("Successfully uploaded Jenkins logs to Nexus: ${directory}")
}

return this
