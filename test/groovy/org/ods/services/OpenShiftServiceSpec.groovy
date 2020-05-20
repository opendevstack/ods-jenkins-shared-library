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
        result == [registry: registry, repository: repository, name: name,]

        where:
        imageUrl                                || registry             | repository | name
        '172.30.21.196:5000/foo/bar:2-3ec425bc' || '172.30.21.196:5000' | 'foo'      | 'bar'
        '172.30.21.196:5000/baz/qux@sha256:abc' || '172.30.21.196:5000' | 'baz'      | 'qux'
        'baz/qux@sha256:abc'                    || ''                   | 'baz'      | 'qux'
        'foo/bar:2-3ec425bc'                    || ''                   | 'foo'      | 'bar'
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
        'foo/bar:2-3ec425bc'                    | '172.30.21.196:5000/foo/bar@sha256:xyz' || '172.30.21.196:5000' | 'foo'      | 'bar' | 'sha256:xyz' | 'xyz'
        'baz/qux@sha256:abc'                    | 'n/a'                                   || ''                   | 'baz'      | 'qux' | 'sha256:abc' | 'abc'
    }

    def "pod data extraction"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = new OpenShiftService(steps, new Logger(steps, false), 'foo')
        def file = new FixtureHelper().getResource("pod.json")

        when:
        def result = service.extractPodData(file.text, "deployment 'bar'")

        then:
        result == [
            podName: 'bar-164-6xxbw',
            podNamespace: 'foo-dev',
            podMetaDataCreationTimestamp: '2020-05-18T10:43:56Z',
            deploymentId: 'bar-164',
            podNode: 'ip-172-31-61-82.eu-central-1.compute.internal',
            podIp: '10.128.17.92',
            podStatus: 'Running',
            podStartupTimeStamp: '2020-05-18T10:43:56Z',
            containers: [
                bar: '172.30.21.196:5000/foo-dev/bar@sha256:07ba1778e7003335e6f6e0f809ce7025e5a8914dc5767f2faedd495918bee58a'
            ]
        ]
    }

}
