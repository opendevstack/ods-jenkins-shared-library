package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger

class ThrowingBaseStage extends ThrowingBaseStage {

    //protected def script

    public final String STAGE_NAME = 'NOT SET'

    Stage(def script) {
        super(script)
    }

    def execute() {
        ILogger logger = ServiceRegistry.instance.get(Logger)
        script.stage(STAGE_NAME) {
            logger.infoClocked ("${STAGE_NAME}", '**** STARTING orchestration stage ****')
            try {
                return this.run()
            } catch (e) {
                def eThrow = e
                logger.warn("Error occured within the orchestration pipeline: ${e.message}")
                throw eThrow
            } finally {
                logger.infoClocked ("${STAGE_NAME}", '**** ENDED orchestration stage ****')
            }
        }
    }
}