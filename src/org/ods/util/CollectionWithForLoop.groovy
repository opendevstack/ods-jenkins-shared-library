package org.ods.util

class CollectionWithForLoop {

    static List findAll(Collection c, Closure filter) {
        def results = []

        for (def i = 0; i < c.size(); i++) {
            if (filter(c[i])) {
                results.add(c[i])
            }
        }
        return results
    }

    static Map collectEntries(Collection c, Closure key, Closure value) {
        Map results = [:]

        for (def i = 0; i < c.size(); i++) {
            results[key(c[i])] = value(c[i])
        }

        return results
    }

}
