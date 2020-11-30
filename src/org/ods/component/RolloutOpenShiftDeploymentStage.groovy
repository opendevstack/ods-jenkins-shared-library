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
        if (!config.resourceName) {
            config.resourceName = context.componentId
        }
        if (!config.deploymentKind) {
            config.deploymentKind = OpenShiftService.DEPLOYMENTCONFIG_KIND
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
            config.tailorSelector = "app=${context.projectId}-${context.componentId}"
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
        if (context.triggeredByOrchestrationPipeline && config.deploymentKind == OpenShiftService.DEPLOYMENT_KIND) {
            script.error "Deployment resources cannot be used in the orchestration pipeline yet."
            return
        }
        if (!context.environment) {
            logger.warn 'Skipping because of empty (target) environment ...'
            return
        }

        def dExists = deploymentExists()
        def originalDeploymentVersion = 0
        if (dExists) {
            originalDeploymentVersion = openShift.getRevision(
                context.targetProject, config.deploymentKind, config.resourceName
            )
        }

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
            // If deployment did not exist prior to this stage,
            // check again now as Tailor might have created it.
            if (!dExists) {
                dExists = deploymentExists()
            }
        }

        if (!dExists) {
            script.error "${config.deploymentKind} '${config.resourceName}' does not exist. " +
                'Verify that you have setup the OpenShift resource correctly ' +
                'and/or set the "deploymentKind"/"resourceName" option of the pipeline stage appropriately.'
        }

        def ownedImageStreams = openShift
            .getImagesOfDeployment(context.targetProject, config.deploymentKind, config.resourceName)
            .findAll { context.targetProject == it.repository }
        def missingStreams = missingImageStreams(ownedImageStreams)
        if (missingStreams) {
            script.error "The following ImageStream resources  for ${config.deploymentKind} '${config.resourceName}' " +
                """do not exist: '${missingStreams.collect { "${it.repository}/${it.name}" }}'. """ +
                'Verify that you have setup the OpenShift resources correctly ' +
                'and/or set the "resourceName" option of the pipeline stage appropriately.'
        }

        setImageTagLatest(ownedImageStreams)

        String podManager
        try {
            podManager = openShift.rollout(
                context.targetProject,
                config.deploymentKind,
                config.resourceName,
                originalDeploymentVersion,
                config.deployTimeoutMinutes
            )
        } catch (ex) {
            script.error ex.message
        }

        def podData = openShift.getPodDataForDeployment(
            context.targetProject,
            config.deploymentKind,
            podManager,
            config.deployTimeoutRetries
        )
        // TODO: Once the orchestration pipeline can deal with multiple replicas,
        // update this to store multiple pod artifacts.
        def pod = podData[0]
        context.addDeploymentToArtifactURIs(config.resourceName, pod)

        return pod
    }

    protected String stageLabel() {
        if (config.resourceName != context.componentId) {
            return "${STAGE_NAME} (${config.resourceName})"
        }
        STAGE_NAME
    }

    private boolean deploymentExists() {
        openShift.resourceExists(context.targetProject, config.deploymentKind, config.resourceName)
    }

    private List<Map<String, String>> missingImageStreams(List<Map<String, String>> imageStreams) {
        imageStreams.findAll { !openShift.resourceExists(context.targetProject, 'ImageStream', it.name) }
    }

    private void setImageTagLatest(List<Map<String, String>> imageStreams) {
        imageStreams.each { openShift.setImageTag(context.targetProject, it.name, config.imageTag, 'latest') }
    }

}
