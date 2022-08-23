package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.OpenShiftService
import org.ods.util.IPipelineSteps

class AbstractDeploymentStrategy {
    protected IContext context
    protected OpenShiftService openShift
    protected RolloutOpenShiftDeploymentOptions options
    protected IPipelineSteps steps

    protected AbstractDeploymentStrategy(){};

    protected void retagImages(String targetProject, Set<String> images) {
        images.each { image ->
            findOrCreateImageStream(targetProject, image)
            openShift.importImageTagFromProject(
                targetProject, image, context.cdProject, options.imageTag, options.imageTag
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

    @TypeChecked(TypeCheckingMode.SKIP)
    protected Set<String> getBuiltImages() {
        context.buildArtifactURIs.builds.keySet().findAll { it ->
            !it.startsWith("imported-")
        }
    }

    protected Map<String, Map<String, Integer>> fetchOriginalVersions(Map<String, List<String>> deploymentResources) {
        def originalVersions = [:]
        deploymentResources.each { resourceKind, resourceNames ->
            if (!originalVersions.containsKey(resourceKind)) {
                originalVersions[resourceKind] = [:]
            }
            resourceNames.each { resourceName ->
                originalVersions[resourceKind][resourceName] = openShift.getRevision(
                    context.targetProject, resourceKind, resourceName
                )
            }
        }
        originalVersions
    }
}
