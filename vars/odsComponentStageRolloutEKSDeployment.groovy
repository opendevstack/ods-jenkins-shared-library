import org.ods.component.RolloutEKSDeploymentStage
import org.ods.component.IContext

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
    if (!context.environment) {
        logger.warn 'Skipping because of empty (target) environment ...'
        return
    }
    if (!config.envPath) {
        config.envPath = "./environments"
    }
    if (!config.eks) {
        config.eks = true
    }

    Map awsEnvironmentVars = readYaml(file: "${config.envPath}/${context.environment}.yml")
    return new RolloutEKSDeploymentStage(
        this,
        context,
        config,
        ServiceRegistry.instance.get(OpenShiftService),
        ServiceRegistry.instance.get(JenkinsService),
        awsEnvironmentVars,
        logger
    ).execute()     
}
return this
