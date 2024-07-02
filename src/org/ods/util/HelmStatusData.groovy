package org.ods.util

import com.cloudbees.groovy.cps.NonCPS
import groovy.transform.PackageScope
import groovy.transform.TypeChecked

// Relevant data returned by helm status --show-resources -o json
@TypeChecked
class HelmStatusData {

    // release name
    String name
    String namespace

    HelmStatusInfoData info

    // version is an integer but we map it to a string.
    String version

    @SuppressWarnings(['IfStatementBraces'])
    @NonCPS
    static HelmStatusData fromJsonObject(Object object) {
        try {
            def jsonObject = ensureMap(object, "")

            Map<String,Object> status = [:]

            // Constructors of classes receiving the data catches missing keys or unexpected types and
            // report them in summary as IllegalArgumentException.
            if (jsonObject.name) status.name = jsonObject.name
            if (jsonObject.version) status.version = jsonObject.version
            if (jsonObject.namespace) status.namespace = jsonObject.namespace

            def infoObject = ensureMap(jsonObject.info, "info")
            Map<String, Object> info = [:]

            if (infoObject.status) info.status = infoObject.status
            if (infoObject.description) info.description = infoObject.description
            if (infoObject['last_deployed']) info.lastDeployed = infoObject['last_deployed']

            def resourcesObject = ensureMap(infoObject.resources, "info.resources")
            // All resources are in an json object which organize resources by keys
            // Examples are  "v1/Cluster", "v1/ConfigMap"  "v1/Deployment", "v1/Pod(related)"
            // For these keys the map contains list of resources.
            Map<String, List<HelmStatusResourceData>> resourcesMap = [:]
            for (entry in resourcesObject.entrySet()) {
                def key = entry.key as String
                def resourceList = ensureList(entry.value, "info.resources.${key}")
                def resources = []
                resourceList.eachWithIndex { resourceJsonObject, i ->
                    def resourceData = fromJsonObjectHelmStatusResource(
                        resourceJsonObject,
                        "info.resources.${key}.[${i}]")
                    if (resourceData != null) {
                        resources << resourceData
                    }
                }
                resourcesMap.put(key, resources)
            }
            info.resources = resourcesMap
            status.info = new HelmStatusInfoData(info)
            new HelmStatusData(status)
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Unexpected helm status information in JSON at 'info': ${e.getMessage()}")
        }
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

    @SuppressWarnings(['IfStatementBraces', 'Instanceof'])
    @NonCPS
    private static HelmStatusResourceData fromJsonObjectHelmStatusResource(
        resourceJsonObject, String context) {
        def resourceObject = ensureMap(resourceJsonObject, context)
        Map<String, Object> resource = [:]
        if (resourceObject.apiVersion) resource.apiVersion = resourceObject.apiVersion
        if (resourceObject.kind) resource.kind = resourceObject.kind
        Map<String, Object> metadataObject = ensureMap(
            resourceObject.metadata, "${context}.metadata")
        if (metadataObject.name) resource.metadataName = metadataObject.name
        if (resource.kind == "PodList") {
            return null
        }
        if (resource.kind == "Pod") {
            def statusObject = ensureMap(resourceObject.status,
                "${context}.status")
            List<HelmStatusContainerStatusData> containerStatuses = []
            def containerStatusesJsonArray = ensureList(statusObject.containerStatuses,
                "${context}.status.containerStatuses")
            containerStatusesJsonArray.eachWithIndex { cs, int csi ->
                def cso = ensureMap(cs, "${context}.status.containerStatuses[${csi}]")
                Map<String, Object> containerStatus = [:]
                if (cso.name) containerStatus.name = cso.name
                if (cso.image) containerStatus.image = cso.image
                if (cso.imageID) containerStatus.imageID = cso.imageID
                containerStatuses << new HelmStatusContainerStatusData(cso)
            }
            resource.containerStatuses = containerStatuses
        }
        try {
            new HelmStatusResourceData(resource)
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Unexpected helm status JSON at '${context}': ${e.getMessage()}")
        }
    }

    @SuppressWarnings(['PublicMethodsBeforeNonPublicMethods'])
    @NonCPS
    static @PackageScope void handleMissingKeysOrBadTypes(List<String> missingKeys, List<String> badTypes) {
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

    @SuppressWarnings(['Instanceof'])
    HelmStatusData(Map<String, Object> map) {
        def missingKeys = []
        def badTypes = []

        def stringTypes = [ "name", "namespace"]
        for (att in stringTypes) {
            if (!map.containsKey(att)) {
                missingKeys << att
            } else if (!(map[att] instanceof String)) {
                badTypes << "${att}: expected String, found ${map[att].getClass()}"
            }
        }
        if (!map.containsKey('info')) {
            missingKeys << 'info'
        } else if (!(map['info'] instanceof HelmStatusInfoData)) {
            badTypes << "info: expected HelmStatusInfoData, found ${map['info'].getClass()}"
        }

        if (!map.containsKey('version')) {
            missingKeys << 'version'
        }
        handleMissingKeysOrBadTypes(missingKeys, badTypes)

        this.name = map['name'] as String
        this.namespace = map['namespace'] as String
        this.info = map['info'] as HelmStatusInfoData
        this.version = map['version'].toString()
    }

}
