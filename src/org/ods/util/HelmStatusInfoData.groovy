package org.ods.util

import groovy.transform.TypeChecked

//Relevant helm status info data we want to capture
@TypeChecked
class HelmStatusInfoData {

    // deployment status - see `helm status --help` for full list
    // Example: "deployed"
    String status
    // description of the release (can be completion message or error message)
    // Example:  "Upgrade complete"
    String description
    // last-deployed field.
    // Example: "2024-03-04T15:21:09.34520527Z"
    String lastDeployed

    Map<String, List<HelmStatusResourceData>> resources

    @SuppressWarnings(['Instanceof'])
    HelmStatusInfoData(Map<String, Object> map) {
        def missingKeys = []
        def badTypes = []

        def stringTypes = [ "status", "description", "lastDeployed"]
        for (att in stringTypes) {
            if (!map.containsKey(att)) {
                missingKeys << att
            } else if (!(map[att] instanceof String)) {
                badTypes << "${att}: expected String, found ${map[att].getClass()}"
            }
        }
        if (!map.containsKey('resources')) {
            missingKeys << 'resources'
        } else if (!(map['resources'] instanceof Map)) {
            badTypes << "resources: expected Map, found ${map['resources'].getClass()}"
        }

        HelmStatusData.handleMissingKeysOrBadTypes(missingKeys, badTypes)

        this.status = map['status'] as String
        this.description = map['description'] as String
        this.lastDeployed = map['lastDeployed'] as String
        this.resources = map['resources'] as Map
    }

}
