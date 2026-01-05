package org.ods.component

import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.util.HelmStatus
import org.ods.services.ServiceRegistry
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
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

    // Expected rollout data now uses simple maps with deploymentId and containers
    static Map<String, Map<String, Object>> expectedRolloutData = [
        'core': [
            'deploymentId': 'core',
            'containers'  : [
                'chart-component-a': "${contextData.clusterRegistryAddress}/myproject-dev/helm-component-a@sha256:12345abcdef",
            ],
        ],
        'standalone-gateway': [
            'deploymentId': 'standalone-gateway',
            'containers'  : [
                'chart-component-b': "${contextData.clusterRegistryAddress}/myproject-dev/helm-component-b@sha256:98765fedcba",
            ],
        ]
    ]

    static def config = [
        'helmAdditionalFlags'    : ['--additional-flag-1', '--additional-flag-2'],
        'helmEnvBasedValuesFiles': ['values.env.yaml', 'secrets.env.yaml'],
        'helmValuesFiles'        : ['values.yaml', 'secrets.yaml'],
        'selector'               : "app.kubernetes.io/instance=${contextData.componentId}",
    ]

    // Container images returned by getContainerImagesWithNameFromPodSpec for 'core' deployment
    static def coreContainerImages = [
        'chart-component-a': "${contextData.clusterRegistryAddress}/myproject-dev/helm-component-a@sha256:12345abcdef",
    ]

    // Container images returned by getContainerImagesWithNameFromPodSpec for 'standalone-gateway' deployment
    static def standaloneGatewayContainerImages = [
        'chart-component-b': "${contextData.clusterRegistryAddress}/myproject-dev/helm-component-b@sha256:98765fedcba",
    ]

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

        // Setup stub for getContainerImagesWithNameFromPodSpec
        openShift.getContainerImagesWithNameFromPodSpec(contextData.targetProject, 'Deployment', 'core') >> coreContainerImages
        openShift.getContainerImagesWithNameFromPodSpec(contextData.targetProject, 'Deployment', 'standalone-gateway') >> standaloneGatewayContainerImages

        Map<String, Map<String, Object>> rolloutData
        HelmDeploymentStrategy strategy = new HelmDeploymentStrategy(steps, context, config, openShift, jenkins, logger)

        when:
        rolloutData = strategy.deploy()

        then:
        1 * openShift.helmStatus(contextData.targetProject, contextData.componentId) >> helmStatus

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
                ['--additional-flag-1', '--additional-flag-2', '--history-max 5'],
                true,
        )

        expectedRolloutData.keySet() == rolloutData.keySet()

        expectedRolloutData.each { key, expectedData ->
            def actualData = rolloutData[key]
            assert expectedData == actualData
        }
    }

    def "rollout: check deploymentMean with container images from pod spec"() {
        given:

        def expectedDeploymentMeans = [
            builds: [:],
            deployments: [
                'core-deploymentMean': [
                    type: 'helm',
                    selector: 'app=myproject-core',
                    namespace: 'foo-dev',
                    chartDir: 'chart',
                    helmReleaseName: 'core',
                    helmEnvBasedValuesFiles: [],
                    helmValuesFiles: ['values.yaml'],
                    helmValues: [:],
                    helmDefaultFlags: ['--install', '--atomic'],
                    helmAdditionalFlags: [],
                    helmStatus: [name: 'standalone-app',
                                 version: '43',
                                 namespace: 'myproject-test',
                                 status: 'deployed',
                                 description: 'Upgrade complete',
                                 lastDeployed: '2024-03-04T15:21:09.34520527Z',
                                 resourcesByKind: [
                                     Cluster: ['some-cluster'],
                                     ConfigMap: ['core-appconfig-configmap'],
                                     Deployment: ['core', 'standalone-gateway'],
                                     Secret: ['core-rsa-key-secret','core-security-exandradev-secret','core-security-unify-secret'],
                                     Service: ['core','standalone-gateway']
                                 ]
                    ],
                    resources: [
                        Deployment: ['core', 'standalone-gateway']
                    ]
                ],
                core: [
                    deploymentId: 'core',
                    containers: [
                        'core': 'myproject-dev/helm-component-a@sha256:12345abcdef'
                    ]
                ],
            ]
        ]

        def config = [:]

        def ctxData = contextData + [environment: 'dev', targetProject: 'foo-dev', openshiftRolloutTimeoutRetries: 5, chartDir: 'chart']
        IContext context = new Context(null, ctxData, logger)
        OpenShiftService openShiftService = Mock(OpenShiftService.class)
        // Now uses getContainerImagesWithNameFromPodSpec instead of checkForPodData
        openShiftService.getContainerImagesWithNameFromPodSpec('foo-dev', 'Deployment', 'core') >> [
            'containerA': 'myproject-dev/helm-component-a@sha256:12345abcdef'
        ]
        openShiftService.getContainerImagesWithNameFromPodSpec('foo-dev', 'Deployment', 'standalone-gateway') >> [
            'containerB': 'myproject-dev/helm-component-b@sha256:98765fedcba'
        ]
        ServiceRegistry.instance.add(OpenShiftService, openShiftService)

        JenkinsService jenkinsService = Stub(JenkinsService.class)
        jenkinsService.maybeWithPrivateKeyCredentials(*_) >> { args -> args[1]('/tmp/file') }
        ServiceRegistry.instance.add(JenkinsService, jenkinsService)

        HelmDeploymentStrategy strategy = Spy(HelmDeploymentStrategy, constructorArgs: [null, context, config, openShiftService, jenkinsService, logger])

        when:
        def deploymentResources = HelmStatus.fromJsonObject(FixtureHelper.createHelmCmdStatusMap())
        def rolloutData = strategy.getRolloutData(deploymentResources)
        def actualDeploymentMeans = context.getBuildArtifactURIs()

        then:
        printCallStack()
        assertJobStatusSuccess()

        // Verify rolloutData structure
        rolloutData.containsKey('core')
        rolloutData.containsKey('standalone-gateway')
        rolloutData['core'].deploymentId == 'core'
        rolloutData['core'].containers instanceof Map
        rolloutData['standalone-gateway'].deploymentId == 'standalone-gateway'
        rolloutData['standalone-gateway'].containers instanceof Map

        // Verify deploymentMean was added
        actualDeploymentMeans.deployments.containsKey('core-deploymentMean')
        actualDeploymentMeans.deployments['core-deploymentMean'].type == 'helm'
        actualDeploymentMeans.deployments['core-deploymentMean'].helmReleaseName == 'core'
    }

    def "rollout: check rolloutData with StatefulSet resource"() {
        given:
        def helmStatus = HelmStatus.fromJsonObject(FixtureHelper.createHelmCmdStatusMapWithStatefulSet())

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

        steps.dir(contextData.chartDir, _ as Closure) >> { args -> args[1]() }
        jenkins.maybeWithPrivateKeyCredentials("${contextData.cdProject}-helm-private-key", _ as Closure) >> { args -> args[1]() }

        // Setup stubs for all resource types
        openShift.getContainerImagesWithNameFromPodSpec(contextData.targetProject, 'Deployment', 'core') >> [
            'core': "${contextData.clusterRegistryAddress}/myproject-dev/helm-component-a@sha256:12345abcdef"
        ]
        openShift.getContainerImagesWithNameFromPodSpec(contextData.targetProject, 'Deployment', 'standalone-gateway') >> [
            'standalone-gateway': "${contextData.clusterRegistryAddress}/myproject-dev/helm-component-b@sha256:98765fedcba"
        ]
        openShift.getContainerImagesWithNameFromPodSpec(contextData.targetProject, 'StatefulSet', 'database') >> [
            'postgres': "${contextData.clusterRegistryAddress}/myproject-dev/postgres@sha256:dbsha123456"
        ]

        Map<String, Map<String, Object>> rolloutData
        HelmDeploymentStrategy strategy = new HelmDeploymentStrategy(steps, context, config, openShift, jenkins, logger)

        when:
        rolloutData = strategy.deploy()

        then:
        1 * openShift.helmStatus(contextData.targetProject, contextData.componentId) >> helmStatus
        1 * openShift.helmUpgrade(*_)

        // Verify StatefulSet is included in rolloutData
        rolloutData.containsKey('database')
        rolloutData['database'].deploymentId == 'database'
        rolloutData['database'].containers instanceof Map
        rolloutData['database'].containers['postgres'] == "${contextData.clusterRegistryAddress}/myproject-dev/postgres@sha256:dbsha123456"
    }

    def "rollout: check rolloutData with CronJob resource"() {
        given:
        def helmStatus = HelmStatus.fromJsonObject(FixtureHelper.createHelmCmdStatusMapWithCronJob())

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

        steps.dir(contextData.chartDir, _ as Closure) >> { args -> args[1]() }
        jenkins.maybeWithPrivateKeyCredentials("${contextData.cdProject}-helm-private-key", _ as Closure) >> { args -> args[1]() }

        // Setup stubs for all resource types
        openShift.getContainerImagesWithNameFromPodSpec(contextData.targetProject, 'Deployment', 'core') >> [
            'core': "${contextData.clusterRegistryAddress}/myproject-dev/helm-component-a@sha256:12345abcdef"
        ]
        openShift.getContainerImagesWithNameFromPodSpec(contextData.targetProject, 'Deployment', 'standalone-gateway') >> [
            'standalone-gateway': "${contextData.clusterRegistryAddress}/myproject-dev/helm-component-b@sha256:98765fedcba"
        ]
        openShift.getContainerImagesWithNameFromPodSpec(contextData.targetProject, 'CronJob', 'cleanup-job') >> [
            'cleanup': "${contextData.clusterRegistryAddress}/myproject-dev/cleanup@sha256:cronjob12345"
        ]

        Map<String, Map<String, Object>> rolloutData
        HelmDeploymentStrategy strategy = new HelmDeploymentStrategy(steps, context, config, openShift, jenkins, logger)

        when:
        rolloutData = strategy.deploy()

        then:
        1 * openShift.helmStatus(contextData.targetProject, contextData.componentId) >> helmStatus
        1 * openShift.helmUpgrade(*_)

        // Verify CronJob is included in rolloutData
        rolloutData.containsKey('cleanup-job')
        rolloutData['cleanup-job'].deploymentId == 'cleanup-job'
        rolloutData['cleanup-job'].containers instanceof Map
        rolloutData['cleanup-job'].containers['cleanup'] == "${contextData.clusterRegistryAddress}/myproject-dev/cleanup@sha256:cronjob12345"
    }

    def "rollout: check rolloutData with multi-container Deployment"() {
        given:
        def helmStatus = HelmStatus.fromJsonObject(FixtureHelper.createHelmCmdStatusMapWithMultiContainerDeployment())

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

        steps.dir(contextData.chartDir, _ as Closure) >> { args -> args[1]() }
        jenkins.maybeWithPrivateKeyCredentials("${contextData.cdProject}-helm-private-key", _ as Closure) >> { args -> args[1]() }

        // Setup stubs for all resource types including multi-container deployment
        openShift.getContainerImagesWithNameFromPodSpec(contextData.targetProject, 'Deployment', 'core') >> [
            'core': "${contextData.clusterRegistryAddress}/myproject-dev/helm-component-a@sha256:12345abcdef"
        ]
        openShift.getContainerImagesWithNameFromPodSpec(contextData.targetProject, 'Deployment', 'standalone-gateway') >> [
            'standalone-gateway': "${contextData.clusterRegistryAddress}/myproject-dev/helm-component-b@sha256:98765fedcba"
        ]
        openShift.getContainerImagesWithNameFromPodSpec(contextData.targetProject, 'Deployment', 'multi-container-app') >> [
            'main-app': "${contextData.clusterRegistryAddress}/myproject-dev/main-app@sha256:mainapp12345",
            'envoy-sidecar': "${contextData.clusterRegistryAddress}/myproject-dev/envoy@sha256:envoy67890"
        ]

        Map<String, Map<String, Object>> rolloutData
        HelmDeploymentStrategy strategy = new HelmDeploymentStrategy(steps, context, config, openShift, jenkins, logger)

        when:
        rolloutData = strategy.deploy()

        then:
        1 * openShift.helmStatus(contextData.targetProject, contextData.componentId) >> helmStatus
        1 * openShift.helmUpgrade(*_)

        // Verify multi-container deployment has both containers
        rolloutData.containsKey('multi-container-app')
        rolloutData['multi-container-app'].deploymentId == 'multi-container-app'
        rolloutData['multi-container-app'].containers instanceof Map
        rolloutData['multi-container-app'].containers.size() == 2
        rolloutData['multi-container-app'].containers['main-app'] == "${contextData.clusterRegistryAddress}/myproject-dev/main-app@sha256:mainapp12345"
        rolloutData['multi-container-app'].containers['envoy-sidecar'] == "${contextData.clusterRegistryAddress}/myproject-dev/envoy@sha256:envoy67890"
    }

    def "rollout: check rolloutData with all resource types (Deployment, StatefulSet, CronJob)"() {
        given:
        def helmStatus = HelmStatus.fromJsonObject(FixtureHelper.createHelmCmdStatusMapWithAllResourceTypes())

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

        steps.dir(contextData.chartDir, _ as Closure) >> { args -> args[1]() }
        jenkins.maybeWithPrivateKeyCredentials("${contextData.cdProject}-helm-private-key", _ as Closure) >> { args -> args[1]() }

        // Setup stubs for all resource types
        openShift.getContainerImagesWithNameFromPodSpec(contextData.targetProject, 'Deployment', 'core') >> [
            'core': "${contextData.clusterRegistryAddress}/myproject-dev/helm-component-a@sha256:12345abcdef"
        ]
        openShift.getContainerImagesWithNameFromPodSpec(contextData.targetProject, 'Deployment', 'standalone-gateway') >> [
            'standalone-gateway': "${contextData.clusterRegistryAddress}/myproject-dev/helm-component-b@sha256:98765fedcba"
        ]
        openShift.getContainerImagesWithNameFromPodSpec(contextData.targetProject, 'Deployment', 'multi-container-app') >> [
            'main-app': "${contextData.clusterRegistryAddress}/myproject-dev/main-app@sha256:mainapp12345",
            'envoy-sidecar': "${contextData.clusterRegistryAddress}/myproject-dev/envoy@sha256:envoy67890"
        ]
        openShift.getContainerImagesWithNameFromPodSpec(contextData.targetProject, 'StatefulSet', 'database') >> [
            'postgres': "${contextData.clusterRegistryAddress}/myproject-dev/postgres@sha256:dbsha123456"
        ]
        openShift.getContainerImagesWithNameFromPodSpec(contextData.targetProject, 'CronJob', 'cleanup-job') >> [
            'cleanup': "${contextData.clusterRegistryAddress}/myproject-dev/cleanup@sha256:cronjob12345"
        ]

        Map<String, Map<String, Object>> rolloutData
        HelmDeploymentStrategy strategy = new HelmDeploymentStrategy(steps, context, config, openShift, jenkins, logger)

        when:
        rolloutData = strategy.deploy()

        then:
        1 * openShift.helmStatus(contextData.targetProject, contextData.componentId) >> helmStatus
        1 * openShift.helmUpgrade(*_)

        // Verify all resource types are included
        rolloutData.size() == 5  // core, standalone-gateway, multi-container-app, database, cleanup-job

        // Verify Deployments
        rolloutData['core'].deploymentId == 'core'
        rolloutData['standalone-gateway'].deploymentId == 'standalone-gateway'

        // Verify multi-container Deployment
        rolloutData['multi-container-app'].containers.size() == 2

        // Verify StatefulSet
        rolloutData['database'].deploymentId == 'database'
        rolloutData['database'].containers['postgres'].contains('postgres@sha256')

        // Verify CronJob
        rolloutData['cleanup-job'].deploymentId == 'cleanup-job'
        rolloutData['cleanup-job'].containers['cleanup'].contains('cleanup@sha256')
    }

}
