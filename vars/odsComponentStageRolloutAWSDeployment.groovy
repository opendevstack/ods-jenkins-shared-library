import org.ods.component.RolloutAWSDeploymentStage
import org.ods.component.IContext

import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger

def call(IContext context, Map config = [:]) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    if (!logger) {
        logger = new Logger (this, !!env.DEBUG)
    }
    if (!context.environment) {
        logger.warn 'Skipping because of empty (target) environment ...'
        return
    }
    if (!config.awsEnvPath) {
        config.awsEnvPath = "./environments"
    }

    Map awsEnvironmentVars = readYaml(file: "${config.awsEnvPath}/${context.environment}.yml")
    return new RolloutAWSDeploymentStage(
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
