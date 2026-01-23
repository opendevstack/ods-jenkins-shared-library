package org.ods.services

import org.ods.util.IPipelineSteps
import org.ods.component.IContext
import org.ods.util.ILogger

class EKSService {
    // Constructor arguments
    private final IPipelineSteps steps
    private final IContext context
    private final Map<String, Object> awsEnvironmentVars
    private final ILogger logger

    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'ParameterCount'])
    EKSService(
        IPipelineSteps steps,
        IContext context,
        Map<String, Object> awsEnvironmentVars,
        ILogger logger
    ) {
        this.steps = steps
        this.context = context
        this.awsEnvironmentVars = awsEnvironmentVars
        this.logger = logger
    }

    protected setEKSCluster() {
        withCredentials((awsEnvironmentVars.credentials.key as String).toLowerCase(),
                        (awsEnvironmentVars.credentials.secret as String).toLowerCase()) {
            executeCommand('aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID --profile default')
            executeCommand('aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY --profile default')
            executeCommand("aws configure set region ${awsEnvironmentVars.region} --profile default")
            executeCommand("aws eks list-clusters")
            executeCommand("aws eks update-kubeconfig --region ${awsEnvironmentVars.region} --name ${awsEnvironmentVars.eksCluster}")
            executeCommand("kubectl create namespace ${context.getProjectId()}-${context.getEnvironment()}", false)
        }
    }

    private void executeCommand(String command, boolean showError = true) {
        def status = steps.sh(
            script: command,
            returnStatus: true,
            label: "Executing command: ${command}"
        ) as int
        if (status != 0 && showError) {
            steps.error("Error executing ${command}, status ${status}")
        } 
    }
    
    private withCredentials(String awsAccessKeyId, String awsSecretAccessKey, Closure block) {
        steps.withCredentials([
            steps.string(credentialsId: awsAccessKeyId, variable: 'AWS_ACCESS_KEY_ID'),
            steps.string(credentialsId: awsSecretAccessKey, variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {
            block(steps.env.AWS_ACCESS_KEY_ID, steps.env.AWS_SECRET_ACCESS_KEY)
        }
    }
}