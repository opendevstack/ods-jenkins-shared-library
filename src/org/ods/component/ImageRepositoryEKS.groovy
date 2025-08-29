package org.ods.component

import org.ods.services.EKSService
import org.ods.util.IPipelineSteps
import org.ods.component.IContext

class ImageRepositoryEKS implements IImageRepository {

    // Constructor arguments
    private final IPipelineSteps steps
    private final IContext context
    private final EKSService eks

    private final String ocToken
    private final String awsPassword

    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'ParameterCount'])
    ImageRepositoryEKS(
        IPipelineSteps steps,
        IContext context,
        EKSService eks,
        String ocToken,
        String awsPassword
    ) {
        this.steps = steps
        this.context = context
        this.eks = eks
        this.ocToken = ocToken
        this.awsPassword = awsPassword        
    }

    public void retagImages(String targetProject, Set<String> images,  String sourceTag, String targetTag) {
        steps.echo "retagImages ${images.size()} images."
        images.each { image ->
            eks.createRepository(image)
            copyImage(image, context, sourceTag, targetTag)
        }
    }

   private int copyImage(image, context, sourceTag, targetTag) {
        String ocCredentials="jenkins:$ocToken"
        String awsCredentials="AWS:$awsPassword"
        String dockerSource="docker://${context.config.dockerRegistry}/${context.cdProject}/${image}:${sourceTag}"
        String awsTarget="docker://${eks.getECRRegistry()}/${image}:${targetTag}"

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
}
