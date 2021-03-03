import org.ods.component.ScanWithAquaStage
import org.ods.component.IContext

import org.ods.services.AquaService
import org.ods.services.BitbucketService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger

def call(IContext context, Map config = [:]) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    // this is only for testing, because we need access to the script context :(
    if (!logger) {
        logger = new Logger(this, !!env.DEBUG)
    }
    def aquaService = ServiceRegistry.instance.get(AquaService)
    if (!aquaService) {
        aquaService = new AquaService(this, 'aqua-report.txt', logger)
        ServiceRegistry.instance.add(AquaService, aquaService)
    }
    def bitbucketService = ServiceRegistry.instance.get(BitbucketService)
    if (!bitbucketService) {
        bitbucketService = new BitbucketService(
            this,
            context.bitbucketUrl,
            context.projectId,
            context.credentialsId,
            logger
        )
    }
    def stage = new ScanWithAquaStage(this, context, config, aquaService, bitbucketService,
        logger)
    stage.execute()
}

return this
