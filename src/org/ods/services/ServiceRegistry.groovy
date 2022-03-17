package org.ods.services

import java.util.concurrent.ConcurrentHashMap

class ServiceRegistry {

    private final Map registry = new ConcurrentHashMap()

    public static ServiceRegistry instance = new ServiceRegistry()

    static def removeInstance() {
        instance = null;
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


}
