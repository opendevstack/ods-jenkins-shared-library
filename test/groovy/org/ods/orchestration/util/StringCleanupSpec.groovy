package org.ods.orchestration.util

import spock.lang.Specification

class StringCleanupSpec extends Specification {

    def "Remove nonbreakable white space"() {
        given: 'String with nonbreakable white space'
        def inputString = '\u00A0This\u00A0string\u00A0has\u00A0wrong\u00A0spaces\u00A0'
        def CHARACTER_REMOVEABLE = [
            '\u00A0': ' ',
        ]

        when: 'We remove the characters'
        def outputString = StringCleanup.removeCharacters(inputString, CHARACTER_REMOVEABLE)

        then: 'The characters were removed'
        outputString == ' This string has wrong spaces '
    }
}
