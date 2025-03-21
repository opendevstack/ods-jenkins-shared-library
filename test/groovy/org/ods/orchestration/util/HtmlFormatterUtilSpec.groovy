package org.ods.orchestration.util

import spock.lang.Specification

class HtmlFormatterUtilSpec extends Specification {

    def "Maps and Lists converted to HTML <ul> or to 'EMPTY_DEFAULT' when empty"() {
        given: 'String with nonbreakable white space'

        def emptyList = []
        def list = ['v1', 'v2']

        def emptyMap = [:]
        def map = [
            'k1': '',
            'k2': [],
            'k3': 'v3',
            'k4': ['v4.1', 'v4.2']
        ]

        when: 'We convert an empty list to HTML <ul>'
        def emptyListHtml = HtmlFormatterUtil.toUl(emptyList as List<Object>, 'EMPTY_DEFAULT')

        then: 'We get the String "EMPTY_DEFAULT" as a result for the empty list'
        emptyListHtml == 'EMPTY_DEFAULT'

        when: 'We convert an empty map to HTML <ul>'
        def emptyMapHtml = HtmlFormatterUtil.toUl(emptyMap as Map<String, Object>, 'EMPTY_DEFAULT')

        then: 'We get the String "EMPTY_DEFAULT" as a result for the empty map'
        emptyMapHtml == 'EMPTY_DEFAULT'

        when: "We convert a list to HTML <ul> with class 'some-ul-class'"
        def listHtml = HtmlFormatterUtil.toUl(list as List<Object>, 'EMPTY_DEFAULT', 'some-ul-class')

        then: "We get the HTML String for a HTML <ul class='some-ul-class'> with the list items as <li> sub-elements"
        listHtml == "<ul class='some-ul-class'><li>v1</li><li>v2</li></ul>"

        when: "We convert a map to HTML <ul> with class 'some-ul-class'"
        def mapHtml = HtmlFormatterUtil.toUl(map as Map<String, Object>, 'EMPTY_DEFAULT', 'some-ul-class')

        then: "We get the HTML String for a HTML <ul class='some-ul-class'> with the map items as <li> sub-elements"
        mapHtml == "<ul class='some-ul-class'><li>k1: EMPTY_DEFAULT</li><li>k2: EMPTY_DEFAULT</li><li>k3: v3</li><li>k4: <ul class='some-ul-class'><li>v4.1</li><li>v4.2</li></ul></li></ul>"

    }
}
