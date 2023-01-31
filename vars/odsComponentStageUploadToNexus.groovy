import org.ods.component.IContext
import org.ods.component.UploadToNexusStage
import org.ods.services.NexusService
import org.ods.services.ServiceRegistry
import org.ods.util.ILogger
import org.ods.util.Logger

// https://help.sonatype.com/repomanager3/rest-and-integration-api/components-api#ComponentsAPI-UploadComponent
def call(IContext context, Map config = [:]) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    // this is only for testing, because we need access to the script context :(
    if (!logger) {
        logger = new Logger (this, !!env.DEBUG)
    }
    def stage = new UploadToNexusStage(
        this,
        context,
        config,
        ServiceRegistry.instance.get(NexusService),
        logger
    )
    return stage.execute()
}
return this
