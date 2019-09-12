package org.ods.service

import spock.lang.*

import util.*

class JenkinsServiceSpec extends SpecHelper {

    def "unstash files into path"() {
        given:
        def name = "myStash"
        def path = "myPath"
        def type = "myType"

        def script = Spy(PipelineSteps)
        def service = new JenkinsService(script)

        when:
        def result = service.unstashFilesIntoPath(name, path, type)

        then:
        1 * script.dir(path, _)

        then:
        1 * script.unstash(name)

        then:
        result == true
    }

    def "unstash files into path with failure"() {
        given:
        def name = "myStash"
        def path = "myPath"
        def type = "myType"

        def script = Spy(PipelineSteps)
        def service = new JenkinsService(script)

        when:
        def result = service.unstashFilesIntoPath(name, path, type)

        then:
        1 * script.unstash(name) >> {
            throw new RuntimeException()
        }

        then:
        1 * script.echo("Could not find any files of type '${type}' to unstash for name '${name}'")

        then:
        result == false
    }
}
