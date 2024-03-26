package org.ods.orchestration.util

import util.SpecHelper

import java.util.concurrent.ConcurrentHashMap

class ConcurrentCacheSpec extends SpecHelper {

    def "Return value from cache"() {
        given:
            Map cache_ = new ConcurrentHashMap(64)
            cache_.put("key", 1L)
            def cache = new ConcurrentCache(cache_)

        when:
            def result = cache.get("key", { key -> 0L })

        then:
            1L == result
    }

    def "Return value from cache not existing before"() {
        given:
            def cache = new ConcurrentCache(new ConcurrentHashMap(64))

        when:
            def result = cache.get("key", { key -> 1L })

        then:
            1L == result
    }

    def "Put value in cache not existing before"() {
        given:
            def cache = new ConcurrentCache(new ConcurrentHashMap(64))
            cache.put("key", 1L)

        when:
            def result = cache.get("key", { key -> 0L })

        then:
            1L == result
    }

    def "Put value in cache existing before"() {
        given:
            Map cache_ = new ConcurrentHashMap(64)
            cache_.put("key", 1L)
            def cache = new ConcurrentCache(cache_)
            cache.put("key", 3L)

        when:
            def result = cache.get("key", { key -> 0L })

        then:
            1L == result
    }

    def "Output toString"() {
        given:
            Map cache_ = new ConcurrentHashMap(64)
            cache_.put("key", 1L)
            def cache = new ConcurrentCache(cache_)

        when:
            def result = cache.toString()

        then:
            "{key=1}" == result
    }
}
