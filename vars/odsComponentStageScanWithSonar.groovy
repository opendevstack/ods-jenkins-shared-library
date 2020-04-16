import org.ods.component.ScanWithSonarStage
import org.ods.component.IContext

import org.ods.services.SonarQubeService
import org.ods.services.ServiceRegistry

def call(IContext context, Map config = [:]) {
    def stage = new ScanWithSonarStage(
        this,
        context,
        config,
        ServiceRegistry.instance.get(SonarQubeService)
    )
    stage.execute()
}
return this
