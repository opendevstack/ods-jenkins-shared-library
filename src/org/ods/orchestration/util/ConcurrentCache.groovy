package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Function

class ConcurrentCache<K, V> {

    private final ConcurrentMap<K, V> cache
    private final Function<? super K, ? extends V> computeIfAbsent

    ConcurrentCache(Function<? super K, ? extends V> computeIfAbsent, int initialCapacity = 16, float loadFactor = 0.75f, int concurrencyLevel = 1) {
        this.cache = new ConcurrentHashMap(initialCapacity, loadFactor, concurrencyLevel)
        this.computeIfAbsent = computeIfAbsent
    }

    @NonCPS
    def get(K key) {
        cache.computeIfAbsent(key, this.computeIfAbsent)
    }

    @NonCPS
    @Override
    String toString() {
        return cache.toString()
    }
}
