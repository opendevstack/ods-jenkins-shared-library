package org.ods.component

import org.ods.util.IPipelineSteps
import org.ods.util.ILogger

class ImageECR implements IImageRepository {

    // Constructor arguments
    private final IPipelineSteps steps
    private final IContext context
    private final ILogger logger
    private final Map<String, Object> awsEnvironmentVars
    private final String ocToken

    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'ParameterCount'])
    ImageECR(
        IPipelineSteps steps,
        IContext context,
        ILogger logger,
        Map<String, Object> awsEnvironmentVars
    ) {
        this.steps = steps
        this.context = context
        this.logger = logger
        this.awsEnvironmentVars = awsEnvironmentVars
        this.ocToken = getOCToken()
    }

    public void retagImages(String targetProject, Set<String> images,  String sourceTag, String targetTag) {
        logger.debug("retagImages called with targetProject=${targetProject}, images=${images}")
        images.each { image ->
            createRepository(image)
            copyImage(image, context, sourceTag, targetTag)
        }
    }

    private void createRepository(String repositoryName) {
        def createRepoCmd = "aws ecr describe-repositories --repository-names"
        executeCommand("${createRepoCmd} ${repositoryName} --region ${awsEnvironmentVars.region}", false)
    }

    private int copyImage(image, context, sourceTag, targetTag) {
        String ocCredentials = "jenkins:${this.ocToken}"
        String awsCredentials = "AWS:${getAWSPassword()}"
        String dockerSource = "docker://${context.config.dockerRegistry}/${context.cdProject}/${image}:${sourceTag}"
        String awsTarget = "docker://${getECRRegistry()}/${image}:${targetTag}"

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
