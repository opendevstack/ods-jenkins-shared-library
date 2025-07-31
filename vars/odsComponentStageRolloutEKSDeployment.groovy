import org.ods.component.RolloutOpenShiftDeploymentStage
import org.ods.component.IContext
import org.ods.component.EKSLoginStage

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

    def stage = new EKSLoginStage(
        this,
        context,
        config,
        logger
    )
    stage.execute()

    // ODS 3.x allowed to specify resourceName, which is no longer possible.
    // Fail if the option is still given to inform about this breaking change.
    if (config.resourceName) {
        error(
            "The config option 'resourceName' has been removed from odsComponentStageRolloutOpenShiftDeployment. " +
            "Instead, all resources with a common label are rolled out together. " +
            "If you need separate rollouts, consider specifying additional labels " +
            "and target those via the 'selector' config option."
        )
        return
    }
    return new RolloutOpenShiftDeploymentStage(
        this,
        context,
        config,
        ServiceRegistry.instance.get(OpenShiftService),
        ServiceRegistry.instance.get(JenkinsService),
        logger
    ).execute()
}
return this
