import org.ods.component.ScanWithSonarStage
import org.ods.component.IContext
import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.services.SonarQubeService
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger
import org.ods.util.PipelineSteps
import org.ods.util.IPipelineSteps

def call(IContext context, Map config = [:]) {
    def (logger, steps, bitbucketService, sonarQubeService, nexusService, openShiftService) =
        initializeServices(context)
    initializeConfig(config)
    Map configurationSonarCluster = [:]
    Map configurationSonarProject = [:]
    String errorMessages = ''
    String alertEmails = ''
    try {
        (configurationSonarCluster, configurationSonarProject, alertEmails) =
            fetchSonarConfig(openShiftService, context, config, logger)
        errorMessages = handleSonarScan(
            configurationSonarCluster,
            configurationSonarProject,
            context,
            config,
            [
                bitbucketService : bitbucketService,
                sonarQubeService : sonarQubeService,
                nexusService     : nexusService,
                logger           : logger
            ]
        )
    } catch (Exception e) {
        logger.warn("Error with SonarQube scan due to: ${e.message}")
        logger.debug("Stacktrace: ${e.getStackTrace().join('\n')}")
        errorMessages += "<li>Error with SonarQube scan: ${e.message}</li>"
    }
    notifySonar(steps, context, alertEmails, errorMessages)
}

private List initializeServices(IContext context) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    if (!logger) {
        logger = new Logger(this, !!env.DEBUG)
    }
    ServiceRegistry registry = ServiceRegistry.instance
    IPipelineSteps steps = registry.get(IPipelineSteps)
    if (!steps) {
        steps = new PipelineSteps(this)
        registry.add(IPipelineSteps, steps)
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
    SonarQubeService sonarQubeService = registry.get(SonarQubeService)
    if (!sonarQubeService) {
        sonarQubeService = new SonarQubeService(
            this,
            logger,
            'SonarServerConfig'
        )
        registry.add(SonarQubeService, sonarQubeService)
    }
    NexusService nexusService = registry.get(NexusService)
    if (!nexusService) {
        nexusService = new NexusService(context.nexusUrl, steps, context.credentialsId)
        registry.add(NexusService, nexusService)
    }
    OpenShiftService openShiftService = registry.get(OpenShiftService)
    if (!openShiftService) {
        openShiftService = new OpenShiftService(steps, logger)
        registry.add(OpenShiftService, openShiftService)
    }
    return [logger, steps, bitbucketService, sonarQubeService, nexusService, openShiftService]
}

private void initializeConfig(Map config) {
    if (!config.imageLabels) {
        config.imageLabels = [:]
    }
    if (!config.imageLabels.OPENSHIFT_BUILD_NAMESPACE) {
        config.imageLabels.OPENSHIFT_BUILD_NAMESPACE = env.OPENSHIFT_BUILD_NAMESPACE
    }
}

private List fetchSonarConfig(OpenShiftService openShiftService, IContext context, Map config, ILogger logger) {
    Map configurationSonarCluster = openShiftService.getConfigMapData(
        config.imageLabels.OPENSHIFT_BUILD_NAMESPACE,
        ScanWithSonarStage.SONAR_CONFIG_MAP_NAME
    )
    String alertEmails = configurationSonarCluster['alertEmails']
    Map configurationSonarProject = [:]
    def key = "projects." + context.projectId + ".enabled"
    if (configurationSonarCluster.containsKey(key)) {
        configurationSonarProject.put('enabled', configurationSonarCluster.get(key))
        logger.info "Parameter 'projects.${context.projectId}.enabled' at cluster level exists"
    } else {
        configurationSonarProject.put('enabled', true)
        logger.info "Not parameter 'projects.${context.projectId}.enabled' at cluster level. Default enabled"
    }
    return [configurationSonarCluster, configurationSonarProject, alertEmails]
}

private String handleSonarScan(
    Map configurationSonarCluster,
    Map configurationSonarProject,
    IContext context,
    Map config,
    Map services // expects keys: bitbucketService, sonarQubeService, nexusService, logger
) {
    String errorMessages = ''
    boolean enabledInCluster = Boolean.valueOf(configurationSonarCluster['enabled']?.toString() ?: "true")
    boolean enabledInProject = Boolean.valueOf(configurationSonarProject['enabled']?.toString() ?: "true")
    if (configurationSonarCluster['nexusRepository']) {
        config.sonarQubeNexusRepository = configurationSonarCluster['nexusRepository']
    }
    if (enabledInCluster && enabledInProject) {
        new ScanWithSonarStage(
            this,
            context,
            config,
            services.bitbucketService,
            services.sonarQubeService,
            services.nexusService,
            services.logger,
            configurationSonarCluster,
            configurationSonarProject
        ).execute()
    } else if (!enabledInCluster && !enabledInProject) {
        services.logger.warn("Skipping SonarQube scan because is not enabled nor cluster nor project " +
            "in '${ScanWithSonarStage.SONAR_CONFIG_MAP_NAME}' ConfigMap " +
            "in ${config.imageLabels.OPENSHIFT_BUILD_NAMESPACE} project")
        errorMessages += "<li>SonarQube scan not enabled at cluster nor project level</li>"
    } else if (enabledInCluster) {
        services.logger.warn("Skipping SonarQube scan because is not enabled at project level " +
            "in '${ScanWithSonarStage.SONAR_CONFIG_MAP_NAME}' " +
            "ConfigMap in ${config.imageLabels.OPENSHIFT_BUILD_NAMESPACE} project")
        errorMessages += "<li>SonarQube scan not enabled at project level</li>"
    } else {
        services.logger.warn("Skipping SonarQube scan because is not enabled at cluster level " +
            "in '${ScanWithSonarStage.SONAR_CONFIG_MAP_NAME}' " +
            "ConfigMap in ${config.imageLabels.OPENSHIFT_BUILD_NAMESPACE} project")
        errorMessages += "<li>SonarQube scan not enabled at cluster level</li>"
    }
    return errorMessages
}

private void notifySonar(IPipelineSteps steps, IContext context, String recipients = '', String message = '') {
    String subject = "Build $context.componentId on project $context.projectId had not executed SonarQube!"
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
return this
