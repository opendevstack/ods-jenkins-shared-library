package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
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

    // assigned in constructor
    private final IPipelineSteps steps
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
        this.context = context
        this.logger = logger
        this.steps = steps

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

                options.helmEnvBasedValuesFiles = options.helmEnvBasedValuesFiles.collect {
                    it.replace('.env.', ".${context.environment}.")
                }

                mergedHelmValuesFiles.addAll(options.helmValuesFiles)
                mergedHelmValuesFiles.addAll(options.helmEnvBasedValuesFiles)

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

    @TypeChecked(TypeCheckingMode.SKIP)
    private Map<String, List<PodData>> getRolloutData(
        HelmStatus helmStatus
    ) {
        // TODO This is still needed for several reasons, still pending refactoring in order to
        //  remove the need for pod data
        Map<String, List<PodData>> rolloutData = [:]

        Map<String, List<String>> deploymentKinds = helmStatus.resourcesByKind
                .findAll { kind, res -> kind in DEPLOYMENT_KINDS }

        deploymentKinds.each { kind, names ->
            names.each { name ->
                context.addDeploymentToArtifactURIs("${name}-deploymentMean",
                    [
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
                    ]
                )
                def podDataContext = [
                    "targetProject=${context.targetProject}",
                    "selector=${options.selector}",
                    "name=${name}",
                ]
                def msgPodsNotFound = "Could not find 'running' pod(s) for '${podDataContext.join(', ')}'"
                List<PodData> podData = null
                for (def i = 0; i < options.deployTimeoutRetries; i++) {
                    podData = openShift.checkForPodData(context.targetProject, options.selector, name)
                    if (podData) {
                        break
                    }
                    steps.echo("${msgPodsNotFound} - waiting")
                    steps.sleep(12)
                }
                if (!podData) {
                    throw new RuntimeException(msgPodsNotFound)
                }
                logger.debug("Helm podData for ${podDataContext.join(', ')}: ${podData}")

                rolloutData["${kind}/${name}"] = podData
                // TODO: Once the orchestration pipeline can deal with multiple replicas,
                //  update this to store multiple pod artifacts.
                // TODO: Potential conflict if resourceName is duplicated between
                //  Deployment and DeploymentConfig resource.
                context.addDeploymentToArtifactURIs(name, podData[0]?.toMap())
            }
        }
        return rolloutData
    }

}
