package org.ods.services

import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase

class AquaServiceSpec extends PipelineSpockTestBase {

    def "use credentials in Jenkins"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = Spy(AquaService, constructorArgs: [
            steps,
            new Logger(steps, false)
        ])

        Closure callback = { username, password ->
            println "Done!"
        }

        when:
        def result = service.withCredentials("credentials-id", callback)

        then:
        1 * steps.usernamePassword('credentialsId': "credentials-id",
            'usernameVariable': 'USERNAME',
            'passwordVariable': 'PASSWORD') >> [['USERNAME': 'user'],['PASSWORD': 'pass']]
        1 * steps.withCredentials([[['USERNAME':'user'], ['PASSWORD':'pass']]], _)
    }

    def "invoke Aqua cli"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = Spy(AquaService, constructorArgs: [
            steps,
            new Logger(steps, false)
        ])

        when:
        def result = service.scanViaCli("http://aqua", "internal", "12345",
            "cd-user", "report.html", "report.json", 100)

        then:
        2 * steps.getEnv() >> ['USERNAME':'user', 'PASSWORD': 'pass']
        1 * steps.sh(_) >> {
            assert it.label == ['Scan via Aqua CLI']
            assert it.returnStatus == [true]
            assert it.script.toString().contains('set +e &&')
            assert it.script.toString().contains('aquasec scan 12345')
            assert it.script.toString().contains('--dockerless')
            assert it.script.toString().contains('--register')
            assert it.script.toString().contains('--text')
            assert it.script.toString().contains('--scan-timeout 100')
            assert it.script.toString().contains('--htmlfile \'report.html\'')
            assert it.script.toString().contains('--jsonfile \'report.json\'')
            assert it.script.toString().contains('-w /tmp/aqua')
            assert it.script.toString().contains('-U \'user\'')
            assert it.script.toString().contains('-P \'pass\'')
            assert it.script.toString().contains('-H \'http://aqua\'')
            assert it.script.toString().contains('--registry \'internal\' &&')
            assert it.script.toString().contains('set -e')

            return 0
        }
        0 == result
    }

}
