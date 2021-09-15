import org.ods.component.InfrastructureStage
import org.ods.component.IContext

import org.ods.services.InfrastructureService
import org.ods.services.BitbucketService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger
import org.ods.util.PipelineSteps

@SuppressWarnings('AbcMetric')
def call(IContext context, Map config = [:]) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    // this is only for testing, because we need access to the script context :(
    if (!logger) {
        logger = new Logger(this, !!env.DEBUG)
    }

    ServiceRegistry registry = ServiceRegistry.instance
    PipelineSteps steps = registry.get(PipelineSteps)
    if (!steps) {
        steps = new PipelineSteps(this)
        registry.add(PipelineSteps, steps)
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

    String errorMessages = ''

    try {
        // TODO PRE CHECKS HERE
        new InfrastructureStage(this,
            context,
            config,
            infrastructureService,
            bitbucketService,
            logger
        ).execute()

        // else if (!enabledInCluster && !enabledInProject) {
        //     logger.warn("Skipping Aqua scan because is not enabled nor cluster nor project " +
        //         "in '${ScanWithAquaStage.AQUA_CONFIG_MAP_NAME}' ConfigMap " +
        //         "in ${config.imageLabels.JENKINS_MASTER_OPENSHIFT_BUILD_NAMESPACE} project")
        // } else if (enabledInCluster) {
        //     logger.warn("Skipping Aqua scan because is not enabled at project level " +
        //         "in '${ScanWithAquaStage.AQUA_CONFIG_MAP_NAME}' " +
        //         "ConfigMap in ${config.imageLabels.JENKINS_MASTER_OPENSHIFT_BUILD_NAMESPACE} project")
        // } else {
        //     logger.warn("Skipping Aqua scan because is not enabled at cluster level " +
        //         "in '${ScanWithAquaStage.AQUA_CONFIG_MAP_NAME}' " +
        //         "ConfigMap in ${config.imageLabels.JENKINS_MASTER_OPENSHIFT_BUILD_NAMESPACE} project")
        // }
    } catch (err) {
        logger.warn("Error with Infrastructure as Code due to: ${err}")
    }

}


return this
