import org.ods.component.BuildOpenShiftImageStage
import org.ods.component.IContext

import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry

def call(IContext context, Map config = [:]) {
    def stage = new BuildOpenShiftImageStage(
        this,
        context,
        config,
        ServiceRegistry.instance.get(OpenShiftService)
    )
    stage.execute()
}
return this
