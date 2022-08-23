package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.openshift.OpenShiftResourceMetadata
import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.PipelineSteps
import org.ods.util.PodData

class TailorDeploymentStrategy extends AbstractDeploymentStrategy {

    // Constructor arguments
    private final Script script
    private final IContext context
    private final OpenShiftService openShift
    private final JenkinsService jenkins
    private final ILogger logger

    // assigned in constructor
//    private final IPipelineSteps steps
    private final RolloutOpenShiftDeploymentOptions options


    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'ParameterCount'])
    TailorDeploymentStrategy(
        Script script,
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
        // Tailor options
        if (!config.openshiftDir) {
            config.openshiftDir = 'openshift'
        }
        if (!config.tailorPrivateKeyCredentialsId) {
            config.tailorPrivateKeyCredentialsId = "${context.cdProject}-tailor-private-key"
        }
        if (!config.tailorSelector) {
            config.tailorSelector = config.selector
        }
        if (!config.containsKey('tailorVerify')) {
            config.tailorVerify = false
        }
        if (!config.containsKey('tailorExclude')) {
            config.tailorExclude = 'bc,is'
        }
        if (!config.containsKey('tailorParamFile')) {
            config.tailorParamFile = '' // none apart the automatic param file
        }
        if (!config.containsKey('tailorPreserve')) {
            config.tailorPreserve = [] // do not preserve any paths in live config
        }
        if (!config.containsKey('tailorParams')) {
            config.tailorParams = []
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
        def isHelmDeployment = false //steps.fileExists(options.chartDir + '/Chart.yaml')

        if (context.triggeredByOrchestrationPipeline
            && deploymentResources.containsKey(OpenShiftService.DEPLOYMENT_KIND)
            && !isHelmDeployment) {
            steps.error "Deployment resources cannot be used in a NON HELM orchestration pipeline."
            return [:]
        }
        def originalDeploymentVersions = fetchOriginalVersions(deploymentResources)

        def refreshResources = false
        def paused = true
        try {
            if (!isHelmDeployment) {
                openShift.bulkPause(context.targetProject, deploymentResources)
            }

            // Tag images which have been built in this pipeline from cd project into target project
            retagImages(context.targetProject, getBuiltImages())

//            if (isHelmDeployment) {
//                logger.info "Rolling out ${context.componentId} with HELM, selector: ${options.selector}"
//                refreshResources = true
//                helmUpgrade(context.targetProject)
//            }
            if (steps.fileExists(options.openshiftDir)) {
                refreshResources = true
                tailorApply(context.targetProject)
            }

            if (refreshResources) {
                deploymentResources = openShift.getResourcesForComponent(
                    context.targetProject, DEPLOYMENT_KINDS, options.selector
                )
            }

            def rolloutData = [:]
            if (!isHelmDeployment) {
                def metadata = new OpenShiftResourceMetadata(
                    steps,
                    context.properties,
                    options.properties,
                    logger,
                    openShift
                )
                metadata.updateMetadata(true, deploymentResources)
                rolloutData = rollout(deploymentResources, originalDeploymentVersions)
                paused = false
                return rolloutData
            } // else {
//                rolloutData = rollout(deploymentResources, originalDeploymentVersions, true)
//                paused = false
//                return rolloutData
//            }
        } finally {
            if (paused && !isHelmDeployment) {
                openShift.bulkResume(context.targetProject, DEPLOYMENT_KINDS, options.selector)
            }
        }
    }

//    protected String stageLabel() {
//        if (options.selector != context.selector) {
//            return "${STAGE_NAME} (${options.selector})"
//        }
//        STAGE_NAME
//    }


    private void tailorApply(String targetProject) {
        steps.dir(options.openshiftDir) {
            jenkins.maybeWithPrivateKeyCredentials(options.tailorPrivateKeyCredentialsId) { String pkeyFile ->
                openShift.tailorApply(
                    targetProject,
                    [selector: options.tailorSelector, exclude: options.tailorExclude],
                    options.tailorParamFile,
                    options.tailorParams,
                    options.tailorPreserve,
                    pkeyFile,
                    options.tailorVerify
                )
            }
        }
    }

//    private void helmUpgrade(String targetProject) {
//        steps.dir(options.chartDir) {
//            jenkins.maybeWithPrivateKeyCredentials(options.helmPrivateKeyCredentialsId) { String pkeyFile ->
//                if (pkeyFile) {
//                    steps.sh(script: "gpg --import ${pkeyFile}", label: 'Import private key into keyring')
//                }
//
//                // we add two things persistent - as these NEVER change (and are env independent)
//                options.helmValues['registry'] = context.clusterRegistryAddress
//                options.helmValues['componentId'] = context.componentId
//
//                // we persist the original ones set from outside - here we just add ours
//                Map mergedHelmValues = [:]
//                mergedHelmValues << options.helmValues
//                mergedHelmValues['imageNamespace'] = targetProject
//                mergedHelmValues['imageTag'] = options.imageTag
//
//                // deal with dynamic value files - which are env dependent
//                def mergedHelmValuesFiles = []
//                mergedHelmValuesFiles.addAll(options.helmValuesFiles)
//
//                options.helmEnvBasedValuesFiles.each { envValueFile ->
//                    mergedHelmValuesFiles << envValueFile.replace('.env',
//                        ".${context.environment}")
//                }
//
//                openShift.helmUpgrade(
//                    targetProject,
//                    options.helmReleaseName,
//                    mergedHelmValuesFiles,
//                    mergedHelmValues,
//                    options.helmDefaultFlags,
//                    options.helmAdditionalFlags,
//                    options.helmDiff
//                )
//            }
//        }
//    }

    // rollout returns a map like this:
    // [
    //    'DeploymentConfig/foo': [[podName: 'foo-a', ...], [podName: 'foo-b', ...]],
    //    'Deployment/bar': [[podName: 'bar-a', ...]]
    // ]
    @TypeChecked(TypeCheckingMode.SKIP)
    private Map<String, List<PodData>> rollout(
        Map<String, List<String>> deploymentResources,
        Map<String, Map<String, Integer>> originalVersions,
        boolean isHelm = false) {

        def rolloutData = [:]
        deploymentResources.each { resourceKind, resourceNames ->
            resourceNames.each { resourceName ->
                def originalVersion = 0
                if (originalVersions.containsKey(resourceKind)) {
                    originalVersion = originalVersions[resourceKind][resourceName] ?: 0
                }

                def podData = [:]
                if (!isHelm) {
                    podData = rolloutDeployment(resourceKind, resourceName, originalVersion)
                    context.addDeploymentToArtifactURIs("${resourceName}-deploymentMean",
                        [
                            'type': 'tailor', 'selector': options.selector,
                            'tailorSelectors': [selector: options.tailorSelector, exclude: options.tailorExclude],
                            'tailorParamFile': options.tailorParamFile,
                            'tailorParams': options.tailorParams,
                            'tailorPreserve': options.tailorPreserve,
                            'tailorVerify': options.tailorVerify
                        ])
//                } else {
//                    podData = openShift.checkForPodData(context.targetProject, options.selector)
//                    context.addDeploymentToArtifactURIs("${resourceName}-deploymentMean",
//                        [
//                            'type': 'helm',
//                            'selector': options.selector,
//                            'chartDir': options.chartDir,
//                            'helmReleaseName': options.helmReleaseName,
//                            'helmEnvBasedValuesFiles': options.helmEnvBasedValuesFiles,
//                            'helmValuesFiles': options.helmValuesFiles,
//                            'helmValues': options.helmValues,
//                            'helmDefaultFlags': options.helmDefaultFlags,
//                            'helmAdditionalFlags': options.helmAdditionalFlags
//                        ])
                }
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

    private List<PodData> rolloutDeployment(String resourceKind, String resourceName, int originalVersion) {
        def ownedImageStreams = openShift
            .getImagesOfDeployment(context.targetProject, resourceKind, resourceName)
            .findAll { context.targetProject == it.repository }
        def missingStreams = missingImageStreams(ownedImageStreams)
        if (missingStreams) {
            steps.error "The following ImageStream resources  for ${resourceKind} '${resourceName}' " +
                """do not exist: '${missingStreams.collect { "${it.repository}/${it.name}" }}'. """ +
                'Verify that you have setup the OpenShift resources correctly.'
        }

        setImageTagLatest(ownedImageStreams)

        // May be paused in order to prevent multiple rollouts.
        // If a rollout is triggered when resuming, the rollout method should detect it.
        openShift.resume("${resourceKind}/${resourceName}", context.targetProject)

        String podManager
        try {
            podManager = openShift.rollout(
                context.targetProject,
                resourceKind,
                resourceName,
                originalVersion,
                options.deployTimeoutMinutes
            )
        } catch (ex) {
            steps.error ex.message
        }

        return openShift.getPodDataForDeployment(
            context.targetProject,
            resourceKind,
            podManager,
            options.deployTimeoutRetries
        )
    }


    private List<Map<String, String>> missingImageStreams(List<Map<String, String>> imageStreams) {
        imageStreams.findAll { !openShift.resourceExists(context.targetProject, 'ImageStream', it.name) }
    }

    private void setImageTagLatest(List<Map<String, String>> imageStreams) {
        imageStreams.each { openShift.setImageTag(context.targetProject, it.name, options.imageTag, 'latest') }
    }

}
