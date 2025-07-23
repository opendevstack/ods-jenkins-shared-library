import org.ods.component.Context
import org.ods.component.Pipeline
import org.ods.orchestration.util.PipelineUtil
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
    final String PROCESS_ID = "${env.JOB_NAME}/${env.BUILD_NUMBER}"

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
                NexusService nexusService = registry.get(NexusService)
                def context = new Context(null, config, logger)
                String repo = context.getRepoName().replace("${context.getProjectId()}-", "")

                if (!nexusService) {
                    nexusService = new NexusService(context.nexusUrl, steps, context.credentialsId)
                    registry.add(NexusService, nexusService)
                }

                uploadTestReportToNexus(steps, nexusService, context, repo, logger)
                uploadJenkinsLogToNexus(registry, nexusService, context, repo, logger)

                new ClassLoaderCleaner().clean(logger, PROCESS_ID)
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

private void uploadJenkinsLogToNexus(ServiceRegistry registry, NexusService nexusService, Context context, String repo,
                                     Logger logger) {
    final FORMATTED_DATE = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    JenkinsService jenkinsService = registry.get(JenkinsService)
    def text = jenkinsService.getCompletedBuildLogAsText()
    IPipelineSteps steps = new PipelineSteps(this)

    if (steps.currentBuild.result) {
        text += "STATUS ${steps.currentBuild.result}"
    } else {
        text += "STATUS: SUCCESS"
    }
    nexusService.storeArtifact(
        "leva-documentation",
        "${context.getProjectId().toLowerCase()}/${repo}/" +
            "${formattedDate}-${context.getBuildNumber()}/logs",
        "jenkins.log",
        text.bytes,
        "application/text"
    )
    logger.debug("Successfully uploaded Jenkins logs to Nexus: ${nexusRepository}/" +
        "${context.getProjectId().toLowerCase()}/${repo}/${FORMATTED_DATE}-${context.getBuildNumber()}/logs")
}

private void uploadTestReportToNexus(IPipelineSteps steps, NexusService nexusService, def context, String repo,
                                     def logger) {
    node {
        final FORMATTED_DATE = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        final def XUNIT_DIR = "${PipelineUtil.XUNIT_DOCUMENTS_BASE_DIR}"
        final def TEST_DIR = "${steps.env.WORKSPACE}/${XUNIT_DIR}"
        logger.error("TEST_DIR: ${TEST_DIR}")
        def zipFileName = "xunit.zip"
        if (!TEST_DIR || !steps.fileExists(TEST_DIR)) {
            throw new IllegalArgumentException("Error: The test directory '${TEST_DIR}' does not exist.")
        }
        def zipFile = nexusService.buildXunitZipFile(steps, TEST_DIR, zipFileName)
        def directory = "${context.getProjectId().toLowerCase()}/${repo}/" +
            "${FORMATTED_DATE}-${context.getBuildNumber()}/xunit"
        nexusService.uploadTestReportToNexus(zipFileName, zipFile, "leva-documentation", directory)
    }
}

return this
