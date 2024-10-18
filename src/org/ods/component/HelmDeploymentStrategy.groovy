package org.ods.component

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

                // Here we determine the most recent pod and store it as an artifact.
                // And we fail if there are multiple pods with different images.
                def latestPods = multipleMax(podData) { it.podMetaDataCreationTimestamp }
                def equal = areEqual(latestPods) { a,b ->
                    def imagesA = a.containers.values() as Set
                    def imagesB = b.containers.values() as Set
                    return imagesA == imagesB
                }
                if (!equal) {
                    throw new RuntimeException("Unable to determine the most recent Pod. Multiple pods running with different creation timestamps and images found for ${resourceName}")
                }
                // TODO: Once the orchestration pipeline can deal with multiple replicas,
                // update this to store multiple pod artifacts.
                // TODO: Potential conflict if resourceName is duplicated between
                // Deployment and DeploymentConfig resource.
                context.addDeploymentToArtifactURIs(resourceName, latestPods[0]?.toMap())
            }
        }
        rolloutData
    }

    private List multipleMax(Collection c, Closure comp) {
        List res = []
        if (!c) {
            return res
        }
        def max = null;
        c.each {
            def current = comp(it)
            if (current.is(null) || current > max) {
                max = current
                res = [it]
            } else if (current == max) {
                res += it
            }
        }
        return res
    }

    private boolean areEqual(Collection c, Closure equals) {
        if (c?.size() > 1) {
            def base = null
            c.each {
                if (base.is(null)) {
                    base = it
                } else if (!equals(base, it)) {
                    return false
                }
            }
        }
        return true
    }
}
