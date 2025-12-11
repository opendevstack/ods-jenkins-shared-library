package org.ods.services

import com.cloudbees.groovy.cps.NonCPS

import java.util.concurrent.ConcurrentHashMap

@SuppressWarnings('NonFinalPublicField')
class ServiceRegistry {

    private Map registry = new ConcurrentHashMap()

    public static ServiceRegistry instance = new ServiceRegistry()

    @NonCPS
    static def removeInstance() {
        if (instance?.registry) {
            instance.registry.clear()
            instance.registry = null
        }
        instance = null
    }

    @NonCPS
    void add(Class<?> type, def service) {
        registry[type.name] = service
    }

    @NonCPS
    def <T> T get(Class<T> type) {
        return registry[type.name] as T
    }

    @NonCPS
    def clear() {
        registry.clear()
    }

    @NonCPS
    def getAllServices() {
        return registry
    }

}
