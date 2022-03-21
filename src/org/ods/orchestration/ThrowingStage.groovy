package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.util.Logger

class ThrowingStage  {

    public final String STAGE_NAME = 'Throwing Stage'
    protected script

    public ThrowingStage(def script) {
        this.script = script
    }

    def run () {
        ILogger logger = ServiceRegistry.instance.get(Logger)
        logger.logWithThrow('haha')
    }

}