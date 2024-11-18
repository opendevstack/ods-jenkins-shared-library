package org.ods.util

import com.cloudbees.groovy.cps.NonCPS

class HelmStatus {

    private String name
    private String version
    private String namespace
    private String status
    private String description
    private String lastDeployed
    /**
     * Resources names by kind
     */
    private Map<String, List<String> > resourcesByKind

    @NonCPS
    static HelmStatus fromJsonObject(Object object) {
        try {
            def rootObject = ensureMap(object, "")
            def infoObject = ensureMap(rootObject.info, "info")

            // validation of missing keys or types
            def missingKeys = []
            def badTypes = []
            def expectedStringAttribsForRoot = collectMissingStringAttributes(rootObject,
                ["name", "namespace"])
            def expectedStringAttribsForInfo = collectMissingStringAttributes(infoObject,
                ["status", "description", "last_deployed"])
            missingKeys.addAll(expectedStringAttribsForRoot.first)
            missingKeys.addAll(expectedStringAttribsForInfo.first)
            badTypes.addAll(expectedStringAttribsForRoot.second)
            badTypes.addAll(expectedStringAttribsForInfo.second)
            def att = "version"
            if (!rootObject.containsKey(att)) {
                missingKeys << att
            } else if (!(rootObject[att] in Integer)) {
                badTypes << "${att}: expected Integer, found ${rootObject[att].getClass()}"
            }
            handleMissingKeysOrBadTypes(missingKeys, badTypes)
            def resourcesObject = ensureMap(infoObject.resources, "info.resources")
            // All resources are in an json object which organize resources by keys
            // Examples are  "v1/Cluster", "v1/ConfigMap"  "v1/Deployment", "v1/Pod(related)"
            // For these keys the map contains list of resources.
            Map<String, List<String>> resourcesByKind = [:] .withDefault { [] }
            for (entry in resourcesObject.entrySet()) {
                def key = entry.key as String
                def resourceList = ensureList(entry.value, "info.resources.${key}")
                resourceList.eachWithIndex { resourceJsonObject, i ->
                    def resourceContext = "info.resources.${key}.[${i}]"
                    def resource = extractResource(resourceJsonObject, resourceContext)
                    if (resource) {
                        resourcesByKind[resource.first] << resource.second
                    }
                }
            }
            def hs = new HelmStatus()

            hs.name = rootObject.name
            hs.version = rootObject.version as String
            hs.namespace = rootObject.namespace
            hs.status = infoObject.status
            hs.description = infoObject.description
            hs.lastDeployed = infoObject["last_deployed"]

            hs.resourcesByKind = resourcesByKind.collectEntries { kind, names ->
                [ kind, names.collect() ]
            } as Map<String, List<String> >
            return hs
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                "Unexpected helm status information in JSON at 'info': ${ex.message}")
        }
    }

    @NonCPS
    Map<String, List<String>> getResources() {
        // Return a cloned copy of the map
        return resourcesByKind.collectEntries { kind, names ->
            [ kind, names.collect() ]
        }
    }

    @NonCPS
    String getName() {
        return name
    }

    @NonCPS
    void setName(String name) {
        this.name = name
    }

    @NonCPS
    String getVersion() {
        return version
    }

    @NonCPS
    void setVersion(String version) {
        this.version = version
    }

    @NonCPS
    String getNamespace() {
        return namespace
    }

    @NonCPS
    void setNamespace(String namespace) {
        this.namespace = namespace
    }

    @NonCPS
    String getStatus() {
        return status
    }

    @NonCPS
    void setStatus(String status) {
        this.status = status
    }

    @NonCPS
    String getDescription() {
        return description
    }

    @NonCPS
    void setDescription(String description) {
        this.description = description
    }

    @NonCPS
    String getLastDeployed() {
        return lastDeployed
    }

    @NonCPS
    void setLastDeployed(String lastDeployed) {
        this.lastDeployed = lastDeployed
    }

    @NonCPS
    Map<String, List<String>> getResourcesByKind() {
        return resourcesByKind
    }

    @NonCPS
    void setResourcesByKind(Map<String, List<String>> resourcesByKind) {
        this.resourcesByKind = resourcesByKind
    }

    @NonCPS
    Map<String, Object> toMap() {
        def result = [
            name: name,
            version: version,
            namespace: namespace,
            status: status,
            description: description,
            lastDeployed: lastDeployed,
            resourcesByKind: resourcesByKind,
        ]
        return result
    }


    @NonCPS
    String toString() {
        return toMap().toMapString()
    }

    @NonCPS
    private static Tuple2<String, String> extractResource(
        resourceJsonObject, String context) {
        def resourceObject = ensureMap(resourceJsonObject, context)
        Map<String, Object> resource = [:]
        if (resourceObject?.kind) {
            resource.kind = resourceObject.kind
        }
        if (resource?.kind == "PodList") {
            return null
        }
        Map<String, Object> metadataObject = ensureMap(
            resourceObject.metadata, "${context}.metadata")
        if (metadataObject?.name) {
            resource["name"] = metadataObject.name
        }

        def expected = collectMissingStringAttributes(resource, ["name", "kind"])
        handleMissingKeysOrBadTypes(expected.first, expected.second)
        return new Tuple2(resource.kind, resource.name)
    }

    @NonCPS
    private static Map ensureMap(Object obj, String context) {
        if (obj == null) {
            return [:]
        }
        if (!(obj in Map)) {
            def msg = context ?
                "${context}: expected JSON object, found ${obj.getClass()}" :
                "Expected JSON object, found ${obj.getClass()}"

            throw new IllegalArgumentException(msg)
        }
        return obj as Map
    }

    @NonCPS
    private static List ensureList(Object obj, String context) {
        if (obj == null) {
            return []
        }
        if (!(obj in List)) {
            throw new IllegalArgumentException(
                "${context}: expected JSON array, found ${obj.getClass()}")
        }
        return obj as List
    }


    @NonCPS
    private static Tuple2<List<String>, List<String>> collectMissingStringAttributes(
        Map<String, Object> jsonObject, List<String> stringAttributes) {
        def missingOrEmptyKeys = []
        def badTypes = []
        for (att in stringAttributes) {
            if (! (jsonObject.containsKey(att) && jsonObject[att])) {
                missingOrEmptyKeys << att
            } else if (!(jsonObject[att] in String)) {
                badTypes << "${att}: expected String, found ${jsonObject[att].getClass()}"
            }
        }
        return new Tuple2(missingOrEmptyKeys, badTypes)
    }

    @NonCPS
    private static void handleMissingKeysOrBadTypes(List<String> missingKeys, List<String> badTypes) {
        if (missingKeys || badTypes) {
            def msgs = []
            if (missingKeys) {
                msgs << "Missing keys: ${missingKeys.join(', ')}"
            }
            if (badTypes) {
                msgs << "Bad types: ${badTypes.join(', ')}"
            }
            throw new IllegalArgumentException(msgs.join("."))
        }
    }

}
