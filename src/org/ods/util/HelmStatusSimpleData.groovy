package org.ods.util

import com.cloudbees.groovy.cps.NonCPS
import groovy.transform.TypeChecked

@TypeChecked
class HelmStatusSimpleData {

    String name
    String version
    String namespace
    String status
    String description
    String lastDeployed
    /**
     * Resources names by kind
     */
    Map<String, List<String> > resourcesByKind

    @SuppressWarnings(['Instanceof'])
    static HelmStatusSimpleData fromJsonObject(Object object) {
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
            } else if (!(rootObject[att] instanceof Integer)) {
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
            def hs = new HelmStatusSimpleData()
            hs.with {
                name = rootObject.name
                version = rootObject.version as String
                namespace = rootObject.namespace
                status = infoObject.status
                description = infoObject.description
                lastDeployed = infoObject["last_deployed"]
            }
            hs.resourcesByKind = resourcesByKind.collectEntries { kind, names ->
                [ kind, names.collect() ]
            } as Map<String, List<String> >
            hs
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                "Unexpected helm status information in JSON at 'info': ${ex.message}")
        }
    }

    Map<String, List<String>> getResourcesByKind(List<String> kinds) {
        resourcesByKind.subMap(kinds)
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
        result
    }

    @NonCPS
    String toString() {
        toMap().toMapString()
    }

    private static Tuple2<String, String> extractResource(
        resourceJsonObject, String context) {
        def resourceObject = ensureMap(resourceJsonObject, context)
        Map<String, Object> resource = [:]
        if (resourceObject.kind) {
            resource.kind = resourceObject.kind
        }
        if (resource.kind == "PodList") {
            return null
        }
        Map<String, Object> metadataObject = ensureMap(
            resourceObject.metadata, "${context}.metadata")
        if (metadataObject.name) {
            resource["name"] = metadataObject.name
        }

        def expected = collectMissingStringAttributes(resource, ["name", "kind"])
        handleMissingKeysOrBadTypes(expected.first, expected.second)
        return new Tuple2(resource.kind, resource.name)
    }

    @SuppressWarnings(['Instanceof'])
    @NonCPS
    private static Map ensureMap(Object obj, String context) {
        if (obj == null) {
            return [:]
        }
        if (!(obj instanceof Map)) {
            def msg = context ?
                "${context}: expected JSON object, found ${obj.getClass()}" :
                "Expected JSON object, found ${obj.getClass()}"

            throw new IllegalArgumentException(msg)
        }
        obj as Map
    }

    @SuppressWarnings(['Instanceof'])
    @NonCPS
    private static List ensureList(Object obj, String context) {
        if (obj == null) {
            return []
        }
        if (!(obj instanceof List)) {
            throw new IllegalArgumentException(
                "${context}: expected JSON array, found ${obj.getClass()}")
        }
        obj as List
    }

    @SuppressWarnings(['Instanceof'])
    private static Tuple2<List<String>, List<String>> collectMissingStringAttributes(
        Map<String, Object> jsonObject, List<String> stringAttributes) {
        def missingOrEmptyKeys = []
        def badTypes = []
        for (att in stringAttributes) {
            if (! (jsonObject.containsKey(att) && jsonObject[att])) {
                missingOrEmptyKeys << att
            } else if (!(jsonObject[att] instanceof String)) {
                badTypes << "${att}: expected String, found ${jsonObject[att].getClass()}"
            }
        }
        new Tuple2(missingOrEmptyKeys, badTypes)
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
