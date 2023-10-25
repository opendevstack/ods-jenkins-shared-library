package org.ods.services

import groovy.json.JsonSlurperClassic
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
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

    def "warn build"() {
        given:
        def steps = Mock(IPipelineSteps)
        steps.currentBuild >> [result:'UNSTABLE']
        def logger = Mock(ILogger)
        def openShift = new OpenShiftService(steps, logger)
        def message = "Test"

        when:
        openShift.warnBuild(message)

        then:
        1 * openShift.logger.warn(message)
        openShift.steps.currentBuild.result == 'UNSTABLE'
    }

    def "extract repo name from target"() {
        given:
        def steps = Mock(IPipelineSteps)
        def logger = Mock(ILogger)
        def openShift = new OpenShiftService(steps, logger)

        when:
        def result = openShift.extractRepoNameFromTarget(target)

        then:
        result == expected

        where:
        target                      |       expected
        null                        |       "N/A"
        [:]                         |       "N/A"
        [selector:null]             |       "N/A"
        [selector:"app="]           |       ""
        [selector:"app=Test"]       |       "Test"
        [selector:"app=Proj-Test"]  |       "Test"
    }

    def "mark repo with tailor warning"() {
        given:
        def steps = Mock(IPipelineSteps)
        def logger = Mock(ILogger)
        def openShift = new OpenShiftService(steps, logger)
        def testProject =
            [repositories:
                 [
                    [id:"golang", branch:"master", type:"ods",
                     doInstall:true],
                    [id:"other", branch:"master", type:"ods",
                     doInstall:true],
                    [id:"third", branch:"master", type:"ods",
                     doInstall:true]
                 ]
            ]


        when:
        openShift.markRepoWithTailorWarn(testProject, "third")

        then:
        println testProject
        testProject.repositories.get(0).tailorWarning == null
        testProject.repositories.get(1).tailorWarning == null
        testProject.repositories.get(2).tailorWarning == true
    }
}
