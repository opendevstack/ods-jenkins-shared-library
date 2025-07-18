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

                def testDir = "${steps.env.WORKSPACE}/${xunitDir}"
                logger.error("testDir: ${testDir}")
                def zipFileName = "xunit.zip"
//                def file = buildXunitZipFile(steps, testDir, zipFileName, logger)
//                def directory = "${context.getProjectId().toLowerCase()}/${repo}/${formattedDate}-${context.getBuildNumber()}/xunit"
//                nexusService.uploadTestReportToNexus(zipFileName, file, nexusRepository, directory)
//                logger.debug("Successfully uploaded xunit file to Nexus: ${nexusRepository}/${context.getProjectId().toLowerCase()}/${repo}/${formattedDate}-${context.getBuildNumber()}/xunit")

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

//private File buildXunitZipFile(def steps, def testDir, def zipFileName, def logger) {
//    logger.error("AMP X01")
//    if (!testDir || !steps.fileExists(testDir)) {
//        throw new IllegalArgumentException("Error: The test directory '${testDir}' does not exist.")
//    }
//    logger.error("AMP X02")
//
//    def zipFilePath=  Paths.get(testDir, zipFileName)
//    logger.error("AMP X03")
//    try {
//        steps.sh "cd ${testDir} && zip -r ${zipFileName} ."
//        logger.error("AMP X04")
//        def file = zipFilePath.toFile()
//        logger.error("AMP X05")
//        if (!file.exists() || file.length() == 0) {
//            throw new RuntimeException("Error: The ZIP file was not created correctly at '${zipFilePath}'.")
//        }
//        logger.error("AMP X06")
//        return file
//    } catch (Exception e) {
//        //logger.error("Error creating the xUnit ZIP file: ${e.message}")
//        throw e
//    }
//}

return this
