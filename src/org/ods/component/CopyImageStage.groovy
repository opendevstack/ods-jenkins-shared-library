package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.OpenShiftService
import org.ods.util.ILogger

@SuppressWarnings('ParameterCount')
@TypeChecked
class CopyImageStage extends Stage {

    public final String STAGE_NAME = 'Copy Container Image'
    private final OpenShiftService openShift
    private final ILogger logger
    private final CopyImageOptions options

    @TypeChecked(TypeCheckingMode.SKIP)
    CopyImageStage(
        def script,
        IContext context,
        Map<String, Object> config,
        OpenShiftService openShift,
        ILogger logger) {
        super(script, context, logger)
        this.options = new CopyImageOptions(config)
        this.openShift = openShift
        this.logger = logger
    }

    // This is called from Stage#execute if the branch being built is eligible.
    protected run() {
        logger.info("Copy the image ${options.sourceImageUrlIncludingRegistry}!")
        String STR_DOCKER_PROTOCOL = 'docker://'

        logger.info("Resolved source Image data: ${this.options.sourceImageUrlIncludingRegistry} into ${this.options},\n" +
            "importing into: ${STR_DOCKER_PROTOCOL}${context.clusterRegistryAddress}/${context.cdProject}/${this.options.image}")

        // to make this work, we need a target image stream first
        openShift.findOrCreateImageStream("${context.cdProject}", "${this.options.image.split(':').first()}")

        // read the target registry auth (for this project's -cd project ..)
        def targetInternalRegistryToken = steps.readFile '/run/secrets/kubernetes.io/serviceaccount/token'

        def sourcetoken = options.sourceCredential ? "--src-creds ${options.sourceCredential}" : ''

        try {
            steps.sh """
            set +x
            skopeo copy --src-tls-verify=false ${sourcetoken} \
            ${this.options.registry}/${this.options.repo}/${this.options.image} \
            --dest-creds openshift:${targetInternalRegistryToken} \
            ${STR_DOCKER_PROTOCOL}${context.clusterRegistryAddress}/${context.cdProject}/${this.options.image} \
            --dest-tls-verify=false
            """
        } catch (err) {
            throw err
        }

        def imageName = this.options.image.split(':').first()
        def imageTag = this.options.image.split(':').last()

        def internalImageRef = openShift.getImageReference(context.cdProject, imageName, imageTag)

        logger.info("!!! Image successfully imported into ${context.clusterRegistryAddress}/${context.cdProject}/${this.options.image},\n" +
            "details: ${internalImageRef}")

        def buildResourceName = "imported-${imageName}"

        context.addBuildToArtifactURIs("${buildResourceName}",
            [image: internalImageRef, source: options.sourceImageUrlIncludingRegistry])

        if (options.tagIntoTargetEnv) {

            openShift.findOrCreateImageStream(context.targetProject, imageName)
            openShift.importImageTagFromProject(
                context.targetProject, imageName, context.cdProject,
                imageTag, imageTag
            )
            logger.info("!!! Image ${this.options.image} tagged into ${context.targetProject}")
        }
    }

    protected String stageLabel() {
        return "${STAGE_NAME} (${context.componentId})"
        STAGE_NAME
    }
}
