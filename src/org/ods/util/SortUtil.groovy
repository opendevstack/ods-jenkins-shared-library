package org.ods.util

import com.cloudbees.groovy.cps.NonCPS

class SortUtil {

    @NonCPS
    // Sorts a collection of maps in the order of the keys in properties
    static List<Map> sortIssuesByProperties(Collection<Map> issues, List properties) {
        return issues.sort { issue ->
            issue.subMap(properties).values().join("-")
        }
    }
}
