package org.ods.component

import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.util.ILogger

@SuppressWarnings('ParameterCount')
class RolloutOpenShiftDeploymentStage extends Stage {

    public final String STAGE_NAME = 'Deploy to OpenShift'
    private final OpenShiftService openShift
    private final JenkinsService jenkins

    RolloutOpenShiftDeploymentStage(
        def script,
        IContext context,
        Map config,
        OpenShiftService openShift,
        JenkinsService jenkins,
        ILogger logger) {
        super(script, context, config, logger)
        if (!config.selector) {
            config.selector = "app=${context.projectId}-${context.componentId}"
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
        if (!config.openshiftDir) {
            config.openshiftDir = 'openshift'
        }
        if (!config.tailorPrivateKeyCredentialsId) {
            config.tailorPrivateKeyCredentialsId = "${context.projectId}-cd-tailor-private-key"
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
        this.openShift = openShift
        this.jenkins = jenkins
    }

    protected run() {
        if (!context.environment) {
            logger.warn 'Skipping because of empty (target) environment ...'
            return
        }
        def deploymentKinds = [OpenShiftService.DEPLOYMENT_KIND, OpenShiftService.DEPLOYMENTCONFIG_KIND]

        def deploymentResources = openShift.getResourcesForComponent(
            context.targetProject, deploymentKinds, config.selector
        )
        if (context.triggeredByOrchestrationPipeline
            && deploymentResources.containsKey(OpenShiftService.DEPLOYMENT_KIND)) {
            script.error "Deployment resources cannot be used in the orchestration pipeline yet."
            return
        }
        def originalDeploymentVersions = fetchOriginalVersions(deploymentResources)

        if (script.fileExists(config.openshiftDir)) {
            script.dir(config.openshiftDir) {
                jenkins.maybeWithPrivateKeyCredentials(config.tailorPrivateKeyCredentialsId) { pkeyFile ->
                    openShift.tailorApply(
                        context.targetProject,
                        [selector: config.tailorSelector, exclude: config.tailorExclude],
                        config.tailorParamFile,
                        config.tailorParams,
                        config.tailorPreserve,
                        pkeyFile,
                        config.tailorVerify
                    )
                }
            }
        }

        deploymentResources = openShift.getResourcesForComponent(
            context.targetProject, deploymentKinds, config.selector
        )
        return rollout(deploymentResources, originalDeploymentVersions)
    }

    private Map<String,List<Map>> rollout(
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

                if (!rolloutData.containsKey(resourceKind)) {
                    rolloutData[resourceKind] = [:]
                }
                rolloutData[resourceKind][resourceName] = podData
                // TODO: Once the orchestration pipeline can deal with multiple replicas,
                // update this to store multiple pod artifacts.
                // TODO: Potential conflict if resourceName is duplicated between
                // Deployment and DeploymentConfig resource.
                def pod = podData[0]
                context.addDeploymentToArtifactURIs(resourceName, pod)
            }
        }
        rolloutData
    }

    private List<Map> rolloutDeployment(String resourceKind, String resourceName, int originalVersion) {
        def ownedImageStreams = openShift
            .getImagesOfDeployment(context.targetProject, resourceKind, resourceName)
            .findAll { context.targetProject == it.repository }
        def missingStreams = missingImageStreams(ownedImageStreams)
        if (missingStreams) {
            script.error "The following ImageStream resources  for ${resourceKind} '${resourceName}' " +
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
                config.deployTimeoutMinutes
            )
        } catch (ex) {
            script.error ex.message
        }

        return openShift.getPodDataForDeployment(
            context.targetProject,
            resourceKind,
            podManager,
            config.deployTimeoutRetries
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
        imageStreams.each { openShift.setImageTag(context.targetProject, it.name, config.imageTag, 'latest') }
    }

}
