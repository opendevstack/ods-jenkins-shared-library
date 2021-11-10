package org.ods.orchestration.util

class StringCleanup {
    static CHARACTER_REMOVEABLE = [
       '\u00A0' : ' '
    ]

    static removeCharacters(inputString) {
        def outputString = inputString
        CHARACTER_REMOVEABLE.forEach{k,v ->
            outputString = outputString.replaceAll(k,v)
        }
        return outputString
    }
}
