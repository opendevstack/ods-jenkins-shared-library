package org.ods.component

import org.ods.services.EKSService
import org.ods.util.IPipelineSteps
import org.ods.component.IContext

class ImageRepositoryECR implements IImageRepository {

    // Constructor arguments
    private final IPipelineSteps steps
    private final IContext context
    private final EKSService eks

    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'ParameterCount'])
    ImageRepositoryECR(
        IPipelineSteps steps,
        IContext context,
        Map<String, Object> awsEnvironmentVars
    ) {
        this.steps = steps
        this.context = context
        this.awsEnvironmentVars = awsEnvironmentVars
    }

    public void retagImages(String targetProject, Set<String> images,  String sourceTag, String targetTag) {
        images.each { image ->
            createRepository(image)
            copyImage(image, context, sourceTag, targetTag)
        }
    }

    private void createRepository(String repositoryName) {
        executeCommand("aws ecr create-repository --repository-name ${repositoryName} --region ${awsEnvironmentVars.region}", false)
    }

    private int copyImage(image, context, sourceTag, targetTag) {
        String ocCredentials="jenkins:${getOCToken()}"
        String awsCredentials="AWS:${getAWSPassword()}"
        String dockerSource="docker://${context.config.dockerRegistry}/${context.cdProject}/${image}:${sourceTag}"
        String awsTarget="docker://${getECRRegistry()}/${image}:${targetTag}"

        return steps.sh(
            script: """
                skopeo copy \
                --src-tls-verify=false --src-creds "${ocCredentials}"\
                --dest-tls-verify=false --dest-creds "${awsCredentials}"\
                $dockerSource $awsTarget
            """,
            returnStatus: true,
            label: "Copy image to awsTarget ${awsTarget}"
        ) as int
    }

    private String getOCToken() {
        return steps.sh(
            script: "oc whoami -t",
            returnStdout: true
        ).trim()
    }

    private String getAWSPassword() {
        return steps.sh(
            script: "aws ecr get-login-password --region ${awsEnvironmentVars.region}",
            returnStdout: true
        ).trim()
    }

    private String getECRRegistry() {
        return "${awsEnvironmentVars.account}.dkr.ecr.${awsEnvironmentVars.region}.amazonaws.com"
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
}
