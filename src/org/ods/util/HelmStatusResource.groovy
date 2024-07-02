package org.ods.util

import com.cloudbees.groovy.cps.NonCPS
import groovy.transform.TypeChecked

@TypeChecked
class HelmStatusResource {

    String kind
    String name

    @NonCPS
    Map toMap() {
        [
            kind: kind,
            name: name,
        ]
    }

    @NonCPS
    String toString() {
        toMap().toMapString()
    }

}
