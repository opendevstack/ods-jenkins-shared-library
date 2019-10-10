package org.ods.service

@Singleton
class ServiceRegistry {

    private registry = [:]

    void add(def name, def service) {
        registry[name] = service
    }

    def get(def name) {
        return registry[name]
    }
}
