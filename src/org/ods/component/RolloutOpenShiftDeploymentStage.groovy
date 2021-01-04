package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.util.ILogger
import org.ods.util.PodData

@SuppressWarnings('ParameterCount')
@TypeChecked
class RolloutOpenShiftDeploymentStage extends Stage {

    public final String STAGE_NAME = 'Deploy to OpenShift'
    private final List<String> DEPLOYMENT_KINDS = [
        OpenShiftService.DEPLOYMENT_KIND, OpenShiftService.DEPLOYMENTCONFIG_KIND,
    ]
    private final OpenShiftService openShift
    private final JenkinsService jenkins
    private final RolloutOpenShiftDeploymentOptions options

    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity'])
    @TypeChecked(TypeCheckingMode.SKIP)
    RolloutOpenShiftDeploymentStage(
        def script,
        IContext context,
        Map<String, Object> config,
        OpenShiftService openShift,
        JenkinsService jenkins,
        ILogger logger) {
        super(script, context, logger)
        if (!config.selector) {
            config.selector = context.selector
        }
        if (!config.imageTag) {
            config.imageTag = context.shortGitCommit
        }
        if (!config.deployTimeoutMinutes) {
            config.deployTimeoutMinutes = context.openshiftRolloutTimeout ?: 5
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
            config.helmValuesFiles = []
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

        this.options = new RolloutOpenShiftDeploymentOptions(config)
        this.openShift = openShift
        this.jenkins = jenkins
    }

    protected run() {
        if (!context.environment) {
            logger.warn 'Skipping because of empty (target) environment ...'
            return
        }

        def deploymentResources = openShift.getResourcesForComponent(
            context.targetProject, DEPLOYMENT_KINDS, options.selector
        )
        if (context.triggeredByOrchestrationPipeline
            && deploymentResources.containsKey(OpenShiftService.DEPLOYMENT_KIND)) {
            steps.error "Deployment resources cannot be used in the orchestration pipeline yet."
            return
        }
        def originalDeploymentVersions = fetchOriginalVersions(deploymentResources)

        // Tag images which have been built in this pipeline from cd project into target project
        retagImages(context.targetProject, getBuiltImages())

        def refreshResources = false
        if (steps.fileExists("${options.chartDir}/Chart.yaml")) {
            if (context.triggeredByOrchestrationPipeline) {
                steps.error "Helm cannot be used in the orchestration pipeline yet."
                return
            }
            helmUpgrade(context.targetProject)
            refreshResources = true
        } else if (steps.fileExists(options.openshiftDir)) {
            tailorApply(context.targetProject)
            refreshResources = true
        }

        if (refreshResources) {
            deploymentResources = openShift.getResourcesForComponent(
                context.targetProject, DEPLOYMENT_KINDS, options.selector
            )
        }
        return rollout(deploymentResources, originalDeploymentVersions)
    }

    protected String stageLabel() {
        if (options.selector != context.selector) {
            return "${STAGE_NAME} (${options.selector})"
        }
        STAGE_NAME
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private Set<String> getBuiltImages() {
        context.buildArtifactURIs.builds.keySet()
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

    private void helmUpgrade(String targetProject) {
        steps.dir(options.chartDir) {
            jenkins.maybeWithPrivateKeyCredentials(options.helmPrivateKeyCredentialsId) { String pkeyFile ->
                if (pkeyFile) {
                    steps.sh(script: "gpg --import ${pkeyFile}", label: 'Import private key into keyring')
                }
                options.helmValues.imageTag = options.imageTag
                openShift.helmUpgrade(
                    targetProject,
                    options.helmReleaseName,
                    options.helmValuesFiles,
                    options.helmValues,
                    options.helmDefaultFlags,
                    options.helmAdditionalFlags,
                    options.helmDiff
                )
            }
        }
    }

    private void retagImages(String targetProject, Set<String> images) {
        images.each { image ->
            findOrCreateImageStream(targetProject, image)
            openShift.importImageTagFromProject(
                targetProject, image, context.cdProject, options.imageTag, options.imageTag
            )
        }
    }

    private findOrCreateImageStream(String targetProject, String image) {
        try {
            openShift.findOrCreateImageStream(targetProject, image)
        } catch (Exception ex) {
            steps.error "Could not find/create ImageStream ${image} in ${targetProject}. Error was: ${ex}"
        }
    }

    // rollout returns a map like this:
    // [
    //    'DeploymentConfig/foo': [[podName: 'foo-a', ...], [podName: 'foo-b', ...]],
    //    'Deployment/bar': [[podName: 'bar-a', ...]]
    // ]
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

                def podData = rolloutDeployment(resourceKind, resourceName, originalVersion)

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

    private Map<String, Map<String, Integer>> fetchOriginalVersions(Map<String, List<String>> deploymentResources) {
        def originalVersions = [:]
        deploymentResources.each { resourceKind, resourceNames ->
            if (!originalVersions.containsKey(resourceKind)) {
                originalVersions[resourceKind] = [:]
            }
            resourceNames.each { resourceName ->
                originalVersions[resourceKind][resourceName] = openShift.getRevision(
                    context.targetProject, resourceKind, resourceName
                )
            }
        }
        originalVersions
    }

    private List<Map<String, String>> missingImageStreams(List<Map<String, String>> imageStreams) {
        imageStreams.findAll { !openShift.resourceExists(context.targetProject, 'ImageStream', it.name) }
    }

    private void setImageTagLatest(List<Map<String, String>> imageStreams) {
        imageStreams.each { openShift.setImageTag(context.targetProject, it.name, options.imageTag, 'latest') }
    }

}
