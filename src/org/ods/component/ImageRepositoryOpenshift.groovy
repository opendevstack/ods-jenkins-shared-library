package org.ods.component

import org.ods.services.OpenShiftService
import org.ods.util.IPipelineSteps

class ImageRepositoryOpenshift implements IImageRepository {

    // Constructor arguments
    private final IPipelineSteps steps
    private final IContext context
    private final OpenShiftService openShift

    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'ParameterCount'])
    ImageRepositoryOpenshift(
        IPipelineSteps steps,
        IContext context,
        OpenShiftService openShift
    ) {
        this.steps = steps
        this.context = context
        this.openShift = openShift
    }

    public void retagImages(String targetProject, Set<String> images,  String sourceTag, String targetTag) {
        images.each { image ->
            findOrCreateImageStream(targetProject, image)
            openShift.importImageTagFromProject(
                targetProject, image, context.cdProject, sourceTag, targetTag
            )
        }
    }

    private findOrCreateImageStream(String targetProject, String image) {
        try {
            openShift.findOrCreateImageStream(targetProject, image)
        } catch (Exception ex) {
            steps.error "Could not find/create ImageStream ${image} in ${targetProject}. Error was: ${ex}"
        }
    }
}
