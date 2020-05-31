import org.ods.component.ScanWithSnykStage
import org.ods.component.IContext

import org.ods.services.SnykService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger

def call(IContext context, Map config = [:]) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    // this is only for testing, because we need access to the script context :(
    if (!logger) {
        logger = new Logger (this, !!env.DEBUG)
    }
    def snykService = ServiceRegistry.instance.get(SnykService)
    if (!snykService) {
        snykService = new SnykService(this, 'snyk-report.txt')
    }
    def stage = new ScanWithSnykStage(this, context, config, snykService,
        logger)
    stage.execute()
}
return this
