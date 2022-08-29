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

        if (config.verifyTLS == null) {
            config.verifyTLS = true
        }

        this.options = new CopyImageOptions(config)
        this.openShift = openShift
        this.logger = logger
    }

    // This is called from Stage#execute if the branch being built is eligible.
    @SuppressWarnings(['AbcMetric'])
    @TypeChecked(TypeCheckingMode.SKIP)
    protected run() {
        logger.info("Copy the image ${options.sourceImageUrlIncludingRegistry}!")
        final String STR_DOCKER_PROTOCOL = 'docker://'

        logger.info("Resolved source Image data: " +
            "${this.options.sourceImageUrlIncludingRegistry},\n" +
            "importing into: " +
            "${STR_DOCKER_PROTOCOL}${context.clusterRegistryAddress}/${context.cdProject}/${this.options.image}")

        // to make this work, we need a target image stream first
        openShift.findOrCreateImageStream("${context.cdProject}", "${this.options.image.split(':').first()}")

        // read the target registry auth (for this project's -cd project ..)
        def targetInternalRegistryToken = steps.readFile '/run/secrets/kubernetes.io/serviceaccount/token'

        def sourcetoken = options.sourceCredential ? "--src-creds ${options.sourceCredential}" : ''

        int status = copyImage(sourcetoken, targetInternalRegistryToken, STR_DOCKER_PROTOCOL)
        if (status != 0) {
            script.error("Could not copy `${this.options.sourceImageUrlIncludingRegistry}', status ${status}")
        }

        // Extract image name and tag for use in compliance later
        def imageName = this.options.image.split(':').first()
        def imageTag = this.options.image.split(':').last()

        def internalImageRef = openShift.getImageReference(context.cdProject, imageName, imageTag)

        logger.info("!!! Image successfully imported into " +
            "${context.clusterRegistryAddress}/${context.cdProject}/${this.options.image},\n" +
            "details: ${internalImageRef}")

        // Add the imported image to the artifacts so they can show up in compliance docs
        def buildResourceName = "imported-${imageName}"
        context.addBuildToArtifactURIs("${buildResourceName}",
            [image: internalImageRef, source: options.sourceImageUrlIncludingRegistry])

        // If this option is set will additionally tag the image in the target namespace, not just the cd namespace
        if (options.tagIntoTargetEnv) {
            openShift.findOrCreateImageStream(context.targetProject, imageName)
            openShift.importImageTagFromProject(
                context.targetProject, imageName, context.cdProject,
                imageTag, imageTag
            )
            logger.info("!!! Image ${this.options.image} tagged into ${context.targetProject}")
        }
    }

    private int copyImage(sourcetoken, targetInternalRegistryToken, String dockerProtocol) {
        int status = steps.sh(
            script: """
                skopeo copy --src-tls-verify=${this.options.verifyTLS} ${sourcetoken} \
                ${this.options.registry}/${this.options.repo}/${this.options.image} \
                --dest-creds openshift:${targetInternalRegistryToken} \
                ${dockerProtocol}${context.clusterRegistryAddress}/${context.cdProject}/${this.options.image} \
                --dest-tls-verify=${this.options.verifyTLS}
            """,
            returnStatus: true,
            label: "Copy image ${this.options.repo}/${this.options.image}"
        ) as int
        return status
    }

    protected String stageLabel() {
        return "${STAGE_NAME} " +
            "(${context.componentId}) " +
            "${this.options.registry}/${this.options.repo}/${this.options.image}'"
    }
}
