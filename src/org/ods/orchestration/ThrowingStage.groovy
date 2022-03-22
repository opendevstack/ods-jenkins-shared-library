package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger
import org.ods.component.BuildOpenShiftImageOptions

class ThrowingStage extends ThrowingBaseStage {

    public final String STAGE_NAME = 'Throwing Stage'
    private final BuildOpenShiftImageOptions options

    static logStatic (ILogger logger, String message) {
        logger.debug(message)
    }

    public ThrowingStage(def script) {
        super (script)
        this.options = new BuildOpenShiftImageOptions([:])
    }

    protected def run (Closure stages = null) {
        ILogger logger = ServiceRegistry.instance.get(Logger)        
        if (stages) {
            stages(logger)
        }
        logger.logWithThrow('log and throwing from stage')
    }
}