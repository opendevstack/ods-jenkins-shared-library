package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS

import java.util.concurrent.ConcurrentMap
import java.util.function.Function

class ConcurrentCache<K, V> {

    private final ConcurrentMap<K, V> cache

    ConcurrentCache(def cache) {
        this.cache = cache
    }

    @NonCPS
    def get(K key,
            Function<? super K, ? extends V> computeIfAbsent) {
        cache.computeIfAbsent(key, computeIfAbsent)
    }

    @NonCPS
    def put(K key, V value) {
        cache.putIfAbsent(key, value)
    }


    @NonCPS
    @Override
    String toString() {
        return cache.toString()
    }
}
