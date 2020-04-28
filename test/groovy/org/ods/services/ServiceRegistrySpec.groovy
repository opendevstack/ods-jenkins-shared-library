package org.ods.services

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
        service.add(ServiceA, new ServiceA())
        service.add(ServiceB, new ServiceB())

        then:
        service.get(ServiceA).run()
        !service.get(ServiceB).run()
    }
}
