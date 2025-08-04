package org.ods.component

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


    protected String getOCTOken() {
        return steps.sh(
            script: "oc whoami -t",
            returnStdout: true
        ).trim()
    }

    protected setEKSCluster() {
        withCredentials((environmentVars.credentials.key as String).toLowerCase(),
                        (environmentVars.credentials.secret as String).toLowerCase()) {
            executeCommand('aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID --profile default')
            executeCommand('aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY --profile default')
            executeCommand("aws configure set region ${environmentVars.region} --profile default")
            executeCommand("aws eks update-kubeconfig --region ${environmentVars.region} --name" + "${context.getProjectId()}-${context.getEnvironment()}")
        }
    }

    protected String getLoginPassword() {
        return steps.sh(
            script: "aws ecr get-login-password --region ${environmentVars.region}",
            returnStdout: true
        ).trim()
    }
    
    protected String getECRRegistry() {
        return "${environmentVars.accountId}.dkr.ecr.${environmentVars.region}.amazonaws.com"
    }    

    protected boolean existRepository(String repositoryName) {
        try {
            steps.sh(
                script: "aws ecr describe-repositories --repository-names ${repositoryName} --region ${environmentVars.region}",
                returnStatus: true
            )
            return true
        } catch (Exception e) {
            return false
        }
    }

    protected void createRepository(String repositoryName) {
        executeCommand("aws ecr create-repository --repository-name ${repositoryName} --region ${environmentVars.region}")
    }

    private void executeCommand(String command) {
        return steps.sh(
            script: command,
            returnStatus: true,
            label: "Executing command: ${command}"
        ) as int
        if (status != 0) {
            script.error("Error executing ${command}, status ${status}")
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