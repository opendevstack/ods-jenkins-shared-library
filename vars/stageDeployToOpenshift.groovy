def call(def context, def tailorSelector = '') {
  stage('Deploy to Openshift') {
    if (!context.environment) {
      echo "Skipping for empty environment ..."
      return
    }

    if (utilsTailor.enabled()) {

      def tailorVersion = utilsTailor.getVersion()
      echo "Using Tailor (${tailorVersion}) to deploy changes."

      utilsTailor.execRunUpdate(context, tailorSelector)

      def deploymentConfigExists = utilsOpenshift.deploymentConfigExists(
        context,
        context.targetProject,
        context.componentId
      )
      if (deploymentConfigExists) {
        def configTriggerEnabled = utilsOpenshift.automaticConfigChangeTriggerEnabled(
          context,
          context.targetProject,
          context.componentId
        )
        rolloutDeployment(
          context,
          context.targetProject,
          context.componentId,
          context.openshiftRolloutTimeout,
          configTriggerEnabled
        )
        // NOTE: This stage does not support multiple deployments via Tailor. If
        // you need to control multiple deployments from one repository, you'll
        // need to build your own stage, using the building blocks provided by
        // utilsOpenshift and utilsTailor.
      } else {
        echo "Update applied, but no DeploymentConfig ${context.componentId} found in ${context.targetProject}."
      }

    } else {

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

      rolloutDeployment(
        context,
        context.targetProject,
        context.componentId,
        context.openshiftRolloutTimeout,
        imageTriggerEnabled
      )

    }
  }
}

def rolloutDeployment(def context, def targetProject, def componentId, def rolloutTimeout, def automaticRollout) {
  def deployment = utilsOpenshift.rolloutDeployment(
    context,
    targetProject,
    componentId,
    rolloutTimeout,
    !automaticRollout
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

return this
