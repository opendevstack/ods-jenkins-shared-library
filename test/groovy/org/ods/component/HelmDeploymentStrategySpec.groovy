package org.ods.component

import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.util.HelmStatus
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.PodData
import spock.lang.Shared
import util.FixtureHelper
import vars.test_helper.PipelineSpockTestBase

class HelmDeploymentStrategySpec extends PipelineSpockTestBase {
    private ILogger logger = Stub()

    @Shared
    static def contextData = [
        'artifactUriStore'              : [
            'builds': [
                'bar': [:],
            ],
        ],
        'buildTime'                     : '2020-03-23 12:27:08 +0100',
        'buildUrl'                      : 'https://jenkins.example.com/job/foo-cd/job/foo-cd-bar-master/11/console',
        'cdProject'                     : 'myproject-cd',
        'chartDir'                      : 'chart',
        'clusterRegistryAddress'        : 'image-registry.openshift.svc:1000',
        'componentId'                   : 'core',
        'environment'                   : 'test',
        'gitBranch'                     : 'master',
        'gitCommit'                     : 'cdefab12345',
        'gitCommitAuthor'               : 'John Doe',
        'gitCommitMessage'              : 'Foo',
        'gitCommitTime'                 : '2020-03-23 12:27:08 +0100',
        'gitUrl'                        : 'https://example.com/scm/foo/bar.git',
        'odsSharedLibVersion'           : '2.x',
        'openshiftRolloutTimeoutRetries': 5,
        'projectId'                     : 'myproject',
        'targetProject'                 : 'myproject-test',
    ]

    static Map<String, List<PodData>> expectedRolloutData = [
        'Deployment/core': [
            new PodData(
                [
                    'containers'                  : [
                        'chart-component-a': "${contextData.clusterRegistryAddress}/myproject-dev/helm-component-a@sha256:12345abcdef",
                    ],
                    'deploymentId'                : 'backend-helm-monorepo-chart-component-a-789abcde',
                    'podMetaDataCreationTimestamp': '2024-11-11T16:01:04Z',
                    'podName'                     : 'backend-helm-monorepo-chart-component-a-789abcde-asdf',
                    'podNamespace'                : "${contextData.targetProject}",
                    'podStatus'                   : 'Running',
                ])
        ],
        'Deployment/standalone-gateway': [
            new PodData(
                [
                    'containers'                  : [
                        'chart-component-b': "${contextData.clusterRegistryAddress}/myproject-dev/helm-component-b@sha256:98765fedcba",
                    ],
                    'deploymentId'                : 'backend-helm-monorepo-chart-component-b-01234abc',
                    'podMetaDataCreationTimestamp': '2024-11-11T16:01:04Z',
                    'podName'                     : 'backend-helm-monorepo-chart-component-b-01234abc-qwerty',
                    'podNamespace'                : "${contextData.targetProject}",
                    'podStatus'                   : 'Running',
                ])
        ]
    ]

    static def config = [
        'helmAdditionalFlags'    : ['--additional-flag-1', '--additional-flag-2'],
        'helmEnvBasedValuesFiles': ['values.env.yaml', 'secrets.env.yaml'],
        'helmValuesFiles'        : ['values.yaml', 'secrets.yaml'],
        'selector'               : "app.kubernetes.io/instance=${contextData.componentId}",
    ]

    static def corePodData = new PodData([
        'containers'                  : [
            'chart-component-a': "${contextData.clusterRegistryAddress}/myproject-dev/helm-component-a@sha256:12345abcdef",
        ],
        'deploymentId'                : 'backend-helm-monorepo-chart-component-a-789abcde',
        'podMetaDataCreationTimestamp': '2024-11-11T16:01:04Z',
        'podName'                     : 'backend-helm-monorepo-chart-component-a-789abcde-asdf',
        'podNamespace'                : "${contextData.targetProject}",
        'podStatus'                   : 'Running',
    ])

    static def standaloneGatewayPodData = new PodData([
        'containers'                  : [
            'chart-component-b': "${contextData.clusterRegistryAddress}/myproject-dev/helm-component-b@sha256:98765fedcba",
        ],
        'deploymentId'                : 'backend-helm-monorepo-chart-component-b-01234abc',
        'podMetaDataCreationTimestamp': '2024-11-11T16:01:04Z',
        'podName'                     : 'backend-helm-monorepo-chart-component-b-01234abc-qwerty',
        'podNamespace'                : "${contextData.targetProject}",
        'podStatus'                   : 'Running',
    ])

    def "rollout: check rolloutData"() {
        given:
        def helmStatus = HelmStatus.fromJsonObject(FixtureHelper.createHelmCmdStatusMap())

        OpenShiftService openShift = Mock()
        JenkinsService jenkins = Stub()
        IPipelineSteps steps = Stub()

        IContext context = Stub {
            getTargetProject() >> contextData.targetProject
            getEnvironment() >> contextData.environment
            getBuildArtifactURIs() >> contextData.artifactUriStore
            getComponentId() >> contextData.componentId
            getClusterRegistryAddress() >> contextData.clusterRegistryAddress
            getCdProject() >> contextData.cdProject
        }

        // Invoke the closures passed to the methods, given that those are part
        // of HelmDeploymentStrategy and should also be tested
        steps.dir(contextData.chartDir, _ as Closure) >> { args -> args[1]() }
        jenkins.maybeWithPrivateKeyCredentials("${contextData.cdProject}-helm-private-key", _ as Closure) >> { args -> args[1]() }

        Map<String, List<PodData>> rolloutData
        HelmDeploymentStrategy strategy = new HelmDeploymentStrategy(steps, context, config, openShift, jenkins, logger)

        when:
        rolloutData = strategy.deploy()

        then:
        1 * openShift.helmStatus(contextData.targetProject, contextData.componentId) >> { helmStatus }

        1 * openShift.helmUpgrade(
                contextData.targetProject,
                contextData.componentId,
                ['values.yaml', 'secrets.yaml', 'values.test.yaml','secrets.test.yaml'],
                [
                    registry: contextData.clusterRegistryAddress,
                    componentId: contextData.componentId,
                    'global.registry': contextData.clusterRegistryAddress,
                    'global.componentId': contextData.componentId,
                    imageNamespace: contextData.targetProject,
                    imageTag: '',
                    'global.imageNamespace': contextData.targetProject,
                    'global.imageTag': '',
                ],
                ['--install', '--atomic'],
                ['--additional-flag-1', '--additional-flag-2'],
                true,
        )

        2 * openShift.checkForPodData(contextData.targetProject, config.selector, _) >> { args ->
            switch (args[2]) {
                case 'core':
                    return [corePodData]
                case 'standalone-gateway':
                    return [standaloneGatewayPodData]
                default:
                    return []
            }
        }

        assert expectedRolloutData.keySet() == rolloutData.keySet()

        expectedRolloutData.each { key, expectedPodData ->
            def actualPodData = rolloutData[key]

            def expectedMaps = expectedPodData*.toMap()
            def actualMaps = actualPodData*.toMap()

            assert expectedMaps == actualMaps
        }
    }

    def "rollout: check deploymentMean when multiple pods then accept only latest"() {
        given:

        def expectedDeploymentMeans = [
            "builds": [:],
            "deployments": [
                "bar-deploymentMean": [
                    "type": "helm",
                    "selector": "app=foo-bar",
                    "chartDir": "chart",
                    "helmReleaseName": "bar",
                    "helmEnvBasedValuesFiles": [],
                    "helmValuesFiles": ["values.yaml"],
                    "helmValues": [:],
                    "helmDefaultFlags": ["--install", "--atomic"],
                    "helmAdditionalFlags": []
                ],
                "bar":[
                    "podName": null,
                    "podNamespace": null,
                    "podMetaDataCreationTimestamp": "2024-12-12T20:10:47Z",
                    "deploymentId": "bar-124",
                    "podNode": null,
                    "podIp": null,
                    "podStatus": null,
                    "podStartupTimeStamp": null,
                    "containers": [
                        "containerA": "imageAnew",
                        "containerB": "imageBnew",
                    ],
                ]
            ]
        ]
        def config = [:]

        def ctxData = contextData + [environment: 'dev', targetProject: 'foo-dev', openshiftRolloutTimeoutRetries: 5, chartDir: 'chart']
        IContext context = new Context(null, ctxData, logger)
        OpenShiftService openShiftService = Mock(OpenShiftService.class)
        openShiftService.checkForPodData(*_) >> [
            new PodData([deploymentId: "${contextData.componentId}-124", podMetaDataCreationTimestamp: "2024-12-12T20:10:46Z", containers: ["containerA": "imageAold", "containerB": "imageBold"]]),
            new PodData([deploymentId: "${contextData.componentId}-124", podMetaDataCreationTimestamp: "2024-12-12T20:10:47Z", containers: ["containerA": "imageAnew", "containerB": "imageBnew"]]),
            new PodData([deploymentId: "${contextData.componentId}-123", podMetaDataCreationTimestamp: "2024-11-11T20:10:46Z"])
        ]
        ServiceRegistry.instance.add(OpenShiftService, openShiftService)

        JenkinsService jenkinsService = Stub(JenkinsService.class)
        jenkinsService.maybeWithPrivateKeyCredentials(*_) >> { args -> args[1]('/tmp/file') }
        ServiceRegistry.instance.add(JenkinsService, jenkinsService)

        HelmDeploymentStrategy strategy = Spy(HelmDeploymentStrategy, constructorArgs: [null, context, config, openShiftService, jenkinsService, logger])

        when:
        def deploymentResources = [Deployment: ['bar']]
        def rolloutData = strategy.getRolloutData(deploymentResources)
        def actualDeploymentMeans = context.getBuildArtifactURIs()


        then:
        printCallStack()
        assertJobStatusSuccess()

        assert expectedDeploymentMeans == actualDeploymentMeans
    }

    def "rollout: check deploymentMean when multiple pods with same timestamp but different image then pipeline fails"() {
        given:

        def expectedDeploymentMeans = [
            "builds": [:],
            "deployments": [
                "bar-deploymentMean": [
                    "type": "helm",
                    "selector": "app=foo-bar",
                    "chartDir": "chart",
                    "helmReleaseName": "bar",
                    "helmEnvBasedValuesFiles": [],
                    "helmValuesFiles": ["values.yaml"],
                    "helmValues": [:],
                    "helmDefaultFlags": ["--install", "--atomic"],
                    "helmAdditionalFlags": []
                ],
                "bar":[
                    "podName": null,
                    "podNamespace": null,
                    "podMetaDataCreationTimestamp": "2024-12-12T20:10:47Z",
                    "deploymentId": "bar-124",
                    "podNode": null,
                    "podIp": null,
                    "podStatus": null,
                    "podStartupTimeStamp": null,
                    "containers": [
                        "containerA": "imageAnew",
                        "containerB": "imageBnew",
                    ],
                ]
            ]
        ]
        def config = [:]

        def ctxData = contextData + [environment: 'dev', targetProject: 'foo-dev', openshiftRolloutTimeoutRetries: 5, chartDir: 'chart']
        IContext context = new Context(null, ctxData, logger)
        OpenShiftService openShiftService = Mock(OpenShiftService.class)
        openShiftService.checkForPodData(*_) >> [
            new PodData([deploymentId: "${contextData.componentId}-124", podMetaDataCreationTimestamp: "2024-12-12T20:10:47Z", containers: ["containerA": "imageAnew", "containerB": "imageBnew"]]),
            new PodData([deploymentId: "${contextData.componentId}-124", podMetaDataCreationTimestamp: "2024-12-12T20:10:47Z", containers: ["containerA": "imageAold", "containerB": "imageBold"]]),
        ]
        ServiceRegistry.instance.add(OpenShiftService, openShiftService)

        JenkinsService jenkinsService = Stub(JenkinsService.class)
        jenkinsService.maybeWithPrivateKeyCredentials(*_) >> { args -> args[1]('/tmp/file') }
        ServiceRegistry.instance.add(JenkinsService, jenkinsService)

        HelmDeploymentStrategy strategy = Spy(HelmDeploymentStrategy, constructorArgs: [null, context, config, openShiftService, jenkinsService, logger])

        when:
        def deploymentResources = [Deployment: ['bar']]
        def rolloutData = strategy.getRolloutData(deploymentResources)
        def actualDeploymentMeans = context.getBuildArtifactURIs()


        then:
        printCallStack()
        def e = thrown(RuntimeException)

        assert e.message == "Unable to determine the most recent Pod. Multiple pods running with the same latest creation timestamp and different images found for bar"

    }

}
