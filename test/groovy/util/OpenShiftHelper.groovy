package util

import groovy.json.JsonSlurper

import static org.ods.services.OpenShiftService.DEPLOYMENTCONFIG_KIND
import static org.ods.services.OpenShiftService.DEPLOYMENT_KIND

class OpenShiftHelper {

    static isDeploymentKind(String kind) {
        return kind in [DEPLOYMENTCONFIG_KIND, DEPLOYMENT_KIND]
    }

    static def validateResourceParams(script, project, resources) {
        if (project) {
            assert script =~ /\s-n\s+\Q${project}\E(?:\s|$)/
        } else {
            assert !(script =~ /\s-n\b/)
        }
        assert script =~ /\s\Q${resources}\E(?:\s|$)/
        return true
    }

    static def validateLabels(script, project, resources, labels, selector = null) {
        assert script =~ /^\s*oc\s+label\s/
        assert script =~ /\s--overwrite\b/
        validateResourceParams(script, project, resources)
        if (selector) {
            assert script =~ /\s-l\s+\Q${selector}\E(?:\s|$)/
        } else {
            assert !(script =~ /\s-l\b/)
        }
        labels.each { key, value ->
            if (value != null) {
                assert script =~ /\s\Q${resources}\E\s(?:.*?\s)?\Q${key}='${value}'\E(?:\s|$)/
            } else {
                assert script =~ /\s\Q${resources}\E\s(?:.*?\s)?\Q${key}-\E(?:\s|$)/
            }
        }
        return true
    }

    static def validatePatch(script, project, resources, patch, path = null) {
        assert script =~ /^\s*oc\s+patch\s.*?--type='merge'/
        validateResourceParams(script, project, resources)
        def matcher = script =~ /\s-p\s+'([^']*)'/
        assert matcher.size() == 1
        def jsonPatch = matcher[0][1]
        def actualPatch = new JsonSlurper().parseText(jsonPatch)
        if (path) {
            path.substring(1).split('/').each {
                actualPatch = actualPatch[it]
            }
        }
        assert patch == actualPatch
        return true
    }
}
