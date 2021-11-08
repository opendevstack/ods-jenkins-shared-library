package org.ods.util

import spock.lang.Specification

class CollectionWithForLoopSpec extends Specification {

    def "findAll"() {
        given:
        def fruitNames = ["apples", "bananas"]

        when:
        def withFor = CollectionWithForLoop.findAll(fruitNames, {it.length() > 6} )
        def collectionOriginal = fruitNames.findAll {it.length() > 6}
        then:
        withFor == ["bananas"]
        withFor == collectionOriginal
    }

    def "collectEntries"() {
        given:
        def fruits = [ [id: "apples"], [id: "bananas"] ]

        when:
        def collectWithFor = CollectionWithForLoop.collectEntries(fruits,
            { it.id }) {return "tastes yummy!"}
        def collectOriginal= fruits.collectEntries {
            [(it.id): "tastes yummy!"]}

        then:
        collectWithFor == collectOriginal
        collectWithFor == ["apples": "tastes yummy!", "bananas": "tastes yummy!"]
    }
}
