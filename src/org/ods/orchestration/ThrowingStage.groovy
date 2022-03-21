package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger

class ThrowingStage extends ThrowingBaseStage {

    public final String STAGE_NAME = 'Throwing Stage'

    static staticLog (ILogger logger, String message) {
        logger.debug(message)
    }

    public ThrowingStage(def script) {
        super (script)
    }

    def run (Closure stages = null) {
        ILogger logger = ServiceRegistry.instance.get(Logger)
        if (stages) {
            stages(logger)
        }
        logger.logWithThrow('log and throwing from stage')
    }

}