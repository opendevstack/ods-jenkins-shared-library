import org.ods.component.CopyImageStage
import org.ods.component.IContext

import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger

def call(IContext context, Map config = [:]) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    // this is only for testing, because we need access to the script context :(
    if (!logger) {
        logger = new Logger (this, !!env.DEBUG)
    }

    final String STR_DOCKER_PROTOCOL = 'docker://'
    def sourceImageData = config.sourceImageUrlIncludingRegistry?.minus(STR_DOCKER_PROTOCOL)?.split('/')
    def sourceImageDataSplitLength = sourceImageData?.size()

    // [REGISTRY/]REPO/IMAGE[:TAG]
    if (sourceImageDataSplitLength >= 2) {
        config.image = sourceImageData[sourceImageDataSplitLength-1]
        config.repo = sourceImageData[sourceImageDataSplitLength-2]
        // no registry info, use internal cluster registry
        if (sourceImageDataSplitLength == 2) {
            config.registry = context.clusterRegistryAddress
        } else {
            config.registry = sourceImageData[0..sourceImageDataSplitLength-3].join('/')
        }
        config.registry = STR_DOCKER_PROTOCOL + config.registry
    } else {
        error ("Incoming ${sourceImageUrlIncludingRegistry} is wrong. " +
            "It must contain AT minimum the repo and image.\n" +
            'Desired format: [REGISTRY/]REPO/IMAGE[:TAG]')
    }

    // amend the image info with the 'latest' tag if not passed
    if (!config.image.contains(':')) {
        config.image += ':latest'
        logger.info("No image-tag found, amended to ${config.image}")
    }

    def stage = new CopyImageStage(
        this,
        context,
        config,
        ServiceRegistry.instance.get(OpenShiftService),
        logger
    )
    stage.execute()

    // // Call to aqua stage always after the image creation
    def imageName = config.image.split(':').first()
    def buildResourceName = "imported-${imageName}"
    odsComponentStageScanWithAqua(context,
        [resourceName: "${buildResourceName}", imageLabels: context.extensionImageLabels])
}
return this
