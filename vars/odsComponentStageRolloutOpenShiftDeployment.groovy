import org.ods.component.RolloutOpenShiftDeploymentStage
import org.ods.component.IContext

import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.services.ServiceRegistry

def call(IContext context, Map config = [:]) {
    return new RolloutOpenShiftDeploymentStage(
        this,
        context,
        config,
        ServiceRegistry.instance.get(OpenShiftService),
        ServiceRegistry.instance.get(JenkinsService)
    ).execute()
}
return this
