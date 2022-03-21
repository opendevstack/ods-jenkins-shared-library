package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger

class ThrowingStage extends ThrowingBaseStage {

    public final String STAGE_NAME = 'Throwing Stage'

    public ThrowingStage(def script) {
        super (script)
    }

    def run (Closure stages = null) {
        ILogger logger = ServiceRegistry.instance.get(Logger)
        if (stages) {
            stages(script)
        }
        logger.logWithThrow('haha')
    }

}