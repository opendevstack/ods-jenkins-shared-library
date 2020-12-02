import org.ods.component.IContext

import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger

def call(IContext context, Map config = [:], Closure block) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    // this is only for testing, because we need access to the script context :(
    if (!logger) {
        logger = new Logger (this, !!env.DEBUG)
    }
    logger.warn(
        "odsComponentStageImportOpenShiftImageOrElse has been replaced " +
        "by odsComponentFindOpenShiftImageOrElse, please update your Jenkinsfile."
    )
    odsComponentFindOpenShiftImageOrElse(context, config, block)
}

return this
