package org.ods.component

import groovy.json.JsonOutput
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.openshift.OpenShiftResourceMetadata
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

        Map<String,String> labels = showMandatoryLabels()
        applyMandatoryLabels(labels, deploymentResources)

        // // FIXME: pauseRollouts is non trivial to determine!
        // // we assume that Helm does "Deployment" that should work for most
        // // cases since they don't have triggers.
        // metadataSvc.updateMetadata(false, deploymentResources)
        def rolloutData = getRolloutData(deploymentResources) ?: [:]
        logger.info(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(rolloutData)))
        return rolloutData
    }

    private void applyMandatoryLabels(Map<String, String> labels, Map<String, List<String>> deploymentResources) {
        logger.debug("${this.class.name} -- MANDATORY LABELS")
        logger.debug(
            JsonOutput.prettyPrint(
                JsonOutput.toJson(labels)))
        logger.debug("${this.class.name} -- MANDATORY LABELS (cleaned)")
        logger.debug(
            JsonOutput.prettyPrint(
                JsonOutput.toJson(labels)))
        labels.remove { it -> it.value == null }
        def resourcesToLabel = deploymentResources.collectMany { entry ->
            entry.value.collect {
                "${entry.key}/${it}"
            }
        }
        logger.debug("${this.class.name} -- RESOURCE TO LABEL")
        logger.debug(
            JsonOutput.prettyPrint(
                JsonOutput.toJson(resourcesToLabel)))
        resourcesToLabel.each { resourceToLabel ->
            openShift.labelResources(context.targetProject, resourceToLabel, labels, null)
        }

        // Add OpenShiftResouceMetadata.strictEntries here to be sure we have them
        if (context.triggeredByOrchestrationPipeline) {
            def additionalLabels = [
                'app.opendevstack.org/system-name': steps.env?.BUILD_PARAM_CONFIGITEM ?: null,
                'app.opendevstack.org/project-version': steps.env?.BUILD_PARAM_CHANGEID ?: null,
                'app.opendevstack.org/work-in-progress': steps.env?.BUILD_PARAM_VERSION == 'WIP' ?: null,
            ]
            resourcesToLabel.each { resourceToLabel ->
                openShift.labelResources(context.targetProject, resourceToLabel, additionalLabels, null)
            }
        }

    }

    private def showMandatoryLabels() {
        // FIXME: OpenShiftResourceMetadata.updateMetadata breaks
        //  This happens because it, unconditionally, tries to reset some fields
        def metadataSvc = new OpenShiftResourceMetadata(
            steps,
            context.properties,
            options.properties,
            logger,
            openShift
        )

        // DEBUG what we consider "mandatory"
        def metadata = metadataSvc.getMandatoryMetadata()
        logger.debug(
            JsonOutput.prettyPrint(
                JsonOutput.toJson(metadata)))
        return metadata
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
                context.addDeploymentToArtifactURIs("${resourceName}-deploymentMean", [
                        'type': 'helm',
                        'selector': options.selector,
                        'chartDir': options.chartDir,
                        'helmReleaseName': options.helmReleaseName,
                        'helmEnvBasedValuesFiles': options.helmEnvBasedValuesFiles,
                        'helmValuesFiles': options.helmValuesFiles,
                        'helmValues': options.helmValues,
                        'helmDefaultFlags': options.helmDefaultFlags,
                        'helmAdditionalFlags': options.helmAdditionalFlags
                    ])
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

}
