import org.ods.service.OpenShiftService
import org.ods.service.ServiceRegistry

def call(def context, Map buildArgs = [:], Map imageLabels = [:]) {

  withStage('Build Openshift Image', context) {

    if (!context.environment) {
      echo "Skipping for empty environment ..."
      return
    }

    def openShift = ServiceRegistry.instance.get(OpenShiftService)

    def buildId = openShift.buildImage(buildArgs, imageLabels)
    context.addArtifactURI("OCP Build Id", buildId)

    def newImageSha = openShift.getCurrentImageSha()
    context.addArtifactURI("OCP Docker image", newImageSha)
  }
}

return this
