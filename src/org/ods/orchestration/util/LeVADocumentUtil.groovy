package org.ods.orchestration.util

class LeVADocumentUtil {
    private LeVADocumentUtil() {
    }

    static List splitName(String name) {
        def components = name.split('-') as List
        return components
    }

    static String getTypeFromName(String name) {
        return splitName(name)[0]
    }

    static String getRepoFromName(String name) {
        return splitName(name)[1]
    }

    static boolean isFullDocument(String name) {
        return !getRepoFromName(name)
    }

    static boolean isPartialDocument(String name) {
        return !isFullDocument(name)
    }
}
