package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS

class LeVADocumentUtil {

    private LeVADocumentUtil() {
    }

    @NonCPS
    static List splitName(String name) {
        def components = name.split('-') as List
        return components
    }

    @NonCPS
    static String getTypeFromName(String name) {
        return splitName(name)[0]
    }

    @NonCPS
    static String getRepoFromName(String name) {
        return splitName(name)[1]
    }

    @NonCPS
    static boolean isFullDocument(String name) {
        return !getRepoFromName(name)
    }

    @NonCPS
    static boolean isPartialDocument(String name) {
        return !isFullDocument(name)
    }

}
