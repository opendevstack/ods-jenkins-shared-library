package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS

class StringCleanup {

    @NonCPS
    static removeCharacters(String inputString, Map characters) {
        def replacements = characters.collectEntries { k, v ->
            def toReplace = k in String ? k.charAt(0) : k
            return [(toReplace): v]
        }
        return inputString.collectReplacements { replacements[it] }
    }

}
