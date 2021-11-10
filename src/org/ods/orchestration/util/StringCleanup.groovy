package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS

class StringCleanup {
    static CHARACTER_REMOVEABLE = [
       '\u00A0' : ' '
    ]

    @NonCPS
    static removeCharacters(inputString) {
        def outputString = inputString
        CHARACTER_REMOVEABLE.forEach{k,v ->
            outputString = outputString.replaceAll(k,v)
        }
        return outputString
    }
}
