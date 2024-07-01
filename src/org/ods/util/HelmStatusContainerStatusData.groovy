package org.ods.util

import groovy.transform.TypeChecked

@TypeChecked
class HelmStatusContainerStatusData {

    String name
    String image
    String imageID

    @SuppressWarnings(['Instanceof'])
    HelmStatusContainerStatusData(Map<String, Object> map) {
        def missingKeys = []
        def badTypes = []

        def stringTypes = [ "name", "image", "imageID"]
        for (att in stringTypes) {
            if (!map.containsKey(att)) {
                missingKeys << att
            } else if (!(map[att] instanceof String)) {
                badTypes << "${att}: expected String, found ${map[att].getClass()}"
            }
        }
        HelmStatusData.handleMissingKeysOrBadTypes(missingKeys, badTypes)

        this.name = map['name'] as String
        this.image = map['image'] as String
        this.imageID = map['imageID'] as String
    }

}
