package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.openshift.OpenShiftResourceMetadata
import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import org.ods.util.PipelineSteps
import org.ods.util.PodData

class TailorDeploymentStrategy extends AbstractDeploymentStrategy implements IDeploymentStrategy {

    private final List<String> DEPLOYMENT_KINDS = [
        OpenShiftService.DEPLOYMENT_KIND, OpenShiftService.DEPLOYMENTCONFIG_KIND,
    ]

    protected def script
    protected Map<String, Object> config
    protected final JenkinsService jenkins
    protected ILogger logger

    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'ParameterCount'])
    TailorDeploymentStrategy(
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
            return []
        }

        def deploymentResources = openShift.getResourcesForComponent(
            context.targetProject, DEPLOYMENT_KINDS, options.selector
        )
        if (context.triggeredByOrchestrationPipeline
            && deploymentResources.containsKey(OpenShiftService.DEPLOYMENT_KIND)) {
            steps.error "Deployment resources cannot be used in a NON HELM orchestration pipeline."
            return []
        }
        def originalDeploymentVersions = fetchOriginalVersions(deploymentResources)

        def refreshResources = false
        def paused = true
        try {
            openShift.bulkPause(context.targetProject, deploymentResources)

            // Tag images which have been built in this pipeline from cd project into target project
            retagImages(context.targetProject, getBuiltImages())

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
        } finally {
            if (paused) {
                openShift.bulkResume(context.targetProject, DEPLOYMENT_KINDS, options.selector)
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
        Map<String, List<String>> deploymentResources,
        Map<String, Map<String, Integer>> originalVersions) {

        def rolloutData = [:]
        deploymentResources.each { resourceKind, resourceNames ->
            resourceNames.each { resourceName ->
                def originalVersion = 0
                if (originalVersions.containsKey(resourceKind)) {
                    originalVersion = originalVersions[resourceKind][resourceName] ?: 0
                }

                def podData = [:]
                podData = rolloutDeployment(resourceKind, resourceName, originalVersion)
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

    void addDeploymentArtifacts(String resourceName) {
        context.addDeploymentToArtifactURIs("${resourceName}-deploymentMean",
            [
                'type'           : 'tailor', 'selector': options.selector,
                'tailorSelectors': [selector: options.tailorSelector, exclude: options.tailorExclude],
                'tailorParamFile': options.tailorParamFile,
                'tailorParams'   : options.tailorParams,
                'tailorPreserve' : options.tailorPreserve,
                'tailorVerify'   : options.tailorVerify
            ])
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

}
