package org.ods.quickstarter

import org.ods.util.ILogger
import org.ods.util.Logger

abstract class Stage {

    protected def script
    protected def context
    protected Map config
    protected ILogger logger

    protected String STAGE_NAME = 'NOT SET'

    protected Stage(def script, IContext context, Map config) {
        this.script = script
        this.context = context
        this.config = config
        this.logger = new Logger(script, false)
    }

    def execute() {
        script.withStage(stageLabel(), context, logger) {
            return this.run()
        }
    }

    abstract protected run()

    protected String stageLabel() {
        STAGE_NAME
    }

}
