package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS

class StringCleanup {

    @NonCPS
    static removeCharacters(inputString, characters) {
        def outputString = inputString
        characters.forEach { k, v ->
            outputString = outputString.replaceAll(k, v)
        }
        return outputString
    }
}
