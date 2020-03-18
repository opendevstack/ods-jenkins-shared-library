import org.ods.build_service.OpenShiftService
import org.ods.build_service.ServiceRegistry

def call(def context) {

  withStage('Deploy to Openshift', context) {

    if (!context.environment) {
      echo "Skipping for empty environment ..."
      return
    }

    def openShift = ServiceRegistry.instance.get(OpenShiftService)

    openShift.checkDeploymentConfigExistence()
    openShift.imageStreamExists()
    def imageTriggerEnabled = openShift.automaticImageChangeTriggerEnabled()
    openShift.setLatestImageTag()
    openShift.rolloutDeployment(!imageTriggerEnabled)
  }
}

return this
