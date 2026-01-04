package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.util.HelmStatus
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps

class HelmDeploymentStrategy extends AbstractDeploymentStrategy {

    // Constructor arguments
    private final IContext context
    private final OpenShiftService openShift
    private final JenkinsService jenkins
    private final ILogger logger
    private final IPipelineSteps steps
    // assigned in constructor
    private final RolloutOpenShiftDeploymentOptions options

    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'ParameterCount'])
    HelmDeploymentStrategy(
        IPipelineSteps steps,
        IContext context,
        Map<String, Object> config,
        OpenShiftService openShift,
        JenkinsService jenkins,
        ILogger logger
    ) {
        if (!config.selector) {
            config.selector = context.selector
        }
        if (!config.imageTag) {
            config.imageTag = context.shortGitCommit
        }
        if (!config.deployTimeoutMinutes) {
            config.deployTimeoutMinutes = context.openshiftRolloutTimeout ?: 15
        }
        if (!config.deployTimeoutRetries) {
            config.deployTimeoutRetries = context.openshiftRolloutTimeoutRetries ?: 5
        }
        // Helm options
        if (!config.chartDir) {
            config.chartDir = 'chart'
        }
        if (!config.containsKey('helmReleaseName')) {
            config.helmReleaseName = context.componentId
        }
        if (!config.containsKey('helmValues')) {
            config.helmValues = [:]
        }
        if (!config.containsKey('helmValuesFiles')) {
            config.helmValuesFiles = ['values.yaml']
        }
        if (!config.containsKey('helmEnvBasedValuesFiles')) {
            config.helmEnvBasedValuesFiles = []
        }
        // helmDefaultFlags are always set and cannot be overridden
        config.helmDefaultFlags = ['--install', '--atomic']
        if (!config.containsKey('helmAdditionalFlags')) {
            config.helmAdditionalFlags = []
        }
        if (!config.containsKey('helmDiff')) {
            config.helmDiff = true
        }
        if (!config.helmPrivateKeyCredentialsId) {
            config.helmPrivateKeyCredentialsId = "${context.cdProject}-helm-private-key"
        }
        if (!config.containsKey('helmReleasesHistoryLimit')) {
            config.helmReleasesHistoryLimit = 5
        }
        this.context = context
        this.logger = logger
        this.steps = steps

        this.options = new RolloutOpenShiftDeploymentOptions(config)
        this.openShift = openShift
        this.jenkins = jenkins
    }

    @Override
    Map<String, Map<String, Object>> deploy() {
        if (!context.environment) {
            logger.warn 'Skipping because of empty (target) environment ...'
            return [:]
        }

        // Tag images which have been built in this pipeline from cd project into target project
        retagImages(context.targetProject, getBuiltImages())

        logger.info("Rolling out ${context.componentId} with HELM, selector: ${options.selector}")
        helmUpgrade(context.targetProject)
        HelmStatus helmStatus = openShift.helmStatus(context.targetProject, options.helmReleaseName)
        if (logger.debugMode) {
            def helmStatusMap = helmStatus.toMap()
            logger.debug("${this.class.name} -- HELM STATUS: ${helmStatusMap}")
        }

        // // FIXME: pauseRollouts is non trivial to determine!
        // // we assume that Helm does "Deployment" that should work for most
        // // cases since they don't have triggers.
        // metadataSvc.updateMetadata(false, deploymentResources)
        def rolloutData = getRolloutData(helmStatus)
        return rolloutData
    }

    private void helmUpgrade(String targetProject) {
        steps.dir(options.chartDir) {
            jenkins.maybeWithPrivateKeyCredentials(options.helmPrivateKeyCredentialsId) { String pkeyFile ->
                if (pkeyFile) {
                    steps.sh(script: "gpg --import ${pkeyFile}", label: 'Import private key into keyring')
                }

                // we add two things persistent - as these NEVER change (and are env independent)
                options.helmValues['registry'] = context.clusterRegistryAddress
                options.helmValues['componentId'] = context.componentId

                // we persist the original ones set from outside - here we just add ours
                Map<String, String> mergedHelmValues = [:]
                mergedHelmValues << options.helmValues

                // we add the global ones - this allows usage in subcharts
                options.helmValues.each { key, value ->
                    mergedHelmValues["global.${key}"] = value
                }

                mergedHelmValues['imageNamespace'] = targetProject
                mergedHelmValues['imageTag'] = options.imageTag

                // we also add the predefined ones as these are in use by the library
                mergedHelmValues['global.imageNamespace'] = targetProject
                mergedHelmValues['global.imageTag'] = options.imageTag

                // deal with dynamic value files - which are env dependent
                def mergedHelmValuesFiles = []

                def  envConfigFiles = options.helmEnvBasedValuesFiles.collect { filenamePattern ->
                    filenamePattern.replace('.env.', ".${context.environment}.")
                }

                mergedHelmValuesFiles.addAll(options.helmValuesFiles)
                mergedHelmValuesFiles.addAll(envConfigFiles)

                // Add history-max flag to limit stored release revisions
                def mergedAdditionalFlags = options.helmAdditionalFlags.collect { it }
                mergedAdditionalFlags << "--history-max ${options.helmReleasesHistoryLimit}".toString()

                openShift.helmUpgrade(
                    targetProject,
                    options.helmReleaseName,
                    mergedHelmValuesFiles,
                    mergedHelmValues,
                    options.helmDefaultFlags,
                    mergedAdditionalFlags,
                    options.helmDiff
                )
            }
        }
    }

    // rollout returns a map like this:
    // [
    //    'DeploymentConfig/foo': [deploymentId: 'foo', containers: [...], ...],
    //    'Deployment/bar': [deploymentId: 'bar', containers: [...], ...]
    // ]
    @TypeChecked(TypeCheckingMode.SKIP)
    private Map<String, Map<String, Object>> getRolloutData(
        HelmStatus helmStatus
    ) {
        Map<String, Map<String, Object>> rolloutData = [:]

        Map<String, List<String>> deploymentKinds = helmStatus.resourcesByKind
                .findAll { kind, res -> kind in DEPLOYMENT_KINDS }

        // Collect containers organized by resource (kind/name) to preserve all images
        Map<String, Map<String, String>> containersByResource = [:]
        Map<String, List<String>> resourcesByKind = [:]

        deploymentKinds.each { kind, names ->
            resourcesByKind[kind] = names
            names.each { name ->
                // Get container images directly from the resource spec (deployment/statefulset/cronjob)
                def containers = openShift.getContainerImagesWithNameFromPodSpec(
                    context.targetProject, kind, name
                )
                logger.debug("Helm container images for ${kind}/${name}: ${containers}")

                // Store containers using the name (which includes the kind suffix for consistency)
                // e.g., "rust-three-deployment", "rust-three-statefulset", "rust-three-cronjob"
                containersByResource[name] = containers

                def resourceData = [
                    deploymentId: name,
                    containers: containers,
                ]
                rolloutData[name] = resourceData
            }
        }

        // Create a single deploymentMean entry for the entire helm release
        def deploymentMean = [
            type: 'helm',
            selector: options.selector,
            namespace: context.targetProject,
            chartDir: options.chartDir,
            helmReleaseName: options.helmReleaseName,
            helmEnvBasedValuesFiles: options.helmEnvBasedValuesFiles,
            helmValuesFiles: options.helmValuesFiles,
            helmValues: options.helmValues,
            helmDefaultFlags: options.helmDefaultFlags,
            helmAdditionalFlags: options.helmAdditionalFlags,
            helmStatus: helmStatus.toMap(),
            resources: resourcesByKind,
        ]

        // Store deployment mean once per helm release (not per resource)
        context.addDeploymentToArtifactURIs("${options.helmReleaseName}-deploymentMean", deploymentMean)

        // Store consolidated deployment data with containers organized by resource
        // Ensure all container images are plain strings (handle deserialization artifacts)
        def consolidatedContainers = [:]
        containersByResource.each { resourceName, containerMap ->
            def stringifiedContainers = [:]
            containerMap.each { containerName, image ->
                // Extract string value - image might be String, or wrapped in Map/List after deserialization
                def imageStr = image
                while (imageStr instanceof Map && imageStr.size() > 0) {
                    imageStr = imageStr.values()[0]
                }
                while (imageStr instanceof List && imageStr.size() > 0) {
                    imageStr = imageStr[0]
                }
                def stringValue = imageStr.toString()
                // Ensure no trailing bracket or other artifacts remain from serialization
                // Use double-escaped bracket to ensure proper regex matching in Groovy
                stringValue = stringValue.replaceAll('\\]\\s*$', '')
                stringifiedContainers[containerName] = stringValue
            }
            consolidatedContainers[resourceName] = stringifiedContainers
        }
        def consolidatedDeploymentData = [
            deploymentId: options.helmReleaseName,
            containers: consolidatedContainers,
        ]
        context.addDeploymentToArtifactURIs(options.helmReleaseName, consolidatedDeploymentData)

        return rolloutData
    }

}