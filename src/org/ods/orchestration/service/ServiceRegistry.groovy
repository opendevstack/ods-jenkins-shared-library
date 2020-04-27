package org.ods.orchestration.service

@Singleton
class ServiceRegistry {

    private registry = [:]

    void add(Class<?> type, def service) {
        registry[type.name] = service
    }

    def <T> T get(Class<T> type) {
        return registry[type.name] as T
    }
}
