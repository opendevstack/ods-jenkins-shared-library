package org.ods.component

import org.ods.services.OpenShiftService

class RolloutOpenShiftDeploymentStage extends Stage {
  protected String STAGE_NAME = 'Deploy to Openshift'
  protected OpenShiftService openShift

  RolloutOpenShiftDeploymentStage(def script, IContext context, Map config, OpenShiftService openShift) {
    super(script, context, config)
    if (!config.deployTimeoutMinutes) {
      config.deployTimeoutMinutes = context.openshiftRolloutTimeout
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
      script.error "DeploymentConfig '${context.componentId}' does not exist."
    }

    def isExists = imageStreamExists()
    if (!isExists) {
      script.error "ImageStream '${context.componentId}' does not exist."
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
      script.error "Could not get latest version of DeploymentConfig '${context.componentId}'."
    }
    def replicationController = "${context.componentId}-${latestVersion}"
    def rolloutStatus = getRolloutStatus(replicationController)
    if (rolloutStatus != "complete") {
      script.error "Deployment #${latestVersion} failed with status '${rolloutStatus}', please check the error in the OpenShift web console."
    } else {
      script.echo "Deployment #${latestVersion} successfully rolled out."
      context.addArtifactURI("OCP Deployment Id", replicationController)
    }
  }

  private boolean deploymentConfigExists() {
    openShift.resourceExists('DeploymentConfig', context.componentId)
  }

  private boolean imageStreamExists() {
    openShift.resourceExists('ImageStream', context.componentId)
  }

  private boolean automaticImageChangeTriggerEnabled() {
    openShift.automaticImageChangeTriggerEnabled(context.componentId)
  }

  private void setImageTagLatest() {
    openShift.setImageTag(context.componentId, context.tagversion, 'latest')
  }

  private void startRollout() {
    openShift.startRollout(context.componentId)
  }

  private void watchRollout() {
    openShift.watchRollout(context.componentId)
  }

  private String getLatestVersion() {
    openShift.getLatestVersion(context.componentId)
  }

  private String getRolloutStatus(String replicationController) {
    openShift.getRolloutStatus(replicationController)
  }
}
