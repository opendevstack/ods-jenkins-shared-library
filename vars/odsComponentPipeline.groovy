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
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter

def call(Map config, Closure body) {
    String nexusRepository = "leva-documentation"

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
                NexusService nexusService = registry.get(NexusService)
                def context = new Context(null, config, logger)

                String repo = context.getRepoName().replace("${context.getProjectId()}-", "")

                def formattedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                if (!nexusService) {
                    nexusService = new NexusService(context.nexusUrl, steps, context.credentialsId)
                    registry.add(NexusService, nexusService)
                }

                node {
                    def xunitDir = "${PipelineUtil.XUNIT_DOCUMENTS_BASE_DIR}"
                    def testDir = "${steps.env.WORKSPACE}/${xunitDir}"
                    logger.error("testDir: ${testDir}")
                    def zipFileName = "xunit.zip"
                    if (!testDir || !steps.fileExists(testDir)) {
                        throw new IllegalArgumentException("Error: The test directory '${testDir}' does not exist.")
                    }
                    def zipFile = nexusService.buildXunitZipFile(steps, testDir, zipFileName)
                    def directory = "${context.getProjectId().toLowerCase()}/${repo}/${formattedDate}-${context.getBuildNumber()}/xunit"
                    nexusService.uploadTestReportToNexus(zipFileName, zipFile, "leva-documentation", directory)
                }

                JenkinsService jenkinsService = registry.get(JenkinsService)
                String text = jenkinsService.getCompletedBuildLogAsText()
                nexusService.storeArtifact(
                    nexusRepository,
                    "${context.getProjectId().toLowerCase()}/${repo}/${formattedDate}-${context.getBuildNumber()}/logs",
                    "jenkins.log",
                    text.bytes,
                    "application/text"
                )
                logger.debug("Successfully uploaded Jenkins logs to Nexus: ${nexusRepository}/${context.getProjectId().toLowerCase()}/${repo}/${formattedDate}-${context.getBuildNumber()}/logs")

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

return this
