package org.ods.services

import groovy.json.JsonSlurperClassic

import spock.lang.*

import util.*
import org.ods.util.Logger

class OpenShiftServiceSpec extends SpecHelper {

    def "image info for image URL"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = new OpenShiftService(steps, new Logger(steps, false))

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
        'qux@sha256:abc'                        || ''                   | ''         | 'qux'
        'bar:2-3ec425bc'                        || ''                   | ''         | 'bar'
    }

    def "image info with SHA for image stream URL"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = GroovySpy(OpenShiftService, constructorArgs: [steps, new Logger(steps, false)], global: true)
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
        def service = new OpenShiftService(steps, new Logger(steps, false))
        def file = new FixtureHelper().getResource("pod.json")

        when:
        def result = service.extractPodData(new JsonSlurperClassic().parseText(file.text))

        then:
        result.size() == 1
        result[0].toMap() == [
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

    def "helm upgrade"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = new OpenShiftService(steps, new Logger(steps, false))

        when:
        service.helmUpgrade(
            'foo',
            'bar',
            ['values.yml', 'values-dev.yml'],
            [imageTag: '6f8db5fb'],
            ['--install', '--atomic'],
            ['--force'],
            true
        )

        then:
        1 * steps.sh(
            script: 'helm -n foo secrets diff upgrade --install --force -f values.yml -f values-dev.yml --set imageTag=6f8db5fb --no-color bar ./',
            label: 'Show diff explaining what helm upgrade would change for release bar in foo'
        )
        1 * steps.sh(
            script: 'helm -n foo secrets upgrade --install --atomic --force -f values.yml -f values-dev.yml --set imageTag=6f8db5fb bar ./',
            label: 'Upgrade Helm release bar in foo'
        )
    }

    // test implementation to prove as much as possible without actually
    // running against a real cluster.
    def "rollout: just watch if triggered already"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def logger = Spy(Logger, constructorArgs: [steps, false])
        def service = Spy(OpenShiftService, constructorArgs: [steps, logger])
        service.getRevision('foo', 'Deployment', 'bar') >> 2
        service.watchRollout('foo', 'Deployment', 'bar', 5) >> 'bar-6f8db5fb69'

        when:
        def result = service.rollout('foo', 'Deployment', 'bar', 1, 5)

        then:
        1 * logger.info("Rollout of deployment for 'bar' has been triggered automatically.")
        result == 'bar-6f8db5fb69'
    }

    // test implementation to prove as much as possible without actually
    // running against a real cluster.
    def "rollout: Deployment: restart if not triggered already"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def logger = Spy(Logger, constructorArgs: [steps, false])
        def service = Spy(OpenShiftService, constructorArgs: [steps, logger])
        service.getRevision('foo', 'Deployment', 'bar') >> 1
        service.watchRollout('foo', 'Deployment', 'bar', 5) >> 'bar-6f8db5fb69'

        when:
        def result = service.rollout('foo', 'Deployment', 'bar', 1, 5)

        then:
        0 * logger.info(*_)
        0 * service.invokeMethod('startRollout')
        1 * service.invokeMethod('restartRollout', ['foo', 'bar', 1])
        result == 'bar-6f8db5fb69'
    }

    // test implementation to prove as much as possible without actually
    // running against a real cluster.
    def "rollout: DeploymentConfig: start if not triggered already"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def logger = Spy(Logger, constructorArgs: [steps, false])
        def service = Spy(OpenShiftService, constructorArgs: [steps, logger])
        service.getRevision('foo', 'DeploymentConfig', 'bar') >> 1
        service.watchRollout('foo', 'DeploymentConfig', 'bar', 5) >> 'bar-2'

        when:
        def result = service.rollout('foo', 'DeploymentConfig', 'bar', 1, 5)

        then:
        0 * logger.info(*_)
        1 * service.invokeMethod('startRollout', ['foo', 'bar', 1])
        0 * service.invokeMethod('restartRollout')
        result == 'bar-2'
    }

    // test implementation to prove as much as possible without actually
    // running against a real cluster.
    def "rollout status: Deployment"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def logger = new Logger(steps, false)
        def service = Spy(OpenShiftService, constructorArgs: [steps, logger])
        service.invokeMethod('getJSON', ['foo', 'rs', 'bar']) >> [
            status: [
                replicas: replicas,
                fullyLabeledReplicas: fullyLabeledReplicas,
                availableReplicas: availableReplicas
            ]
        ]

        when:
        def result = service.getRolloutStatus('foo', 'Deployment', 'bar')

        then:
        result == status

        where:
        replicas | fullyLabeledReplicas | availableReplicas || status
        null     | null                 | null              || 'waiting'
        1        | 0                    | 0                 || 'waiting'
        1        | 1                    | 0                 || 'waiting'
        1        | 1                    | 1                 || 'complete'
    }

    // test implementation to prove as much as possible without actually
    // running against a real cluster.
    def "rollout status: DeploymentConfig"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def logger = new Logger(steps, false)
        def service = Spy(OpenShiftService, constructorArgs: [steps, logger])
        service.invokeMethod('getJSONPath', *_) >> 'complete'

        when:
        def result = service.getRolloutStatus('foo', 'DeploymentConfig', 'bar')

        then:
        result == 'complete'
    }

}
