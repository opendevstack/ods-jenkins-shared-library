import org.ods.services.ServiceRegistry

import org.ods.util.Logger
import org.ods.util.ILogger

import org.ods.orchestration.ThrowingStage

import org.ods.util.UnirestConfig

def call (Map config, Closure stages = null) {
    UnirestConfig.init()
    def debug = config.get('debug', false)
    ServiceRegistry.instance.add(Logger, new Logger(this, debug))
    ILogger logger = ServiceRegistry.instance.get(Logger)
    logger.debug('here')
    try {
        node ('master') {
            writeFile(
                file: "dummy.groovy",
                text: "@Library('ods-jenkins-shared-library@fix/extract_enums') _ \n echo 'dynamic'")
            )
            load ('dummy.groovy')
            new ThrowingStage(this).execute(stages)
        }
    } finally {
        // clear it all ...
        logger.resetStopwatch()
        ServiceRegistry.removeInstance()
    }
}
