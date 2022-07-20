package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.util.ILogger

@SuppressWarnings('ParameterCount')
@TypeChecked
class CopyImageStage extends Stage {

    public final String STAGE_NAME = 'Copy Container Image'
    private final OpenShiftService openShift
    private final JenkinsService jenkins
    private final ILogger logger
    private final CopyImageOptions options

    @TypeChecked(TypeCheckingMode.SKIP)
    CopyImageStage(
        def script,
        IContext context,
        Map<String, Object> config,
        OpenShiftService openShift,
        JenkinsService jenkins,
        ILogger logger) {
        super(script, context, logger)
        // If user did not explicitly define which branches to build images for,
        // build images for all branches which are mapped (deployed) to an environment.
        // In orchestration pipelines, always build the image.
        if (!config.containsKey('branches') && !config.containsKey('branch')) {
            if (context.triggeredByOrchestrationPipeline) {
                config.branch = context.gitBranch
            } else {
                config.branches = context.branchToEnvironmentMapping.keySet().toList()
            }
        }
        if (!config.resourceName) {
            config.resourceName = context.componentId
        }
        // if (!config.imageTag) {
        //     config.imageTag = context.shortGitCommit
        // }
        // if (!config.imageLabels) {
        //     config.imageLabels = [:]
        // }
        // if (context.extensionImageLabels) {
        //     config.imageLabels << context.extensionImageLabels
        // }
        // if (!config.buildArgs) {
        //     config.buildArgs = [:]
        // }
        // if (!config.buildTimeoutMinutes) {
        //     config.buildTimeoutMinutes = context.openshiftBuildTimeout
        // }
        // if (!config.buildTimeoutRetries) {
        //     config.buildTimeoutRetries = context.openshiftBuildTimeoutRetries
        // }
        // if (!config.dockerDir) {
        //     config.dockerDir = context.dockerDir
        // }

        this.options = new CopyImageOptions(config)
        this.openShift = openShift
        this.jenkins = jenkins
        this.logger = logger
    }

    // This is called from Stage#execute if the branch being built is eligible.
    protected run() {
        logger.info("Copy the image ${options.sourceImageUrlIncludingRegistry}!")
        importImageIntoProject(context, options.sourceImageUrlIncludingRegistry, options.tagIntoTargetEnv, options.sourceCredential)
    }

    def importImageIntoProject(def context, String sourceImageUrlIncludingRegistry, boolean tagIntoTargetEnv = false, String sourceCredential = null) {
        String STR_DOCKER_PROTOCOL = 'docker://'
        def sourceImageData = sourceImageUrlIncludingRegistry?.minus(STR_DOCKER_PROTOCOL)?.split('/')
        def sourceImageDataSplitLength = sourceImageData?.size()

        // [REGISTRY/]REPO/IMAGE[:TAG]
        def sourceImageInfo = [ : ]
        if (sourceImageDataSplitLength >= 2) {
            sourceImageInfo.image = sourceImageData[sourceImageDataSplitLength-1]
            sourceImageInfo.repo = sourceImageData[sourceImageDataSplitLength-2]
            // no registry info, use internal cluster registry
            if (sourceImageDataSplitLength == 2) {
                sourceImageInfo.registry = context.clusterRegistryAddress
            } else {
                sourceImageInfo.registry = sourceImageData[0..sourceImageDataSplitLength-3].join('/')
            }
            sourceImageInfo.registry = STR_DOCKER_PROTOCOL + sourceImageInfo.registry
        } else {
            error ("Incoming ${sourceImageUrlIncludingRegistry} is wrong. It must contain AT minimum the repo and image.\n" +
                                        'Desired format: [REGISTRY/]REPO/IMAGE[:TAG]')
        }

        // amend the image info with the 'latest' tag if not passed
        if (!sourceImageInfo.image.contains(':')) {
            sourceImageInfo.image += ':latest'
            logger.info("No image-tag found, amended to ${sourceImageInfo.image}")
        }

        logger.info("Resolved source Image data: ${sourceImageUrlIncludingRegistry} into ${sourceImageInfo},\n" +
            "importing into: ${STR_DOCKER_PROTOCOL}${context.clusterRegistryAddress}/${context.cdProject}/${sourceImageInfo.image}")

        // to make this work, we need a target image stream first
        def openShiftService = ServiceRegistry.instance.get(OpenShiftService)
        openShiftService.findOrCreateImageStream("${context.cdProject}", "${sourceImageInfo.image.split(':').first()}")

        // read the target registry auth (for this project's -cd project ..)
        def targetInternalRegistryToken = readFile '/run/secrets/kubernetes.io/serviceaccount/token'

        def sourcetoken = sourceCredential ? "--src-creds ${sourceCredential}" : ''

        try {
            sh """
            set +x
            skopeo copy --src-tls-verify=false ${sourcetoken} \
            ${sourceImageInfo.registry}/${sourceImageInfo.repo}/${sourceImageInfo.image} \
            --dest-creds openshift:${targetInternalRegistryToken} \
            ${STR_DOCKER_PROTOCOL}${context.clusterRegistryAddress}/${context.cdProject}/${sourceImageInfo.image} \
            --dest-tls-verify=false
            """
        } catch (err) {
            throw err
        }

        def imageName = sourceImageInfo.image.split(':').first()
        def imageTag = sourceImageInfo.image.split(':').last()

        def internalImageRef = openShiftService.getImageReference(context.cdProject, imageName, imageTag)

        logger.info("!!! Image successfully imported into ${context.clusterRegistryAddress}/${context.cdProject}/${sourceImageInfo.image},\n" +
            "details: ${internalImageRef}")

        def buildResourceName = "imported-${imageName}"

        context.addBuildToArtifactURIs("${buildResourceName}",
            [image: internalImageRef, source: sourceImageUrlIncludingRegistry])

        if (tagIntoTargetEnv) {

            openShiftService.findOrCreateImageStream(context.targetProject, imageName)
            openShiftService.importImageTagFromProject(
                context.targetProject, imageName, context.cdProject,
                imageTag, imageTag
            )
            logger.info("!!! Image ${sourceImageInfo.image} tagged into ${context.targetProject}")
        }
        logger.warn("NOT SCANNING IMAGES -- odsComponentStageScanWithAqua(context, [resourceName: \"${buildResourceName}\", imageLabels : context.extensionImageLabels])")
        // this needs to go into the new stage ...  (similar to the odsBuild...stage)
        // odsComponentStageScanWithAqua(context, [resourceName: "${buildResourceName}", imageLabels : context.extensionImageLabels])

    }

    protected String stageLabel() {
        if (options.resourceName != context.componentId) {
            return "${STAGE_NAME} (${options.resourceName})"
        }
        STAGE_NAME
    }
}
