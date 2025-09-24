package org.ods.component

import org.ods.services.SonarQubeService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.ILogger

class AutomaticSonarScanner implements Serializable {

    private final def script
    private final def context
    private final Map config
    private final def bitbucketService
    private final ILogger logger

    AutomaticSonarScanner(def script, def context, Map config, def bitbucketService, ILogger logger) {
        this.script = script
        this.context = context
        this.config = config
        this.bitbucketService = bitbucketService
        this.logger = logger
    }

    void execute() {
        def registry = ServiceRegistry.instance

        // Initialize required services
        if (!registry.get(SonarQubeService)) {
            logger.info 'Registering SonarQubeService'
            registry.add(SonarQubeService, new SonarQubeService(
                script, logger, 'SonarServerConfig'
            ))
        }
        def sonarQubeService = registry.get(SonarQubeService)

        // Initialize config.odsNamespace if not present
        if (!config.odsNamespace) {
            config.odsNamespace = [:]
        }

        // Set OPENSHIFT_BUILD_NAMESPACE from environment if not already set
        if (!config.odsNamespace.OPENSHIFT_BUILD_NAMESPACE) {
            config.odsNamespace.OPENSHIFT_BUILD_NAMESPACE =
                script.env.OPENSHIFT_BUILD_NAMESPACE
        }

        def openShiftService = registry.get(OpenShiftService)
        Map configurationSonarCluster = [:]
        Map configurationSonarProject = [:]

        // Read SonarQube config from ConfigMap
        configurationSonarCluster = openShiftService.getConfigMapData(
            config.odsNamespace.OPENSHIFT_BUILD_NAMESPACE,
            ScanWithSonarStage.SONAR_CONFIG_MAP_NAME
        )

        // Project-level enabled flag
        def key = "projects." + context.projectId + ".enabled"
        if (configurationSonarCluster.containsKey(key)) {
            configurationSonarProject.put(
                'enabled',
                configurationSonarCluster.get(key)
            )
            logger.info(
                "Parameter 'projects.${context.projectId}.enabled' at cluster level exists"
            )
        } else {
            configurationSonarProject.put('enabled', true)
            logger.info(
                "Not parameter 'projects.${context.projectId}.enabled' at cluster level. " +
                "Default enabled"
            )
        }

        boolean enabledInCluster = Boolean.valueOf(
            configurationSonarCluster['enabled']?.toString() ?: "true"
        )
        boolean enabledInProject = Boolean.valueOf(
            configurationSonarProject['enabled']?.toString() ?: "true"
        )

        // Only run scan if enabled in both cluster and project
        if (enabledInCluster && enabledInProject) {
            Stage sonarStage = new ScanWithSonarStage(
                script,
                context,
                [:],
                this.bitbucketService,
                sonarQubeService,
                registry.get(NexusService),
                logger,
                configurationSonarCluster,
                configurationSonarProject
            )
            sonarStage.execute()
            logger.info("Automatic SonarQube scan completed successfully")
        } else {
            if (!enabledInCluster && !enabledInProject) {
                logger.warn(
                    "Skipping SonarQube scan because it is not enabled at cluster nor " +
                    "project level"
                )
            } else if (enabledInCluster) {
                logger.warn(
                    "Skipping SonarQube scan because it is not enabled at project level"
                )
            } else {
                logger.warn(
                    "Skipping SonarQube scan because it is not enabled at cluster level"
                )
            }
        }
    }
}
