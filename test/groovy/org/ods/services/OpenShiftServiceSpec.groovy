
package org.ods.services

import groovy.json.JsonSlurperClassic
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import org.ods.util.PodData
import spock.lang.Unroll
import util.FixtureHelper
import util.OpenShiftHelper
import util.SpecHelper

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

    def "multiple pods data extraction"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = new OpenShiftService(steps, new Logger(steps, false))
        def file = new FixtureHelper().getResource("pods.json")
        List<PodData> expected = [
            [
                podName                     : 'example-be-adapter-5dc8d77dbd-cns9z',
                podNamespace                : 'proj-dev',
                podMetaDataCreationTimestamp: '2023-07-24T11:58:29Z',
                deploymentId                : 'example-be-adapter-5dc8d77dbd',
                podNode                     : 'ip-10-32-10-150.eu-west-1.compute.internal',
                podIp                       : '192.0.2.100',
                podStatus                   : 'Running',
                podStartupTimeStamp         : '2023-07-24T11:58:29Z',
                containers                  : ['be-adapter': 'image-registry.openshift-image-registry.svc:5000/proj-dev/example-be-adapter@sha256:d043df9c66af57297f7ff2157f9c2f801e16122649830e4fbf8e09237a8ccc82']
            ],
            [
                podName                     : 'example-be-adapter-db-5cdd4dddd9-4v4wn',
                podNamespace                : 'proj-dev',
                podMetaDataCreationTimestamp: '2023-07-08T00:16:00Z',
                deploymentId                : 'example-be-adapter-db-5cdd4dddd9',
                podNode                     : 'ip-10-32-8-151.eu-west-1.compute.internal',
                podIp                       : '192.0.2.42',
                podStatus                   : 'Running',
                podStartupTimeStamp         : '2023-07-08T00:16:00Z',
                containers                  : ['be-adapter-db': 'registry.redhat.io/rhscl/postgresql-13-rhel7@sha256:c6fde1a8653a597c18b0326bc71ce4a614273be74b9aef3ced83a1b11472687a']
            ],
            [
                podName                     : 'example-be-cache-58d6d45c57-99tvn',
                podNamespace                : 'proj-dev',
                podMetaDataCreationTimestamp: '2023-07-08T04:24:57Z',
                deploymentId                : 'example-be-cache-58d6d45c57',
                podNode                     : 'ip-10-32-9-116.eu-west-1.compute.internal',
                podIp                       : '192.0.2.34',
                podStatus                   : 'Running',
                podStartupTimeStamp         : '2023-07-08T04:24:57Z',
                containers                  : ['be-cache': 'registry.redhat.io/rhscl/redis-5-rhel7@sha256:52ffb5bf944593c290c2de52db5117044e58633385dcbe70679ea69ff0c5ff01']
            ],
            [
                podName                     : 'example-be-datafeed-576bc779f8-9tftp',
                podNamespace                : 'proj-dev',
                podMetaDataCreationTimestamp: '2023-07-24T11:58:29Z',
                deploymentId                : 'example-be-datafeed-576bc779f8',
                podNode                     : 'ip-10-32-9-69.eu-west-1.compute.internal',
                podIp                       : '192.0.2.173',
                podStatus                   : 'Running',
                podStartupTimeStamp         : '2023-07-24T11:58:29Z',
                containers                  : ['be-datafeed': 'image-registry.openshift-image-registry.svc:5000/proj-dev/example-be-datafeed@sha256:4cb8d88d1ba4e71e20cc6b9a7e8563f5a3bdd9c3fd802c33420b58139d2a2170']
            ],
            [
                podName                     : 'example-be-datafeed-576bc779f8-m6vxf',
                podNamespace                : 'proj-dev',
                podMetaDataCreationTimestamp: '2023-07-24T11:58:29Z',
                deploymentId                : 'example-be-datafeed-576bc779f8',
                podNode                     : 'ip-10-32-8-176.eu-west-1.compute.internal',
                podIp                       : '192.0.2.158',
                podStatus                   : 'Running',
                podStartupTimeStamp         : '2023-07-24T11:58:29Z',
                containers                  : ['be-datafeed': 'image-registry.openshift-image-registry.svc:5000/proj-dev/example-be-datafeed@sha256:4cb8d88d1ba4e71e20cc6b9a7e8563f5a3bdd9c3fd802c33420b58139d2a2170']
            ],
            [
                podName                     : 'example-be-gateway-7c7d8cc68b-p9skv',
                podNamespace                : 'proj-dev',
                podMetaDataCreationTimestamp: '2023-07-24T11:58:29Z',
                deploymentId                : 'example-be-gateway-7c7d8cc68b',
                podNode                     : 'ip-10-32-10-154.eu-west-1.compute.internal',
                podIp                       : '192.0.2.171',
                podStatus                   : 'Running',
                podStartupTimeStamp         : '2023-07-24T11:58:29Z',
                containers                  : ['be-gateway': 'image-registry.openshift-image-registry.svc:5000/proj-dev/example-be-gateway@sha256:94dd09c933f2b202dd629c8048e534c6b84b3cfc38cd1ef8553ac51de91fb11f']
            ],
            [
                podName                     : 'example-be-main-5ffddb5dc9-hn24m',
                podNamespace                : 'proj-dev',
                podMetaDataCreationTimestamp: '2023-07-24T11:58:29Z',
                deploymentId                : 'example-be-main-5ffddb5dc9',
                podNode                     : 'ip-10-32-9-69.eu-west-1.compute.internal',
                podIp                       : '192.0.2.172',
                podStatus                   : 'Running',
                podStartupTimeStamp         : '2023-07-24T11:58:29Z',
                containers                  : [
                    'be-main'           : 'image-registry.openshift-image-registry.svc:5000/proj-dev/example-be-main@sha256:45dcb5214ff20b6374a502e9bd1bba0657073210e38eba0cbd1d9f5ddfa27c67',
                    'fluent-bit-be-main': 'public.ecr.aws/aws-observability/aws-for-fluent-bit@sha256:741c65dc7fa8383c5517886e73b2740e52d2e69bf62f26fac17059144c9a6a54']
            ],
            [
                podName                     : 'example-be-main-5ffddb5dc9-xjb2n',
                podNamespace                : 'proj-dev',
                podMetaDataCreationTimestamp: '2023-07-24T11:58:29Z',
                deploymentId                : 'example-be-main-5ffddb5dc9',
                podNode                     : 'ip-10-32-10-154.eu-west-1.compute.internal',
                podIp                       : '192.0.2.173',
                podStatus                   : 'Running',
                podStartupTimeStamp         : '2023-07-24T11:58:29Z',
                containers                  : [
                    'be-main'           : 'image-registry.openshift-image-registry.svc:5000/proj-dev/example-be-main@sha256:45dcb5214ff20b6374a502e9bd1bba0657073210e38eba0cbd1d9f5ddfa27c67',
                    'fluent-bit-be-main': 'public.ecr.aws/aws-observability/aws-for-fluent-bit@sha256:741c65dc7fa8383c5517886e73b2740e52d2e69bf62f26fac17059144c9a6a54']
            ],
            [
                podName                     : 'example-be-token-6fcb4d85d6-7jr2r',
                podNamespace                : 'proj-dev',
                podMetaDataCreationTimestamp: '2023-07-24T11:58:29Z',
                deploymentId                : 'example-be-token-6fcb4d85d6',
                podNode                     : 'ip-10-32-10-30.eu-west-1.compute.internal',
                podIp                       : '192.0.2.172',
                podStatus                   : 'Running',
                podStartupTimeStamp         : '2023-07-24T11:58:29Z',
                containers                  : ['be-token': 'image-registry.openshift-image-registry.svc:5000/proj-dev/example-be-token@sha256:cc5e57f98ee789429384e8df2832a89fbf1092b724aa8f3faff2708e227cb39e']
            ],
            [
                podName                     : 'example-be-token-6fcb4d85d6-ndp8x',
                podNamespace                : 'proj-dev',
                podMetaDataCreationTimestamp: '2023-07-24T11:58:29Z',
                deploymentId                : 'example-be-token-6fcb4d85d6',
                podNode                     : 'ip-10-32-9-69.eu-west-1.compute.internal',
                podIp                       : '192.0.2.171',
                podStatus                   : 'Running',
                podStartupTimeStamp         : '2023-07-24T11:58:29Z',
                containers                  : ['be-token': 'image-registry.openshift-image-registry.svc:5000/proj-dev/example-be-token@sha256:cc5e57f98ee789429384e8df2832a89fbf1092b724aa8f3faff2708e227cb39e']
            ]
        ]

        when:
        def podJson = new JsonSlurperClassic().parseText(file.text)
        def results = service.parsePodJson(podJson, null)

        then:
        results.size() == expected.size()
        for (int i = 0; i < results.size(); i++) {
            // Note: podIp, podNode and podStartupTimeStamp  are no longer in type PodData.
            // Each result is only a subset of the corresponding expected data.
            // While it can surpise that the == assertion below would not catch this,
            // it actually comes in handy so we can leave the original data in place.
            results[i].toMap() == expected[i]
        }
    }

    def "multiple pods data extraction with resourceName"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = new OpenShiftService(steps, new Logger(steps, false))
        def file = new FixtureHelper().getResource("pods.json")
        List<PodData> expected = [
            [
                podNamespace                : 'proj-dev',
                podMetaDataCreationTimestamp: '2023-07-24T11:58:29Z',
                deploymentId                : 'example-be-token-6fcb4d85d6',
                podStatus                   : 'Running',
                containers                  : ['be-token': 'image-registry.openshift-image-registry.svc:5000/proj-dev/example-be-token@sha256:cc5e57f98ee789429384e8df2832a89fbf1092b724aa8f3faff2708e227cb39e']
            ],
            [
                podNamespace                : 'proj-dev',
                podMetaDataCreationTimestamp: '2023-07-24T11:58:29Z',
                deploymentId                : 'example-be-token-6fcb4d85d6',
                podStatus                   : 'Running',
                containers                  : ['be-token': 'image-registry.openshift-image-registry.svc:5000/proj-dev/example-be-token@sha256:cc5e57f98ee789429384e8df2832a89fbf1092b724aa8f3faff2708e227cb39e']
            ]
        ]

        when:
        def podJson = new JsonSlurperClassic().parseText(file.text)
        def results = service.parsePodJson(podJson, 'example-be-token')

        then:
        results.size() == expected.size()
        for (int i = 0; i < results.size(); i++) {
            results[i].toMap() == expected[i]
        }
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
            podStatus: 'Running',
            containers: [
                bar: '172.30.21.196:5000/foo-dev/bar@sha256:07ba1778e7003335e6f6e0f809ce7025e5a8914dc5767f2faedd495918bee58a'
            ]
        ]
    }

    def "helm status data extraction"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = new OpenShiftService(steps, new Logger(steps, false))
        def helmJsonText = new FixtureHelper().getResource("helmstatus.json").text

        when:
        def helmStatusData = service.helmStatus('guardians-test', 'standalone-app')
//            OpenShiftService.DEPLOYMENT_KIND, OpenShiftService.DEPLOYMENTCONFIG_KIND,])
        then:
        1 * steps.sh(
            script: 'helm -n guardians-test status standalone-app --show-resources  -o json',
            label: 'Gather Helm status for release standalone-app in guardians-test',
            returnStdout: true,
        ) >> helmJsonText
        helmStatusData.name == 'standalone-app'
        helmStatusData.namespace == 'guardians-test'
    }

    def "helm status data extraction bad content"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = new OpenShiftService(steps, new Logger(steps, false))
        def helmJsonText = """
{
  "name": "standalone-app",
  "info": {
    "first_deployed": "2022-12-19T09:44:32.164490076Z",
    "last_deployed": "2024-03-04T15:21:09.34520527Z",
    "deleted": "",
    "description": "Upgrade complete",
    "status": "deployed",
    "resources" : {}
   }
}
        """
        when:
        def helmStatusData = service.helmStatus('guardians-test', 'standalone-app')
//            OpenShiftService.DEPLOYMENT_KIND, OpenShiftService.DEPLOYMENTCONFIG_KIND,])
        then:
        1 * steps.sh(
            script: 'helm -n guardians-test status standalone-app --show-resources  -o json',
            label: 'Gather Helm status for release standalone-app in guardians-test',
            returnStdout: true,
        )
        thrown RuntimeException
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
            script: 'HELM_DIFF_IGNORE_UNKNOWN_FLAGS=true helm -n foo secrets diff upgrade --install --atomic --force -f values.yml -f values-dev.yml --set imageTag=6f8db5fb --no-color --three-way-merge --normalize-manifests bar ./',
            label: 'Show diff explaining what helm upgrade would change for release bar in foo'
        )
        1 * steps.sh(
            script: 'helm -n foo secrets upgrade --install --atomic --force -f values.yml -f values-dev.yml --set imageTag=6f8db5fb bar ./',
            label: 'Upgrade Helm release bar in foo',
            returnStatus: true
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

    def "label resources"() {
        given:
        def steps = Mock(IPipelineSteps)
        steps.sh(_) >> ''
        def logger = Stub(ILogger)
        def labels = [
            'my.domain.org/label-key': 'the-v.al_ue',
            toDelete                 : null,
            emptyValue               : ''
        ]
        def project = 'testProject'
        def resources = 'type/resource'
        def selector = 'app=project-component,foo!=bar,x==y'
        def openShift = new OpenShiftService(steps, logger)

        when:
        openShift.labelResources(project, resources, labels, selector)

        then:
        1 * steps.sh({ OpenShiftHelper.validateLabels(it.script, project, resources, labels, selector) })

        when:
        openShift.labelResources(null, resources, labels, selector)

        then:
        1 * steps.sh({ OpenShiftHelper.validateLabels(it.script, null, resources, labels, selector) })

        when:
        openShift.labelResources(project, resources, labels)

        then:
        1 * steps.sh({ OpenShiftHelper.validateLabels(it.script, project, resources, labels) })

        when:
        openShift.labelResources(project, resources, null, selector)

        then:
        thrown IllegalArgumentException

        when:
        openShift.labelResources(project, null, labels, selector)

        then:
        thrown IllegalArgumentException
    }

    def "pause rollouts"() {
        given:
        def steps = Mock(IPipelineSteps)
        steps.sh(_) >> ''
        def logger = Stub(ILogger)
        def project = 'testProject'
        def resource = 'type/resource'
        def openShift = new OpenShiftService(steps, logger)

        when:
        openShift.pause(resource, project)

        then:
        1 * steps.sh({
            it.script =~ /^\s*oc\s+patch\s+-p\s'\{"spec":\{"paused":true}}'/ &&
                OpenShiftHelper.validateResourceParams(it.script, project, resource)
        })

        when:
        openShift.pause(resource)

        then:
        1 * steps.sh({
            it.script =~ /^\s*oc\s+patch\s+-p\s'\{"spec":\{"paused":true}}'/ &&
                OpenShiftHelper.validateResourceParams(it.script, null, resource)
        })

        when:
        openShift.pause(resource, '')

        then:
        1 * steps.sh({
            it.script =~ /^\s*oc\s+patch\s+-p\s'\{"spec":\{"paused":true}}'/ &&
                OpenShiftHelper.validateResourceParams(it.script, null, resource)
        })

        when:
        openShift.pause(null, project)

        then:
        thrown IllegalArgumentException

        when:
        openShift.pause('', project)

        then:
        thrown IllegalArgumentException
    }

    def "resume rollouts"() {
        given:
        def steps = Mock(IPipelineSteps)
        steps.sh(_) >> ''
        def logger = Stub(ILogger)
        def project = 'testProject'
        def resource = 'type/resource'
        def openShift = new OpenShiftService(steps, logger)

        when:
        openShift.resume(resource, project)

        then:
        1 * steps.sh({
            it.script =~ /^\s*oc\s+patch\s+-p\s'\{"spec":\{"paused":false}}'/ &&
                OpenShiftHelper.validateResourceParams(it.script, project, resource)
        })

        when:
        openShift.resume(resource)

        then:
        1 * steps.sh({
            it.script =~ /^\s*oc\s+patch\s+-p\s'\{"spec":\{"paused":false}}'/ &&
                OpenShiftHelper.validateResourceParams(it.script, null, resource)
        })

        when:
        openShift.resume(resource, '')

        then:
        1 * steps.sh({
            it.script =~ /^\s*oc\s+patch\s+-p\s'\{"spec":\{"paused":false}}'/ &&
                OpenShiftHelper.validateResourceParams(it.script, null, resource)
        })

        when:
        openShift.resume(null, project)

        then:
        thrown IllegalArgumentException

        when:
        openShift.resume('', project)

        then:
        thrown IllegalArgumentException
    }

    def "patch resource"() {
        given:
        def steps = Mock(IPipelineSteps)
        steps.sh(_) >> ''
        def logger = Stub(ILogger)
        def openShift = new OpenShiftService(steps, logger)
        def project = 'testProject'
        def resource = 'type/resource'
        def patch = [field1: 'value1', field2: [field3: 1, field4: null], field5: ['one', 2, null]]
        def path = '/base/path'

        when:
        openShift.patch(resource, patch, path, project)

        then:
        1 * steps.sh({ OpenShiftHelper.validatePatch(it.script, project, resource, patch, path) })

        when:
        openShift.patch(resource, null, path, project)

        then:
        1 * steps.sh({ OpenShiftHelper.validatePatch(it.script, project, resource, null, path) })

        when:
        openShift.patch(resource, [:], path, project)

        then:
        1 * steps.sh({ OpenShiftHelper.validatePatch(it.script, project, resource, [:], path) })

        when:
        openShift.patch(resource, patch, path)

        then:
        1 * steps.sh({ OpenShiftHelper.validatePatch(it.script, null, resource, patch, path) })

        when:
        openShift.patch(resource, patch, path, '')

        then:
        1 * steps.sh({ OpenShiftHelper.validatePatch(it.script, null, resource, patch, path) })

        when:
        openShift.patch(resource, patch)

        then:
        1 * steps.sh({ OpenShiftHelper.validatePatch(it.script, null, resource, patch, null) })

        when:
        openShift.patch(resource, patch, null, project)

        then:
        1 * steps.sh({ OpenShiftHelper.validatePatch(it.script, project, resource, patch, null) })

        when:
        openShift.patch(resource, patch, '/', project)

        then:
        1 * steps.sh({ OpenShiftHelper.validatePatch(it.script, project, resource, patch, '/') })

        when:
        openShift.patch(resource, null, null, project)

        then:
        thrown IllegalArgumentException

        when:
        openShift.patch(null, patch, path, project)

        then:
        thrown IllegalArgumentException

        when:
        openShift.patch('', patch, path, project)

        then:
        thrown IllegalArgumentException

        when:
        // Path must start with a slash
        openShift.patch(resource, patch, 'some/path', project)

        then:
        thrown IllegalArgumentException

        when:
        // Path must start with a slash
        openShift.patch(resource, patch, '', project)

        then:
        thrown IllegalArgumentException
    }

    def "bulk pause rollouts"() {
        given:
        def steps = Stub(IPipelineSteps)
        def logger = Stub(ILogger)
        def openShift = Spy(new OpenShiftService(steps, logger))
        def resources = [type1: ['resource1','resource2'],type2: ['resource3','resource4']]
        openShift.getResourcesForComponent(_,_,_) >> resources

        when:
        openShift.bulkPause('project', resources)

        then:
        1 * openShift.pause('type1/resource1','project')
        1 * openShift.pause('type1/resource2','project')
        1 * openShift.pause('type2/resource3','project')
        1 * openShift.pause('type2/resource4','project')

        when:
        openShift.bulkPause('project', ['type1','type2'], 'app=foo-bar')

        then:
        1 * openShift.pause('type1/resource1','project')
        1 * openShift.pause('type1/resource2','project')
        1 * openShift.pause('type2/resource3','project')
        1 * openShift.pause('type2/resource4','project')
    }

    def "bulk resume rollouts"() {
        given:
        def steps = Stub(IPipelineSteps)
        def logger = Stub(ILogger)
        def openShift = Spy(new OpenShiftService(steps, logger))
        def resources = [type1: ['resource1','resource2'],type2: ['resource3','resource4']]
        openShift.getResourcesForComponent(_,_,_) >> resources

        when:
        openShift.bulkResume('project', resources)

        then:
        1 * openShift.resume('type1/resource1','project')
        1 * openShift.resume('type1/resource2','project')
        1 * openShift.resume('type2/resource3','project')
        1 * openShift.resume('type2/resource4','project')

        when:
        openShift.bulkResume('project', ['type1','type2'], 'app=foo-bar')

        then:
        1 * openShift.resume('type1/resource1','project')
        1 * openShift.resume('type1/resource2','project')
        1 * openShift.resume('type2/resource3','project')
        1 * openShift.resume('type2/resource4','project')
    }

    def "bulk patch resources"() {
        given:
        def steps = Stub(IPipelineSteps)
        def logger = Stub(ILogger)
        def openShift = Spy(new OpenShiftService(steps, logger))
        def resources = [type1: ['resource1','resource2'],type2: ['resource3','resource4']]
        openShift.getResourcesForComponent(_,_,_) >> resources

        when:
        openShift.bulkPatch('project', resources, [:], '/path')

        then:
        1 * openShift.patch('type1/resource1', [:], '/path','project')
        1 * openShift.patch('type1/resource2', [:], '/path','project')
        1 * openShift.patch('type2/resource3', [:], '/path','project')
        1 * openShift.patch('type2/resource4', [:], '/path','project')

        when:
        openShift.bulkPatch('project', ['type1','type2'], 'app=foo-bar', [:], '/path')

        then:
        1 * openShift.patch('type1/resource1', [:], '/path','project')
        1 * openShift.patch('type1/resource2', [:], '/path','project')
        1 * openShift.patch('type2/resource3', [:], '/path','project')
        1 * openShift.patch('type2/resource4', [:], '/path','project')
    }

    def "getConfigMapData obtain data from a ConfigMap"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = GroovySpy(OpenShiftService, constructorArgs: [steps, new Logger(steps, false)], global: true)
        service.getJSON("project-name", "ConfigMap", "config-name") >> [data: [key: "key1", value: "value1"]]

        when:
        def result = service.getConfigMapData("project-name", "config-name")

        then:
        result == [key: "key1", value: "value1"]
    }

    @Unroll
    def "getPodDataForDeployment if #kind request 0 replicas"() {
        given:
          def steps = Spy(util.PipelineSteps)
          def service = GroovySpy(OpenShiftService, constructorArgs: [steps, new Logger(steps, false)], global: true)
          service.getJSONPath('project-name', object, 'deployment-name', '{.spec.replicas}') >> "0"
          service.getJSONPath('project-name', object, 'deployment-name', '{.spec.template.spec.containers[0].image}') >>
          "image-registry.openshift-image-registry.svc:5000/project-name/component@sha256:76b583cc97aa9efeb99adacb58fa6ef94e5dbe28051637c165111eb104c77d22"

        when:
          def result = service.getPodDataForDeployment("project-name", kind, "deployment-name", 1)

        then:
          result[0].containers."component" == "image-registry.openshift-image-registry.svc:5000/project-name/component@sha256:76b583cc97aa9efeb99adacb58fa6ef94e5dbe28051637c165111eb104c77d22"

        where:
          kind              |   object
          'DeploymentConfig'|   'rc'
          'Deployment'      |   'rs'
    }

    def "getConsoleUrl from cluster"() {
        given:
        def steps = Stub(IPipelineSteps)
        def service = new OpenShiftService(steps, new Logger(steps, false))
        def routeUrl = 'https://console-openshift-console.apps.openshift.com'
        steps.sh( { it.script == 'oc whoami --show-console' } ) >> routeUrl

        when:
        def result = service.getConsoleUrl(steps)

        then:
        result == routeUrl
    }

    @Unroll
    def "getConsoleUrl from cluster if null or empty"() {
        given:
        def steps = Stub(IPipelineSteps)
        def service = new OpenShiftService(steps, new Logger(steps, false))
        steps.sh(_) >> routeUrl

        when:
        def result = service.getConsoleUrl(steps)

        then:
        thrown(RuntimeException)

        where:
        routeUrl << [null, '']
    }

    def "getApplicationDomain from consoleUrl"() {
        given:
        def steps = Stub(IPipelineSteps)
        GroovySpy(OpenShiftService, constructorArgs: [steps, new Logger(steps, false)], global: true)
        def routeUrl = 'https://console-openshift-console.apps.openshift.com'
        def expectedDomain = "apps.openshift.com"
        OpenShiftService.getConsoleUrl(_) >> routeUrl

        when:
        def domain = OpenShiftService.getApplicationDomain(steps)

        then:
        domain == expectedDomain
    }

    def "getApplicationDomain from consoleUrl if no dot"() {
        given:
        def steps = Stub(IPipelineSteps)
        GroovySpy(OpenShiftService, constructorArgs: [steps, new Logger(steps, false)], global: true)
        def routeUrl = 'https://console-openshift-console-apps-openshift-com'
        OpenShiftService.getConsoleUrl(_) >> routeUrl

        when:
        def domain = OpenShiftService.getApplicationDomain(steps)

        then:
        thrown(RuntimeException)
    }
}
