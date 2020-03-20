def call(def context) {
  stage('Deploy to Openshift') {
    if (!context.environment) {
      echo "Skipping for empty environment ..."
      return
    }

    def deploymentConfigExists = utilsOpenshift.deploymentConfigExists(
      context,
      context.targetProject,
      context.componentId
    )
    if (!deploymentConfigExists) {
      error "No DeploymentConfig ${context.componentId} found in ${context.targetProject}."
    }
    def imageStreamExists = utilsOpenshift.imageStreamExists(
      context,
      context.targetProject,
      context.componentId
    )
    if (!imageStreamExists) {
      error "No ImageStream ${context.componentId} found in ${context.targetProject}."
    }

    def imageTriggerEnabled = utilsOpenshift.automaticImageChangeTriggerEnabled(
      context,
      context.targetProject,
      context.componentId
    )

    utilsOpenshift.setLatestImageTag(
      context,
      context.targetProject,
      context.componentId,
      context.tagversion
    )

    def deployment = utilsOpenshift.rolloutDeployment(
      context,
      context.targetProject,
      context.componentId,
      context.openshiftRolloutTimeout,
      !imageTriggerEnabled
    )

    if (deployment == null) {
      error "Deployment failed, please check the error in the OCP console."
    } else if (deployment.status.toLowerCase() != "complete") {
      error "Deployment ${deployment.id} failed with status '${deployment.status}', please check the error in the OCP console."
    } else {
      echo "Deployment ${deployment.id} successfully rolled out."
      context.addArtifactURI("OCP Deployment Id", deployment.id)
    }
  }
}

return this
