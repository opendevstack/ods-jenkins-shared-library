package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.util.Logger

public final String STAGE_NAME = 'Throwing Stage'

public ThrowingStage (def script) {

    public void run () {
        ILogger logger = ServiceRegistry.instance.get(Logger)
        logger.logWithThrow('haha')
    }

}