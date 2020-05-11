package org.ods.services

import spock.lang.*

import util.*
import org.ods.util.Logger

class JenkinsServiceSpec extends SpecHelper {

    def "unstash files into path"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = new JenkinsService(steps, new Logger(steps, false))

        def name = "myStash"
        def path = "myPath"
        def type = "myType"

        when:
        def result = service.unstashFilesIntoPath(name, path, type)

        then:
        1 * steps.dir(path, _)

        then:
        1 * steps.unstash(name)

        then:
        result == true
    }

    def "unstash files into path with failure"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = new JenkinsService(steps, new Logger(steps, false))

        def name = "myStash"
        def path = "myPath"
        def type = "myType"

        when:
        def result = service.unstashFilesIntoPath(name, path, type)

        then:
        1 * steps.unstash(name) >> {
            throw new RuntimeException()
        }

        then:
        1 * steps.echo("Could not find any files of type '${type}' to unstash for name '${name}'")

        then:
        result == false
    }
}
