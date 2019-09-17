package org.ods.service

import spock.lang.*

import util.*

class ServiceRegistrySpec extends SpecHelper {

    class ServiceA {
        boolean run() {
            return true
        }
    }

    class ServiceB {
        boolean run() {
            return false
        }
    }

    ServiceRegistry createService() {
        return ServiceRegistry.instance
    }

    def "add and get services"() {
        given:
        def service = createService()

        when:
        service.add("Service-A", new ServiceA())
        service.add("Service-B", new ServiceB())

        then:
        service.get("Service-A").run() == true
        service.get("Service-B").run() == false
    }
}
