package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.util.ILogger

@SuppressWarnings('ParameterCount')
@TypeChecked
class EKSLoginStage extends Stage {

    public final String STAGE_NAME = 'EKS Login'
    private final RolloutOpenShiftDeploymentOptions options

    @TypeChecked(TypeCheckingMode.SKIP)
    EKSLoginStage(
        def script,
        IContext context,
        Map<String, Object> config,       
        ILogger logger) {
        super(script, context, logger)
        this.options = new RolloutOpenShiftDeploymentOptions(config) 
    }
    
    // This is called from Stage#execute
    @SuppressWarnings(['AbcMetric'])
    @TypeChecked(TypeCheckingMode.SKIP)
    protected run() {
        executeCommand('aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID --profile default')
        executeCommand('aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY --profile default')
        executeCommand('aws configure set region $AWS_REGION --profile default')
        executeCommand('aws eks update-kubeconfig --region $AWS_REGION --name' + "${context.getProjectId()}-${context.getEnvironment()}")
    }

    private void executeCommand(String command) {
        return steps.sh(
            script: command,
            returnStatus: true,
            label: "Executing command: ${command}"
        ) as int
        if (status != 0) {
            script.error("Could not Login to -region $AWS_REGION --name $EKS_CLUSTER_NAME, status ${status}")
        } 
    }
}
