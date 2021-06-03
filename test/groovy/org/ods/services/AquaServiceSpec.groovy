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

}
