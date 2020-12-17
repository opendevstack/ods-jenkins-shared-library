package vars

import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import groovy.lang.MissingPropertyException
import org.ods.component.Context
import org.ods.component.IContext
import org.ods.services.SnykService
import org.ods.util.Logger
import org.ods.services.ServiceRegistry
import vars.test_helper.PipelineSpockTestBase
import spock.lang.*

import static org.assertj.core.api.Assertions.assertThat

class OdsComponentStageScanWithSnykSpec extends PipelineSpockTestBase {

    private Logger logger = Mock(Logger)

    @Shared
    def config = [
        projectId    : 'foo',
        componentId  : 'bar',
        nexusUrl     : 'https://nexus.example.com',
        nexusUsername: 'developer',
        nexusPassword: 's3cr3t',
        buildNumber  : '42'
    ]

    def "run successfully"() {
        given:
        IContext context = new Context(null, config, logger)
        SnykService snykService = snykService()
        ServiceRegistry.instance.add(SnykService, snykService)

        when:
        def script = loadScript('vars/odsComponentStageScanWithSnyk.groovy')
        helper.registerAllowedMethod('withEnv', [List, Closure]) { List args, Closure block -> block() }
        helper.registerAllowedMethod('archiveArtifacts', [Map]) { Map args -> }
        helper.registerAllowedMethod('stash', [Map]) { Map args -> }
        script.call(context, [
            failOnVulnerabilities : true,
            snykAuthenticationCode: 's3cr3t'
        ])

        then:
        printCallStack()
        assertThat(callStackDump()).doesNotContain("Skipping as branch")
        assertJobStatusSuccess()
    }

    def "run successfully when a branch prefix is matched"() {
        given:
        def config = [
            projectId    : 'foo',
            componentId  : 'bar',
            nexusUrl     : 'https://nexus.example.com',
            nexusUsername: 'developer',
            nexusPassword: 's3cr3t',
            buildNumber  : '42',
            gitBranch : 'feature/foo'
        ]
        IContext context = new Context(null, config, logger)
        SnykService snykService = snykService()
        ServiceRegistry.instance.add(SnykService, snykService)

        when:
        def script = loadScript('vars/odsComponentStageScanWithSnyk.groovy')
        helper.registerAllowedMethod('withEnv', [List, Closure]) { List args, Closure block -> block() }
        helper.registerAllowedMethod('archiveArtifacts', [Map]) { Map args -> }
        helper.registerAllowedMethod('stash', [Map]) { Map args -> }
        script.call(context, [
            failOnVulnerabilities : true,
            snykAuthenticationCode: 's3cr3t',
            branch: 'feature/'
        ])

        then:
        printCallStack()
        assertThat(callStackDump()).doesNotContain("Skipping as branch 'feature/foo' is not covered by the 'branch' option.")
        assertJobStatusSuccess()
    }

    private SnykService snykService() {
        SnykService snykService = Stub(SnykService.class)
        snykService.version() >> true
        snykService.auth(*_) >> true
        snykService.monitor(*_) >> true
        snykService.test(*_) >> true
        snykService
    }

    @Unroll
    def "may fail"() {
        given:
        IContext context = new Context(null, config, logger)
        SnykService snykService = Stub(SnykService.class)
        snykService.version() >> versionOk
        snykService.auth(*_) >> authOk
        snykService.monitor(*_) >> monitorOk
        snykService.test(*_) >> testOk
        ServiceRegistry.instance.add(SnykService, snykService)

        when:
        def script = loadScript('vars/odsComponentStageScanWithSnyk.groovy')
        helper.registerAllowedMethod('withEnv', [List, Closure]) { List args, Closure block -> block() }
        helper.registerAllowedMethod('archiveArtifacts', [Map]) { Map args -> }
        helper.registerAllowedMethod('stash', [Map]) { Map args -> }
        script.call(context, [
            failOnVulnerabilities : failOnVulnerabilities,
            snykAuthenticationCode: 's3cr3t',
            branch                : '*'
        ])

        then:
        printCallStack()
        assertCallStackContains(message)
        if (expectedToFail) {
            assertJobStatusFailure()
        } else {
            assertJobStatusSuccess()
        }

        where:
        versionOk | authOk | monitorOk | testOk | failOnVulnerabilities || expectedToFail | message
        false     | true   | true      | true   | true                  || true           | 'Snyk binary is not in $PATH'
        true      | false  | true      | true   | true                  || true           | 'Snyk auth failed'
        true      | true   | false     | true   | true                  || true           | 'Snyk monitor failed'
        true      | true   | true      | true   | true                  || false          | 'No vulnerabilities detected'
        true      | true   | true      | false  | false                 || false          | 'Snyk test detected vulnerabilities'
        true      | true   | true      | false  | true                  || true           | 'Snyk scan stage failed'
    }

    def "skip branch should not be scanned"() {
        given:
        def config = [
            branchToEnvironmentMapping: ['master': 'dev', 'release/': 'test'],
            gitBranch                 : 'feature/foo'
        ]
        def context = new Context(null, config, logger)

        when:
        def script = loadScript('vars/odsComponentStageScanWithSnyk.groovy')
        script.call(context, ['branch': 'master'])

        then:
        printCallStack()
        assertCallStackContains("Skipping stage 'Snyk Security Scan' for branch 'feature/foo'")
        assertJobStatusSuccess()
    }

    def "fails on incorrect options"() {
    given:
    def config = [
      environment: null,
      gitBranch: 'master',
      gitCommit: 'cd3e9082d7466942e1de86902bb9e663751dae8e',
      branchToEnvironmentMapping: [:]
    ]
    def context = new Context(null, config, logger)

    when:
    def script = loadScript('vars/odsComponentStageScanWithSnyk.groovy')
    script.call(context, options)

    then:
    def exception = thrown(wantEx)
    exception.message == wantExMessage

    where:
    options           || wantEx                   | wantExMessage
    [branches: 'abc'] || GroovyCastException      | "Cannot cast object 'abc' with class 'java.lang.String' to class 'java.util.List'"
    [foobar: 'abc']   || MissingPropertyException | "No such property: foobar for class: org.ods.component.ScanWithSnykOptions"
  }

}
