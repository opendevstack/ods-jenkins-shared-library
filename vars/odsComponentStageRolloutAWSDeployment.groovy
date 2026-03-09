import org.ods.component.RolloutAWSDeploymentStage
import org.ods.component.IContext
import org.ods.orchestration.util.MROPipelineUtil
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
    def metadata = readYaml(file: 'metadata.yml')
    def repoType = metadata.type
    if (repoType != MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_INFRA) {
        logger.warn "Skipping because of unsupported repository type '${repoType}' ..."
        logger.warn "Update repo type in metadata.yml to ods-infra."
        return
    }
    config.repoType = repoType
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
