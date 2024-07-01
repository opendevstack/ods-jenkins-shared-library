package org.ods.util

import groovy.transform.TypeChecked

//Relevant helm status resource data we want to capture. This is inside the info data.
@TypeChecked
class HelmStatusResourceData {

    String apiVersion
    String kind
    String metadataName
    // for kind "Pod" containerStatusData may be present
    List<HelmStatusContainerStatusData> containerStatuses = []

    @SuppressWarnings(['Instanceof'])
    HelmStatusResourceData(Map<String, Object> map) {
        def missingKeys = []
        def badTypes = []

        def stringTypes = [ "apiVersion", "kind", "metadataName"]
        for (att in stringTypes) {
            if (!map.containsKey(att)) {
                missingKeys << att
            } else if (!(map[att] instanceof String)) {
                badTypes << "${att}: expected String, found ${map[att].getClass()}"
            }
        }

        HelmStatusData.handleMissingKeysOrBadTypes(missingKeys, badTypes)

        this.apiVersion = map['apiVersion'] as String
        this.kind = map['kind'] as String
        this.metadataName = map['metadataName'] as String
        if (map.containsKey('containerStatuses')) {
            this.containerStatuses = (map['containerStatuses'] as List<HelmStatusContainerStatusData>)
        }
    }

}
