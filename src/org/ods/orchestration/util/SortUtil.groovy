package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS

class SortUtil {

    @NonCPS
    // Sorts a collection of maps in the order of the keys in properties
    static List<Map> sortIssuesByProperties(Collection<Map> issues, List keys) {
        return issues.sort { issue ->
            issue.subMap(keys).values().collect { value ->
                // we use zero padding in Jira key number to allow sorting of Strings in numeric order
                value.split('-').last().padLeft(12, '0')
            }.join('-')
        }
    }

    @NonCPS
    // Sorts a collection of maps in the order of the jira key
    static List<Map> sortIssuesByKey(Collection<Map> issues) {
        return sortIssuesByProperties(issues, ['key'])
    }

    @NonCPS
    // Sorts a collection of maps in the order of the keys in properties
    static List<Map> sortHeadingNumbers(Collection<Map> issueKeys, String numberKey) {
        return issueKeys.sort { number ->
            // we use zero padding to allow sorting of Strings in numeric order
            number[numberKey].split('\\.').collect { value ->
                value.padLeft(12, '0')
            }.join('-')
        }
    }

}
