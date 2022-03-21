package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger

class ThrowingBaseStage {

    protected def script

    public final String STAGE_NAME = 'NOT SET'

    ThrowingBaseStage(def script) {
        this.script = script
    }

    def execute() {
        ILogger logger = ServiceRegistry.instance.get(Logger)
        script.stage(STAGE_NAME) {
            logger.info ('**** STARTING orchestration stage ****')
            try {
                return this.run()
            } catch (e) {
                logger.warn("Error occured within the orchestration pipeline: ${e.message}")
                throw e
            } finally {
                logger.infoClocked ('**** ENDED orchestration stage ****')
            }
        }
    }
}