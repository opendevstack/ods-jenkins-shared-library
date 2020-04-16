package org.ods.component

import org.ods.services.OpenShiftService

class RolloutOpenShiftDeploymentStage extends Stage {
  public final String STAGE_NAME = 'Deploy to Openshift'
  private OpenShiftService openShift

  RolloutOpenShiftDeploymentStage(def script, IContext context, Map config, OpenShiftService openShift) {
    super(script, context, config)
    if (!config.deployTimeoutMinutes) {
      config.deployTimeoutMinutes = context.openshiftRolloutTimeout
    }
    if (!config.imageStreams) {
      config.imageStreams = [componentId]
    }
    this.openShift = openShift
  }

  def run() {
    if (!context.environment) {
      script.echo "Skipping for empty environment ..."
      return
    }

    def dcExists = deploymentConfigExists()
    if (!dcExists) {
      script.error "DeploymentConfig '${componentId}' does not exist."
    }

    def isExists = imageStreamExists()
    if (!isExists) {
      script.error "ImageStream '${componentId}' does not exist."
    }

    def imageTriggerEnabled = automaticImageChangeTriggerEnabled()

    setImageTagLatest()

    if (!imageTriggerEnabled) {
      startRollout()
    }
    script.timeout(time: config.deployTimeoutMinutes) {
      watchRollout()
    }

    def latestVersion = getLatestVersion()
    if (!latestVersion) {
      script.error "Could not get latest version of DeploymentConfig '${componentId}'."
    }
    def replicationController = "${componentId}-${latestVersion}"
    def rolloutStatus = getRolloutStatus(replicationController)
    if (rolloutStatus != "complete") {
      script.error "Deployment #${latestVersion} failed with status '${rolloutStatus}', please check the error in the OpenShift web console."
    } else {
      script.echo "Deployment #${latestVersion} successfully rolled out."
    }
    def pod = getPodDataForRollout(replicationController)
    script.echo "Pod ${pod} for #${latestVersion}"
    context.addDeploymentToArtifactURIs (componentId, pod)
    
    return pod
  }

  private boolean deploymentConfigExists() {
    openShift.resourceExists('DeploymentConfig', componentId)
  }

  private boolean imageStreamExists() {
    config.imageStreams.each { imageStreamName ->
      openShift.resourceExists('ImageStream', imageStreamName)
    }
  }

  private boolean automaticImageChangeTriggerEnabled() {
    openShift.automaticImageChangeTriggerEnabled(componentId)
  }

  private void setImageTagLatest() {
    config.imageStreams.each { imageStreamName ->
      openShift.setImageTag(imageStreamName, context.tagversion, 'latest')
    }
  }

  private void startRollout() {
    openShift.startRollout(componentId)
  }

  private void watchRollout() {
    openShift.watchRollout(componentId)
  }

  private String getLatestVersion() {
    openShift.getLatestVersion(componentId)
  }

  private String getRolloutStatus(String replicationController) {
    openShift.getRolloutStatus(replicationController)
  }
  
  private Map getPodDataForRollout(String replicationController) {
    openShift.getPodDataForDeployment(componentId, replicationController)
  }
}
