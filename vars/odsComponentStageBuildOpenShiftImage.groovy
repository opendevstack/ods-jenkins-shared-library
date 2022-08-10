import org.ods.component.BuildOpenShiftImageStage
import org.ods.component.IContext

import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger

def call(IContext context, Map config = [:]) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    // this is only for testing, because we need access to the script context :(
    if (!logger) {
        logger = new Logger (this, !!env.DEBUG)
    }
    def stage = new BuildOpenShiftImageStage(
        this,
        context,
        config,
        ServiceRegistry.instance.get(OpenShiftService),
        ServiceRegistry.instance.get(JenkinsService),
        logger
    )
    def info = stage.execute()

    // Call to aqua stage always after the image creation
    odsComponentStageScanWithAqua(context, config)

    return info
}
return this
