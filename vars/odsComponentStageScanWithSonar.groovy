import org.ods.component.ScanWithSonarStage
import org.ods.component.IContext

import org.ods.services.BitbucketService
import org.ods.services.SonarQubeService
import org.ods.services.ServiceRegistry

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
    def stage = new ScanWithSonarStage(
        this,
        context,
        config,
        bitbucketService,
        sonarQubeService
    )
    stage.execute()
}
return this
