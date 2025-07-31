import org.ods.component.RolloutOpenShiftDeploymentStage
import org.ods.component.IContext
import org.ods.component.EKSLoginStage

import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger

def call(IContext context, Map config = [:]) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    // this is only for testing, because we need access to the script context :(
    if (!logger) {
        logger = new Logger (this, !!env.DEBUG)
    }

    // Get the current token of OC, as later we login to EKS and we need the token to copy the images
    def currentOCToken = sh(script: "oc whoami -t >& /dev/null", label: "Get OpenShift Token", returnStdout: true)
    // This step should be executed before the EKSLoginStage, so that the OpenShift token is available
    def stageLogin = new EKSLoginStage(
        this,
        context,
        config,
        logger
    )
    this.withCredentials([
            string(credentialsId: "${context.cdProject}-aws-region", variable: 'AWS_REGION')
            string(credentialsId: "${context.cdProject}-aws-account-id", variable: 'AWS_ACCOUNT_ID'), 
            string(credentialsId: "${context.cdProject}-aws-access-key-id-dev", variable: 'AWS_ACCESS_KEY_ID'),
            string(credentialsId: "${context.cdProject}-aws-secret-access-key-dev", variable: 'AWS_SECRET_ACCESS_KEY')]) {        
        stageLogin.execute()
    }

    // TODO: 
    // sent currentOCToken to RolloutOpenShiftDeploymentStage 
    // and copy the images from OpenShift to EKS
    // change retagImages for something compatible with EKS
    return new RolloutOpenShiftDeploymentStage(
        this,
        context,
        config,
        ServiceRegistry.instance.get(OpenShiftService),
        ServiceRegistry.instance.get(JenkinsService),
        logger
    ).execute()
}
return this
