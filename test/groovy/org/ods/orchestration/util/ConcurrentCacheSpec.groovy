package org.ods.orchestration.util

import util.SpecHelper

class ConcurrentCacheSpec extends SpecHelper {

    def "Return value from cache"() {
        given:
            def cache = new ConcurrentCache<String, Long>({ key -> 123456L })

        when:
            def result = cache.get("key")

        then:
            123456L == result
    }

    def "Output toString"() {
        given:
            def cache = new ConcurrentCache<String, Long>({ key -> 123456L })
            cache.get("key")

        when:
            def result = cache.toString()

        then:
            "{key=123456}" == result
    }
}
