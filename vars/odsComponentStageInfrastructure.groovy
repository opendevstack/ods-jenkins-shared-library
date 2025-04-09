import org.ods.component.InfrastructureStage
import org.ods.component.IContext

import org.ods.services.InfrastructureService
import org.ods.services.BitbucketService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger
import org.ods.util.PipelineSteps
import org.ods.util.IPipelineSteps
import org.ods.util.GitCredentialStore

@SuppressWarnings('AbcMetric')
def call(IContext context, Map config = [:]) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    // this is only for testing, because we need access to the script context :(
    if (!logger) {
        logger = new Logger(this, !!env.DEBUG)
    }

    ServiceRegistry registry = ServiceRegistry.instance
    IPipelineSteps steps = registry.get(IPipelineSteps)
    if (!steps) {
        steps = new PipelineSteps(this)
        registry.add(IPipelineSteps, steps)
    }
    InfrastructureService infrastructureService = registry.get(InfrastructureService)
    if (!infrastructureService) {
        infrastructureService = new InfrastructureService(steps, logger)
        registry.add(InfrastructureService, infrastructureService)
    }
    BitbucketService bitbucketService = registry.get(BitbucketService)
    if (!bitbucketService) {
        bitbucketService = new BitbucketService(
            this,
            context.bitbucketUrl,
            context.projectId,
            context.credentialsId,
            logger
        )
        registry.add(BitbucketService, bitbucketService)
    }

    bitbucketService.withTokenCredentials { String username, String pw ->
        GitCredentialStore.configureAndStore(this, context.bitbucketUrl, username, pw)
    }

    if (!infrastructureService.CLOUD_PROVIDERS.contains(config.cloudProvider)) {
        error "Cloud provider ${config.cloudProvider} not in supported list: ${infrastructureService.CLOUD_PROVIDERS}"
    }
    if (!config.resourceName) {
        config.resourceName = context.componentId
    }
    if (!config.envPath) {
        config.envPath = "./environments"
    }
    // TODO: once we have other cloud providers move some initialisation logic to service
    def environmentVarsTesting  = readYaml(file: "${config.envPath}/testing.yml")
    def environmentVars = null
    def tfVars = null

    if (!!context.environment) {
        environmentVars = readYaml(file: "${config.envPath}/${context.environment}.yml")

        // handle environment specific variables
        // copy json from ${config.envPath}/${context.environment}.auto.tfvars.json to /
        if (fileExists("${config.envPath}/${context.environment}.json")) {
            withEnv(["VARIABLESFILE=${config.envPath}/${context.environment}.json"])
            {
                def statusVarEnv = sh(
                    script: '''cp $VARIABLESFILE env.auto.tfvars.json && echo "Variables file $VARIABLESFILE" ''',
                    returnStatus: true
                )
                if (statusVarEnv != 0) {
                    error "Can not copy ${config.envPath}/${context.environment}.json file"
                }
            }
        }

        def status = infrastructureService.runMake("create-tfvars")
        if (status != 0) {
            error "Creation of tfvars failed!"
        }
        tfVars = readJSON(file: "terraform.tfvars.json")
    } else {
        logger.info("No deployment target set. Only Testing Sandbox stage will be executed.")
    }

    new InfrastructureStage(this,
        context,
        config,
        infrastructureService,
        logger,
        environmentVars,
        environmentVarsTesting,
        tfVars
    ).execute()
}

return this
