package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.OpenShiftService
import org.ods.util.PodData

abstract class AbstractDeploymentStrategy implements IDeploymentStrategy {

    protected final List<String> DEPLOYMENT_KINDS = [
        OpenShiftService.DEPLOYMENT_KIND, OpenShiftService.DEPLOYMENTCONFIG_KIND,
    ]

    @Override
    abstract Map<String, List<PodData>> deploy()

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

    protected findOrCreateImageStream(String targetProject, String image) {
        try {
            openShift.findOrCreateImageStream(targetProject, image)
        } catch (Exception ex) {
            steps.error "Could not find/create ImageStream ${image} in ${targetProject}. Error was: ${ex}"
        }
    }

    protected void retagImages(String targetProject, Set<String> images) {
        images.each { image ->
            findOrCreateImageStream(targetProject, image)
            openShift.importImageTagFromProject(
                targetProject, image, context.cdProject, options.imageTag, options.imageTag
            )
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected Set<String> getBuiltImages() {
        context.buildArtifactURIs.builds.keySet().findAll { it ->
            !it.startsWith("imported-")
        }
    }
}
