import org.ods.component.Pipeline
import org.ods.util.Logger

import org.ods.services.ServiceRegistry
import org.ods.util.ClassLoaderCleaner
import org.ods.util.UnirestConfig
import java.lang.reflect.Method
import java.nio.file.Paths

def call(Map config, Closure body) {
    def debug = env.DEBUG
    if (debug != null) {
        config.debug = debug
    }

    config.debug = !!config.debug

    def logger = new Logger(this, config.debug)
    def pipeline = new Pipeline(this, logger)
    String processId = "${env.JOB_NAME}/${env.BUILD_NUMBER}"
    try {
        pipeline.execute(config, body)
    }  finally {
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
}

return this
