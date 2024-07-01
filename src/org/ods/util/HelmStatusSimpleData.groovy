package org.ods.util

import com.cloudbees.groovy.cps.NonCPS
import groovy.transform.TypeChecked

@TypeChecked
class HelmStatusSimpleData {

    String releaseName
    String releaseRevision
    String namespace
    String deployStatus
    String deployDescription
    String lastDeployed
    List<HelmStatusResource> resources

    static HelmStatusSimpleData fromJsonObject(Object jsonObject) {
        from(HelmStatusData.fromJsonObject(jsonObject))
    }

    @SuppressWarnings(['NestedForLoop'])
    static HelmStatusSimpleData from(HelmStatusData status) {
        def simpleResources = []
        for (resourceList in status.info.resources.values()) {
            for (hsr in resourceList) {
                simpleResources << new HelmStatusResource(kind: hsr.kind, name: hsr.metadataName)
            }
        }
        new HelmStatusSimpleData(
            releaseName: status.name,
            releaseRevision: status.version,
            namespace: status.namespace,
            deployStatus: status.info.status,
            deployDescription: status.info.description,
            lastDeployed: status.info.lastDeployed,
            resources: simpleResources,)
    }

    Map<String, List<String>> getResourcesByKind(List<String> kinds) {
        def deploymentResources = resources.findAll { it.kind in kinds }
        Map<String, List<String>> resourcesByKind = [:]
        deploymentResources.each {
            if (!resourcesByKind.containsKey(it.kind)) {
                resourcesByKind[it.kind] = []
            }
            resourcesByKind[it.kind] << it.name
        }
        resourcesByKind
    }

    @NonCPS
    Map<String, Object> toMap() {
        def result = [
            releaseName: releaseName,
            releaseRevision: releaseRevision,
            namespace: namespace,
            deployStatus: deployStatus,
            deployDescription: deployDescription,
            lastDeployed: lastDeployed,
            resources: resources.collect { [kind: it.kind, name: it.name] }
        ]
        result
    }

    @NonCPS
    String toString() {
        toMap().toMapString()
    }

}
