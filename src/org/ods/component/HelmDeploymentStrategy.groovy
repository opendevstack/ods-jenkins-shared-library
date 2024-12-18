package org.ods.component

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import org.ods.util.PipelineSteps
import org.ods.util.PodData

class HelmDeploymentStrategy extends AbstractDeploymentStrategy {

    // Constructor arguments
    private final Script script
    private final IContext context
    private final OpenShiftService openShift
    private final JenkinsService jenkins
    private final ILogger logger

    // assigned in constructor
    private def steps
    private final RolloutOpenShiftDeploymentOptions options

    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'ParameterCount'])
    HelmDeploymentStrategy(
        def script,
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
        if (!config.containsKey('helmDefaultFlags')) {
            config.helmDefaultFlags = ['--install', '--atomic']
        }
        if (!config.containsKey('helmAdditionalFlags')) {
            config.helmAdditionalFlags = []
        }
        if (!config.containsKey('helmDiff')) {
            config.helmDiff = true
        }
        if (!config.helmPrivateKeyCredentialsId) {
            config.helmPrivateKeyCredentialsId = "${context.cdProject}-helm-private-key"
        }
        this.script = script
        this.context = context
        this.logger = logger
        this.steps = new PipelineSteps(script)

        this.options = new RolloutOpenShiftDeploymentOptions(config)
        this.openShift = openShift
        this.jenkins = jenkins
    }

    @Override
    Map<String, List<PodData>> deploy() {
        if (!context.environment) {
            logger.warn 'Skipping because of empty (target) environment ...'
            return [:]
        }

        // Tag images which have been built in this pipeline from cd project into target project
        retagImages(context.targetProject, getBuiltImages())

        logger.info "Rolling out ${context.componentId} with HELM, selector: ${options.selector}"
        helmUpgrade(context.targetProject)

        def deploymentResources = openShift.getResourcesForComponent(
            context.targetProject, DEPLOYMENT_KINDS, options.selector
        )
        logger.info("${this.class.name} -- DEPLOYMENT RESOURCES")
        logger.info(
            JsonOutput.prettyPrint(
                JsonOutput.toJson(deploymentResources)))

        // // FIXME: pauseRollouts is non trivial to determine!
        // // we assume that Helm does "Deployment" that should work for most
        // // cases since they don't have triggers.
        // metadataSvc.updateMetadata(false, deploymentResources)
        def rolloutData = getRolloutData(deploymentResources) ?: [:]
        logger.info(JsonOutput.prettyPrint(JsonOutput.toJson(rolloutData)))
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
                Map mergedHelmValues = [:]
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
                mergedHelmValuesFiles.addAll(options.helmValuesFiles)

                options.helmEnvBasedValuesFiles.each { envValueFile ->
                    mergedHelmValuesFiles << envValueFile.replace('.env',
                        ".${context.environment}")
                }

                openShift.helmUpgrade(
                    targetProject,
                    options.helmReleaseName,
                    mergedHelmValuesFiles,
                    mergedHelmValues,
                    options.helmDefaultFlags,
                    options.helmAdditionalFlags,
                    options.helmDiff
                )
            }
        }
    }

    // rollout returns a map like this:
    // [
    //    'DeploymentConfig/foo': [[podName: 'foo-a', ...], [podName: 'foo-b', ...]],
    //    'Deployment/bar': [[podName: 'bar-a', ...]]
    // ]
    @TypeChecked(TypeCheckingMode.SKIP)
    private Map<String, List<PodData>> getRolloutData(
        Map<String, List<String>> deploymentResources) {

        def rolloutData = [:]
        deploymentResources.each { resourceKind, resourceNames ->
            resourceNames.each { resourceName ->
                def podData = []
                for (def i = 0; i < options.deployTimeoutRetries; i++) {
                    podData = openShift.checkForPodData(context.targetProject, options.selector, resourceName)
                    if (!podData.isEmpty()) {
                        break
                    }
                    steps.echo("Could not find 'running' pod(s) with label '${options.selector}' - waiting")
                    steps.sleep(12)
                }
                context.addDeploymentToArtifactURIs("${resourceName}-deploymentMean",
                    [
                        'type': 'helm',
                        'selector': options.selector,
                        'chartDir': options.chartDir,
                        'helmReleaseName': options.helmReleaseName,
                        'helmEnvBasedValuesFiles': options.helmEnvBasedValuesFiles,
                        'helmValuesFiles': options.helmValuesFiles,
                        'helmValues': options.helmValues,
                        'helmDefaultFlags': options.helmDefaultFlags,
                        'helmAdditionalFlags': options.helmAdditionalFlags,
                    ])
                rolloutData["${resourceKind}/${resourceName}"] = podData

                // We need to find the pod that was created as a result of the deployment.
                // The previous pod may still be alive when we use a rollout strategy.
                // We can tell one from the other using their creation timestamp,
                // being the most recent the one we are interested in.
                def latestPods = getLatestPods(podData)
                // While very unlikely, it may happen that there is more than one pod with the same timestamp.
                // Note that timestamp resolution is seconds.
                // If that happens, we are unable to know which is the correct pod.
                // However, it doesn't matter which pod is the right one, if they all have the same images.
                def sameImages = haveSameImages(latestPods)
                if (!sameImages) {
                    throw new RuntimeException("Unable to determine the most recent Pod. Multiple pods running with the same latest creation timestamp and different images found for ${resourceName}")
                }
                // TODO: Once the orchestration pipeline can deal with multiple replicas,
                // update this to store multiple pod artifacts.
                // TODO: Potential conflict if resourceName is duplicated between
                // Deployment and DeploymentConfig resource.
                context.addDeploymentToArtifactURIs(resourceName, latestPods[0]?.toMap())
            }
        }
        return rolloutData
    }

    /**
     * Returns the pods with the latest creation timestamp.
     * Note that the resolution of this timestamp is seconds and there may be more than one pod with the same
     * latest timestamp.
     *
     * @param pods the pods over which to find the latest ones.
     * @return a list with all the pods sharing the same, latest timestamp.
     */
    @NonCPS
    private static List getLatestPods(Iterable pods) {
        return maxElements(pods) { it.podMetaDataCreationTimestamp }
    }

    /**
     * Checks whether all the given pods contain the same images, ignoring order and multiplicity.
     *
     * @param pods the pods to check for image equality.
     * @return true if all the pods have the same images or false otherwise.
     */
    @NonCPS
    private static boolean haveSameImages(Iterable pods) {
        return areEqual(pods) { a, b ->
            def imagesA = a.containers.values() as Set
            def imagesB = b.containers.values() as Set
            return imagesA == imagesB
        }
    }

    /**
     * Selects the items in the iterable which when passed as a parameter to the supplied closure return the maximum value.
     * A null return value represents the least possible return value,
     * so any item for which the supplied closure returns null, won't be selected (unless all items return null).
     * The return list contains all the elements that returned the maximum value.
     *
     * @param iterable the iterable over which to search for maximum values.
     * @param getValue a closure returning the value that corresponds to each element.
     * @return the list of all the elements for which the closure returns the maximum value.
     */
    @NonCPS
    private static List maxElements(Iterable iterable, Closure getValue) {
        List elements = null
        if (!iterable) {
            return []
        }
        def maxValue = null;
        iterable.each {
            def value = getValue(it)
            if (!elements || value > maxValue) {
                maxValue = value
                elements = [it]
            } else if (value == maxValue) {
                elements += it
            }
        }
        return elements
    }

    /**
     * Checks whether all the elements in the given iterable are deemed as equal by the given closure.
     *
     * @param iterable the iterable over which to check for element equality.
     * @param equals a closure that checks two elements for equality.
     * @return true if all the elements are equal or false otherwise.
     */
    @NonCPS
    private static boolean areEqual(Iterable iterable, Closure equals) {
        def equal = true
        if (iterable) {
            def first = true
            def base = null
            iterable.each {
                if (first) {
                    base = it
                    first = false
                } else if (!equals(base, it)) {
                    equal = false
                }
            }
        }
        return equal
    }
}
