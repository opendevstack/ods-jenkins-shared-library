package org.ods.component

import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.PodData

/**
 * Deployment strategy that only pushes images to ECR without performing a
 * Helm upgrade or any OpenShift/EKS rollout.  Use this when images must be
 * synchronised to ECR but the actual cluster deployment is handled elsewhere.
 */
class ECROnlyDeploymentStrategy extends AbstractDeploymentStrategy {

    private final IContext context
    private final ILogger logger
    private final IImageRepository imageRepository
    private final RolloutOpenShiftDeploymentOptions options

    ECROnlyDeploymentStrategy(
        IContext context,
        Map<String, Object> config,
        IImageRepository imageRepository,
        ILogger logger
    ) {
        this.context = context
        this.logger = logger
        this.imageRepository = imageRepository
        this.options = new RolloutOpenShiftDeploymentOptions(config)
    }

    @Override
    Map<String, List<PodData>> deploy() {
        def targetProject = context.targetProject
        if (options.helmValues && options.helmValues['namespaceOverride']) {
            targetProject = options.helmValues['namespaceOverride']
            logger.info("Override namespace deployment to ${targetProject}")
        }
        logger.info("Retagging images for ${targetProject}")
        imageRepository.retagImages(targetProject, getBuiltImages(), options.imageTag, options.imageTag)
        logger.info("ECR-only deployment configured, skipping Helm/OpenShift rollout.")
        return [:]
    }

}
