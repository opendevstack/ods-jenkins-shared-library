package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.util.HelmStatusSimpleData
import org.ods.util.ILogger
import org.ods.util.JsonLogUtil
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
        HelmStatusSimpleData helmStatus = openShift.helmStatus(context.targetProject, options.helmReleaseName)
        if (logger.debugMode) {
            def deploymentResources = getDeploymentResources(helmStatus)
            def helmStatusMap = helmStatus.toMap()
            JsonLogUtil.debug(logger, "${this.class.name} -- HELM STATUS".toString(), helmStatusMap)
            JsonLogUtil.debug(logger, "${this.class.name} -- DEPLOYMENT RESOURCES".toString(), deploymentResources)
        }

        // // FIXME: pauseRollouts is non trivial to determine!
        // // we assume that Helm does "Deployment" that should work for most
        // // cases since they don't have triggers.
        // metadataSvc.updateMetadata(false, deploymentResources)
        def rolloutData = getRolloutData(helmStatus)
        logger.info(JsonLogUtil.jsonToString(rolloutData))
        return rolloutData
    }

    @SuppressWarnings(['UnnecessaryCast'])  // otherwise IDE marked up helmUpgrade call
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
                def mergedHelmValues = [:] as Map<String, String>
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
                def mergedHelmValuesFiles = [] as List<String>
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

    private Map<String, List<String>> getDeploymentResources(HelmStatusSimpleData helmStatus) {
        helmStatus.getResourcesByKind(DEPLOYMENT_KINDS)
    }

    // rollout returns a map like this:
    // [
    //    'DeploymentConfig/foo': [[podName: 'foo-a', ...], [podName: 'foo-b', ...]],
    //    'Deployment/bar': [[podName: 'bar-a', ...]]
    // ]
    @TypeChecked(TypeCheckingMode.SKIP)
    private Map<String, List<PodData>> getRolloutData(
        HelmStatusSimpleData helmStatus
    ) {
        @SuppressWarnings(['LineLength'])  // for the github permalinks
        // Why do we need podData when helm should take care of the install
        // 1. FinalizeOdsComponent#verifyDeploymentsBuiltByODS() will fail at
        //      https://github.com/opendevstack/ods-jenkins-shared-library/blob/7aab67dad73298b1388eca3517a1a2ea856a2b8e/src/org/ods/orchestration/phases/FinalizeOdsComponent.groovy#L165
        //    unless it can associate all pods with a deployment resource.
        // 2. DeploymentDescripter requires to have for each deployment resource
        //    a deployment mean with postfix -deploymentMean
        // 3. Image importing in DeployOdsComponent at
        //    https://github.com/opendevstack/ods-jenkins-shared-library/blob/97463eebc986f1558c37d5ff5a84ec188f9e92d0/src/org/ods/orchestration/phases/DeployOdsComponent.groovy#L51)
        //    currently is driven by images in PodData#containers.
        //
        // If possible this should be redesigned so that the shared library does not have to
        // concern itself with pods anymore.
        def rolloutData = [:]
        helmStatus.resourcesByKind.each { kind, names ->
            if (!(kind in DEPLOYMENT_KINDS)) {
                return // continues with next
            }
            names.each { name ->
                context.addDeploymentToArtifactURIs("${name}-deploymentMean",
                    [
                        type: 'helm',
                        selector: options.selector,
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
                JsonLogUtil.debug(logger, "Helm podData for ${podDataContext.join(', ')}:".toString(), podData)

                rolloutData["${kind}/${name}"] = podData
                // TODO: Once the orchestration pipeline can deal with multiple replicas,
                // update this to store multiple pod artifacts.
                // TODO: Potential conflict if resourceName is duplicated between
                // Deployment and DeploymentConfig resource.
                context.addDeploymentToArtifactURIs(name, podData[0]?.toMap())
            }
        }
        rolloutData
    }

}
