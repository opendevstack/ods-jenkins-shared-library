import org.ods.services.ServiceRegistry

import org.ods.util.Logger
import org.ods.util.ILogger

import org.ods.orchestration.ThrowingStage

//import org.ods.util.UnirestConfig

def call (Map config) {
    //UnirestConfig.init()
    def debug = config.get('debug', false)
    ServiceRegistry.instance.add(Logger, new Logger(this, debug))
    ILogger logger = ServiceRegistry.instance.get(Logger)
    //logger.debug("here")
    try {
        node ('master') {
            new ThrowingStage(this).execute()
        }
    } finally {
        // clear it all ...
        logger.resetStopwatch()
        ServiceRegistry.removeInstance()
    }
}
