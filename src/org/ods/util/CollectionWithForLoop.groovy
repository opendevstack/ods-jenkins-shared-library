package org.ods.util

import com.cloudbees.groovy.cps.NonCPS

class CollectionWithForLoop {

    @NonCPS
    static List findAll(Collection c, Closure filter) {
        def results = []

        for (def i = 0; i < c.size(); i++) {
            if (filter(c[i])) {
                results.add(c[i])
            }
        }
        return results
    }

    @NonCPS
    static collectEntries(Collection c, Closure key, Closure value) {
        Map results = [:]

        for (def i = 0; i < c.size(); i++) {
            results[key(c[i])] = value(c[i])
        }

        return results
    }

}
