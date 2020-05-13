package org.ods.component

import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService

class RolloutOpenShiftDeploymentStage extends Stage {

    public final String STAGE_NAME = 'Deploy to Openshift'
    private final OpenShiftService openShift
    private final JenkinsService jenkins

    RolloutOpenShiftDeploymentStage(
        def script,
        IContext context,
        Map config,
        OpenShiftService openShift,
        JenkinsService jenkins) {
        super(script, context, config)
        if (!config.deployTimeoutMinutes) {
            config.deployTimeoutMinutes = context.openshiftRolloutTimeout
        }
        if (!config.openshiftDir) {
            config.openshiftDir = 'openshift'
        }
        if (!config.tailorPrivateKeyCredentialsId) {
            config.tailorPrivateKeyCredentialsId = "${context.projectId}-cd-tailor-private-key"
        }
        if (!config.tailorSelector) {
            config.tailorSelector = "app=${context.projectId}-${componentId}"
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

    def run() {
        if (!context.environment) {
            script.echo 'Skipping for empty environment ...'
            return
        }

        def dcExists = deploymentConfigExists()
        def originalDeploymentVersion = dcExists ? getLatestVersion() : 0

        if (script.fileExists(config.openshiftDir)) {
            script.dir(config.openshiftDir) {
                jenkins.maybeWithPrivateKeyCredentials(config.tailorPrivateKeyCredentialsId) { pkeyFile ->
                    openShift.tailorApply(
                        [selector: config.tailorSelector, exclude: config.tailorExclude],
                        config.tailorParamFile,
                        config.tailorParams,
                        config.tailorPreserve,
                        pkeyFile,
                        config.tailorVerify
                    )
                }
            }
            // If DC did not exist prior to this stage, check again now as Tailor might have created it.
            if (!dcExists) {
                dcExists = deploymentConfigExists()
            }
        }

        if (!dcExists) {
            script.error "DeploymentConfig '${componentId}' does not exist."
        }

        def ownedImageStreams = openShift
            .getImagesOfDeploymentConfig(componentId)
            .findAll { context.targetProject == it.repository }
        def missingStreams = missingImageStreams(ownedImageStreams)
        if (missingStreams) {
            script.error "The following ImageStream resources  for component '${componentId}' " +
                """do not exist: '${missingStreams.collect { "${it.repository}/${it.name}" }}'."""
        }

        setImageTagLatest(ownedImageStreams)

        if (getLatestVersion() > originalDeploymentVersion) {
            script.echo "Rollout of deployment for '${componentId}' has been triggered automatically."
        } else {
            startRollout()
        }
        script.timeout(time: config.deployTimeoutMinutes) {
            watchRollout()
        }

        def latestVersion = getLatestVersion()
        def replicationController = "${componentId}-${latestVersion}"
        def rolloutStatus = getRolloutStatus(replicationController)
        if (rolloutStatus != 'complete') {
            script.error "Deployment #${latestVersion} failed with status '${rolloutStatus}', " +
                'please check the error in the OpenShift web console.'
        } else {
            script.echo "Deployment #${latestVersion} successfully rolled out."
        }
        def pod = getPodDataForRollout(replicationController)
        context.addDeploymentToArtifactURIs (componentId, pod)

        return pod
    }

    private boolean deploymentConfigExists() {
        openShift.resourceExists('DeploymentConfig', componentId)
    }

    private List<Map<String, String>> missingImageStreams(List<Map<String, String>> imageStreams) {
        imageStreams.findAll { !openShift.resourceExists('ImageStream', it.name) }
    }

    private void setImageTagLatest(List<Map<String, String>> imageStreams) {
        imageStreams.each { openShift.setImageTag(it.name, context.tagversion, 'latest') }
    }

    private void startRollout() {
        openShift.startRollout(componentId)
    }

    private void watchRollout() {
        openShift.watchRollout(componentId)
    }

    private int getLatestVersion() {
        openShift.getLatestVersion(componentId)
    }

    private String getRolloutStatus(String replicationController) {
        openShift.getRolloutStatus(replicationController)
    }

    private Map getPodDataForRollout(String replicationController) {
        openShift.getPodDataForDeployment(replicationController)
    }
}
