package org.ods.orchestration.util

import util.SpecHelper

class EnvironmentResolverSpec extends SpecHelper {

    // -------------------------------------------------------------------------
    // Constructor / resolveEnabledEnvironments
    // -------------------------------------------------------------------------

    def "uses default environments when raw value is null"() {
        when:
        def resolver = new EnvironmentResolver(null)

        then:
        resolver.getEnabledEnvironments() == ['dev', 'qa', 'prod']
    }

    def "uses default environments when raw value is empty"() {
        when:
        def resolver = new EnvironmentResolver('')

        then:
        resolver.getEnabledEnvironments() == ['dev', 'qa', 'prod']
    }

    def "uses default environments when raw value is blank"() {
        when:
        def resolver = new EnvironmentResolver('   ')

        then:
        resolver.getEnabledEnvironments() == ['dev', 'qa', 'prod']
    }

    def "parses dev,qa,prod correctly"() {
        when:
        def resolver = new EnvironmentResolver('dev,qa,prod')

        then:
        resolver.getEnabledEnvironments() == ['dev', 'qa', 'prod']
    }

    def "parses dev,prod correctly"() {
        when:
        def resolver = new EnvironmentResolver('dev,prod')

        then:
        resolver.getEnabledEnvironments() == ['dev', 'prod']
    }

    def "trims whitespace around environment names"() {
        when:
        def resolver = new EnvironmentResolver(' dev , prod ')

        then:
        resolver.getEnabledEnvironments() == ['dev', 'prod']
    }

    // -------------------------------------------------------------------------
    // validateEnvironments
    // -------------------------------------------------------------------------

    def "throws IllegalArgumentException for unknown environment set"() {
        when:
        new EnvironmentResolver(raw)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('Allowed values are: dev,qa,prod | dev,prod')

        where:
        raw << ['dev', 'prod,qa', 'dev,staging,prod', 'dev,qa,prod,staging']
    }

    // -------------------------------------------------------------------------
    // getSourceEnvFor
    // -------------------------------------------------------------------------

    def "getSourceEnvFor returns correct source environment"() {
        given:
        def resolver = new EnvironmentResolver(raw)

        expect:
        resolver.getSourceEnvFor(target) == expected

        where:
        raw            | target  || expected
        'dev,qa,prod'  | 'dev'   || 'dev'
        'dev,qa,prod'  | 'qa'    || 'dev'
        'dev,qa,prod'  | 'prod'  || 'qa'
        'dev,prod'     | 'dev'   || 'dev'
        'dev,prod'     | 'qa'    || 'dev'   // qa not in list → fallback to first
        'dev,prod'     | 'prod'  || 'dev'
    }

    // -------------------------------------------------------------------------
    // getSourceEnvTokenFor
    // -------------------------------------------------------------------------

    def "getSourceEnvTokenFor returns correct token"() {
        given:
        def resolver = new EnvironmentResolver(raw)

        expect:
        resolver.getSourceEnvTokenFor(target) == expectedToken

        where:
        raw            | target  || expectedToken
        'dev,qa,prod'  | 'dev'   || 'D'
        'dev,qa,prod'  | 'qa'    || 'D'
        'dev,qa,prod'  | 'prod'  || 'Q'
        'dev,prod'     | 'dev'   || 'D'
        'dev,prod'     | 'qa'    || 'D'   // qa not in list → fallback to dev → D
        'dev,prod'     | 'prod'  || 'D'
    }

}

