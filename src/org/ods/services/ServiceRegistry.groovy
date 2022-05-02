package org.ods.services

import java.util.concurrent.ConcurrentHashMap

@SuppressWarnings('NonFinalPublicField')
class ServiceRegistry {

    private Map registry = new ConcurrentHashMap()

    public static ServiceRegistry instance = new ServiceRegistry()

    static def removeInstance() {
        if (instance?.registry) {
            instance.registry.clear()
            instance.registry = null
        }
        instance = null
    }

    void add(Class<?> type, def service) {
        registry[type.name] = service
    }

    def <T> T get(Class<T> type) {
        return registry[type.name] as T
    }

    def clear() {
        registry.clear()
    }

    def getAllServices() {
        return registry
    }

}
