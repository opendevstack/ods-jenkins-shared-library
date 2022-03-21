package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger
import org.ods.orchestration.usecase.DocumentType

class ThrowingBaseStage {

    protected def script

    public final String STAGE_NAME = 'NOT SET'

    ThrowingBaseStage(def script) {
        this.script = script
    }

    def execute(Closure stages = null) {
        ILogger logger = ServiceRegistry.instance.get(Logger)
        script.stage(STAGE_NAME) {
            logger.infoClocked ("${STAGE_NAME}", '**** STARTING orchestration stage ****')
            logger.info ("${DocumentType.TIR}")
            try {
                return this.run(stages)
            } catch (e) {
                hudson.Functions.printThrowable(e)
                logger.warn("Error occured within the orchestration pipeline: ${e.message}")
                throw e
            } finally {
                logger.infoClocked ("${STAGE_NAME}", '**** ENDED orchestration stage ****')
            }
        }
    }
}