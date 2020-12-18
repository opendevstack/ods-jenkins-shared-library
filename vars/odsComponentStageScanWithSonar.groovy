import org.ods.component.ScanWithSonarStage
import org.ods.component.IContext

import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger

def call(IContext context, Map config = [:]) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    // this is only for testing, because we need access to the script context :(
    if (!logger) {
        logger = new Logger (this, !!env.DEBUG)
    }
    def stage = new ScanWithSonarStage(this, context, config, logger)
    stage.execute()
}
return this
