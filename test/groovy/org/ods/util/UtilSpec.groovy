package org.ods.util

import static org.junit.Assert.*
import org.junit.Test
import org.ods.util.Util
import vars.test_helper.PipelineSpockTestBase

class LoggerSpec extends PipelineSpockTestBase {

    def "findAll"() {
        when:
        def fruitNames = ["apples", "bananas"]

        then:
        Util.findAll(fruitNames) {
            it.length() > 6
        } == fruitNames.findAll {
            it.length() > 6
        } == ["bananas"]
    }

    def "collectEntries"() {
        when:
        def fruits = [ [id: "apples"], [id: "bananas"] ]

        then:
        Util.collectEntries(fruits, { it.id }) {
            return "tastes yummy!"
        } == fruits.collectEntries {
            [(it.id): "tastes yummy!"]
        } == ["apples": "tastes yummy!", "bananas": "tastes yummy!"]
    }
}
