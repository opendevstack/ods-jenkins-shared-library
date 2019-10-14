package org.ods

class OpenshiftUtils {

    static boolean environmentExists(def script, String name) {
        def statusCode = script.sh(
            script:"oc project ${name} &> /dev/null",
            label: "check if OCP environment ${name} exists",
            returnStatus: true
        )
        return statusCode == 0
    }
}
