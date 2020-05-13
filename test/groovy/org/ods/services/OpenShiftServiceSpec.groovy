package org.ods.services

import spock.lang.*

import util.*
import org.ods.util.Logger

class OpenShiftServiceSpec extends SpecHelper {

    def "image info for image URL"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = new OpenShiftService(steps, new Logger(steps, false), 'foo')

        when:
        def result = service.imageInfoForImageUrl(imageUrl)

        then:
        result == [
            registry: registry,
            repository: repository,
            name: name,
        ]

        where:
        imageUrl                                || registry             | repository | name
        '172.30.21.196:5000/foo/bar:2-3ec425bc' || '172.30.21.196:5000' | 'foo'      | 'bar'
        '172.30.21.196:5000/baz/qux@sha256:abc' || '172.30.21.196:5000' | 'baz'      | 'qux'
    }

    def "image info with SHA for image stream URL"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = GroovySpy(OpenShiftService, constructorArgs: [steps, new Logger(steps, false), 'foo'], global: true)
        OpenShiftService.getImageReference(*_) >> imageReference

        when:
        def result = service.imageInfoWithShaForImageStreamUrl(imageStreamUrl)

        then:
        result == [
            registry: registry,
            repository: repository,
            name: name,
            sha: sha,
            shaStripped: shaStripped,
        ]

        where:
        imageStreamUrl                          | imageReference                          || registry             | repository | name  | sha          | shaStripped
        '172.30.21.196:5000/foo/bar:2-3ec425bc' | '172.30.21.196:5000/foo/bar@sha256:xyz' || '172.30.21.196:5000' | 'foo'      | 'bar' | 'sha256:xyz' | 'xyz'
        '172.30.21.196:5000/baz/qux@sha256:abc' | 'n/a'                                   || '172.30.21.196:5000' | 'baz'      | 'qux' | 'sha256:abc' | 'abc'
    }

}
