import org.ods.component.BuildOpenShiftImageStage
import org.ods.component.IContext

import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.services.ServiceRegistry

def call(IContext context, Map config = [:]) {
    def stage = new BuildOpenShiftImageStage(
        this,
        context,
        config,
        ServiceRegistry.instance.get(OpenShiftService),
        ServiceRegistry.instance.get(JenkinsService)
    )
    return stage.execute()
}
return this
