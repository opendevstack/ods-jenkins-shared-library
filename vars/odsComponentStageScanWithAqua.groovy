import org.ods.component.ScanWithAquaStage
import org.ods.component.IContext
import org.ods.services.AquaRemoteCriticalVulnerabilityWithSolutionException
import org.ods.services.AquaService
import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
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
    AquaService aquaService = registry.get(AquaService)
    if (!aquaService) {
        aquaService = new AquaService(steps, logger)
        registry.add(AquaService, aquaService)
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
    OpenShiftService openShiftService = registry.get(OpenShiftService)
    if (!openShiftService) {
        openShiftService = new OpenShiftService(steps, logger)
        registry.add(OpenShiftService, openShiftService)
    }
    NexusService nexusService = registry.get(NexusService)
    if (!nexusService) {
        nexusService = new NexusService(context.nexusUrl, steps, context.credentialsId)
        registry.add(NexusService, nexusService)
    }

    Map configurationAquaCluster = [:]
    Map configurationAquaProject = [:]
    String errorMessages = ''
    String alertEmails = ''
    try {
        configurationAquaCluster = openShiftService.getConfigMapData(
            config.imageLabels.JENKINS_MASTER_OPENSHIFT_BUILD_NAMESPACE,
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME)
        // Addresses form Aqua advises mails.
        alertEmails = configurationAquaCluster['alertEmails']
        if (!alertEmails) {
            logger.info "Please provide the alert emails of the Aqua platform!"
        }
        // Check if exist project configration at cluster level
        def key = "projects." + context.projectId + ".enabled" // Not work "projects.${context.projectId}.enabled"
        if (configurationAquaCluster.containsKey(key)) {
            configurationAquaProject.put('enabled',
                configurationAquaCluster.get(key))
            logger.info "Parameter 'projects.${context.projectId}.enabled' at cluster level exists"
        } else {
            // If not exist key, is enabled
            configurationAquaProject.put('enabled', true)
            logger.info "Not parameter 'projects.${context.projectId}.enabled' at cluster level. Default enabled"
        }

        boolean enabledInCluster = Boolean.valueOf(configurationAquaCluster['enabled'].toString())
        boolean enabledInProject = Boolean.valueOf(configurationAquaProject['enabled'].toString())
        def inheritedConfig = [:]
        if (config.resourceName) {
            inheritedConfig.resourceName = config.resourceName
        }
        if (enabledInCluster && enabledInProject) {
            new ScanWithAquaStage(this,
                context,
                inheritedConfig,
                aquaService,
                bitbucketService,
                openShiftService,
                nexusService,
                logger,
                configurationAquaCluster,
                configurationAquaProject
            ).execute()
        }
        else if (!enabledInCluster && !enabledInProject) {
            logger.warn("Skipping Aqua scan because is not enabled nor cluster nor project " +
                "in '${ScanWithAquaStage.AQUA_CONFIG_MAP_NAME}' ConfigMap " +
                "in ${config.imageLabels.JENKINS_MASTER_OPENSHIFT_BUILD_NAMESPACE} project")
        } else if (enabledInCluster) {
            logger.warn("Skipping Aqua scan because is not enabled at project level " +
                "in '${ScanWithAquaStage.AQUA_CONFIG_MAP_NAME}' " +
                "ConfigMap in ${config.imageLabels.JENKINS_MASTER_OPENSHIFT_BUILD_NAMESPACE} project")
        } else {
            logger.warn("Skipping Aqua scan because is not enabled at cluster level " +
                "in '${ScanWithAquaStage.AQUA_CONFIG_MAP_NAME}' " +
                "ConfigMap in ${config.imageLabels.JENKINS_MASTER_OPENSHIFT_BUILD_NAMESPACE} project")
        }
    } catch (AquaRemoteCriticalVulnerabilityWithSolutionException e) {
        throw e
    } catch (err) {
        logger.warn("Error with Aqua due to: ${err}")
        errorMessages += "<li>Error with Aqua</li>"
    }
    notifyAqua(steps, context, alertEmails, errorMessages)
}

private void notifyAqua(PipelineSteps steps, IContext context, String recipients = '', String message = '') {
    String subject = "Build $context.componentId on project $context.projectId had not executed Aqua!"
    String body = "<p>$subject</p> " +
        "<p>URL : <a href=\"$context.buildUrl\">$context.buildUrl</a></p> <ul>$message</ul>"

    if (message) {
        steps.emailext(
            body: body, mimeType: 'text/html',
            replyTo: '$script.DEFAULT_REPLYTO', subject: subject,
            to: recipients
        )
    }
}

return this
