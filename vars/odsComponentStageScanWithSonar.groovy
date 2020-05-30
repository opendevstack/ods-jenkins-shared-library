import org.ods.component.ScanWithSonarStage
import org.ods.component.IContext

import org.ods.services.BitbucketService
import org.ods.services.SonarQubeService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger

def call(IContext context, Map config = [:]) {
    def bitbucketService = ServiceRegistry.instance.get(BitbucketService)
    if (!bitbucketService) {
        bitbucketService = new BitbucketService(
            this,
            context.bitbucketUrl,
            context.projectId,
            context.credentialsId
        )
    }
    def sonarQubeService = ServiceRegistry.instance.get(SonarQubeService)
    if (!sonarQubeService) {
        sonarQubeService = new SonarQubeService(
            this,
            'SonarServerConfig'
        )
    }
    ILogger logger = ServiceRegistry.instance.get(Logger)
    // this is only for testing, because we need access to the script context :(
    if (!logger) {
        logger = new Logger (this, !!env.DEBUG)
    }
    def stage = new ScanWithSonarStage(
        this,
        context,
        config,
        bitbucketService,
        sonarQubeService,
        logger
    )
    stage.execute()
}
return this
