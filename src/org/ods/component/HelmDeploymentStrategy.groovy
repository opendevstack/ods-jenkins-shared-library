package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import org.ods.util.PipelineSteps
import org.ods.util.PodData

class HelmDeploymentStrategy extends AbstractDeploymentStrategy implements IDeploymentStrategy {
    private final List<String> DEPLOYMENT_KINDS = [
        OpenShiftService.DEPLOYMENT_KIND, OpenShiftService.DEPLOYMENTCONFIG_KIND,
    ]

    protected def script
    protected Map<String, Object> config
    protected final JenkinsService jenkins
    protected ILogger logger

    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'ParameterCount'])
    @TypeChecked(TypeCheckingMode.SKIP)
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

        def deploymentResources = openShift.getResourcesForComponent(
            context.targetProject, DEPLOYMENT_KINDS, options.selector
        )

        try {

            // Tag images which have been built in this pipeline from cd project into target project
            retagImages(context.targetProject, getBuiltImages())

            logger.info "Rolling out ${context.componentId} with HELM, selector: ${options.selector}"
            helmUpgrade(context.targetProject)
            deploymentResources = openShift.getResourcesForComponent(
                context.targetProject, DEPLOYMENT_KINDS, options.selector
            )

            Map<Object, Object> rolloutData = [:]
            rolloutData = rollout(deploymentResources)
            return rolloutData
        } finally {
            logger.info("Rollout done!")
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
                Map mergedHelmValues = [:]
                mergedHelmValues << options.helmValues
                mergedHelmValues['imageNamespace'] = targetProject
                mergedHelmValues['imageTag'] = options.imageTag

                // deal with dynamic value files - which are env dependent
                List mergedHelmValuesFiles = []
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
    private Map<String, List<PodData>> rollout(
        Map<String, List<String>> deploymentResources) {

        def rolloutData = [:]
        deploymentResources.each { resourceKind, resourceNames ->
            resourceNames.each { resourceName ->

                def podData = [:]

                podData = openShift.checkForPodData(context.targetProject, options.selector)
                addDeploymentArtifacts(resourceName)

                rolloutData["${resourceKind}/${resourceName}"] = podData
                // TODO: Once the orchestration pipeline can deal with multiple replicas,
                // update this to store multiple pod artifacts.
                // TODO: Potential conflict if resourceName is duplicated between
                // Deployment and DeploymentConfig resource.
                context.addDeploymentToArtifactURIs(resourceName, podData[0]?.toMap())
            }
        }
        rolloutData
    }

    @Override
    void addDeploymentArtifacts(resourceName) {
        context.addDeploymentToArtifactURIs("${resourceName}-deploymentMean",
            [
                'type'                   : 'helm',
                'selector'               : options.selector,
                'chartDir'               : options.chartDir,
                'helmReleaseName'        : options.helmReleaseName,
                'helmEnvBasedValuesFiles': options.helmEnvBasedValuesFiles,
                'helmValuesFiles'        : options.helmValuesFiles,
                'helmValues'             : options.helmValues,
                'helmDefaultFlags'       : options.helmDefaultFlags,
                'helmAdditionalFlags'    : options.helmAdditionalFlags
            ])
    }
}
