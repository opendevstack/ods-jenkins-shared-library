package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.util.HelmStatus
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.PodData

class HelmDeploymentStrategy extends AbstractDeploymentStrategy {

    // Constructor arguments
    private final IContext context
    private final OpenShiftService openShift
    private final JenkinsService jenkins
    private final ILogger logger
    private final IPipelineSteps steps
    private final IImageRepository imageRepository

    // assigned in constructor
    private final RolloutOpenShiftDeploymentOptions options

    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'ParameterCount'])
    HelmDeploymentStrategy(
        IPipelineSteps steps,
        IContext context,
        Map<String, Object> config,
        OpenShiftService openShift,
        JenkinsService jenkins,
        IImageRepository imageRepository,
        ILogger logger
    ) {
        HelmDeploymentConfig.applyDefaults(context, config)
        this.context = context
        this.logger = logger
        this.steps = steps
        this.options = new RolloutOpenShiftDeploymentOptions(config)
        this.openShift = openShift
        this.jenkins = jenkins
        this.imageRepository = imageRepository
    }

    @Override
    Map<String, List<PodData>> deploy() {
        if (!context.environment) {
            logger.warn 'Skipping because of empty (target) environment ...'
            return [:]
        }
        // Maybe we need to deploy to another namespace
        // (ie we want to deploy a monitoring stack into a specific namespace)
        def targetProject = context.targetProject
        if (options.helmValues['namespaceOverride']) {
            targetProject = options.helmValues['namespaceOverride']
            logger.info("Override namespace deployment to ${targetProject} ")
        }
        logger.info("Retagging images for ${targetProject} ")
        imageRepository.retagImages(targetProject, getBuiltImages(), options.imageTag, options.imageTag)

        logger.info("Rolling out ${context.componentId} with HELM, selector: ${options.selector}")
        helmUpgrade(targetProject)
        HelmStatus helmStatus = openShift.helmStatus(targetProject, options.helmReleaseName)
        if (logger.debugMode) {
            def helmStatusMap = helmStatus.toMap()
            logger.debug("${this.class.name} -- HELM STATUS: ${helmStatusMap}")
        }

        // // FIXME: pauseRollouts is non trivial to determine!
        // // we assume that Helm does "Deployment" that should work for most
        // // cases since they don't have triggers.
        // metadataSvc.updateMetadata(false, deploymentResources)
        Map<String, List<PodData>> rolloutData = [:]
        if (steps.env.repoType == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_INFRA) {
            return rolloutData
        } else {
            return getRolloutData(helmStatus, targetProject)
        }
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
    private Map<String, List<PodData>> getRolloutData(
        HelmStatus helmStatus, String targetProject
    ) {
        Map<String, List<PodData>> rolloutData = [:]

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
                    targetProject, kind, name
                )
                logger.debug("Helm container images for ${kind}/${name}: ${containers}")

                containersByResource[name] = containers

                def podData = new PodData(
                    deploymentId: name,
                    containers: containers,
                )
                rolloutData[name] = [podData]
            }
        }

        // Create a single deploymentMean entry for the entire helm release
        def deploymentMean = [
            type: 'helm',
            selector: options.selector,
            namespace: targetProject,
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

        // Store consolidated deployment data with all unique containers flattened
        // Ensure all container images are plain strings (handle deserialization artifacts)
        // Flatten containers from all resources into a single map to support multiple containers per resource
        def flattenedContainers = [:]
        containersByResource.each { resourceName, containerMap ->
            containerMap.each { containerName, image ->
                // Extract string value - image might be String, or wrapped in Map/List after deserialization
                def imageStr = image
                while (Map.isInstance(imageStr) && imageStr.size() > 0) {
                    imageStr = imageStr.values()[0]
                }
                while (List.isInstance(imageStr) && imageStr.size() > 0) {
                    imageStr = imageStr[0]
                }
                def stringValue = imageStr.toString()
                // Ensure no trailing bracket or other artifacts remain from serialization
                // Use double-escaped bracket to ensure proper regex matching in Groovy
                stringValue = stringValue.replaceAll('\\]\\s*$', '')
                // Create a unique key for each container
                // If resource has only one container, use resource name; otherwise use resource::container format
                def containerKey = containerMap.size() == 1 ? resourceName : "${resourceName}::${containerName}"
                // Only add if this image isn't already present (avoid duplicates of same image)
                if (!flattenedContainers.values().contains(stringValue)) {
                    flattenedContainers[containerKey] = stringValue
                }
            }
        }
        def consolidatedPodData = new PodData(
            deploymentId: options.helmReleaseName,
            containers: flattenedContainers as Map<String, String>,
        )
        context.addDeploymentToArtifactURIs(options.helmReleaseName, consolidatedPodData.toMap())

        return rolloutData
    }

}
