package org.ods.component

import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.PodData
import spock.lang.Shared
import vars.test_helper.PipelineSpockTestBase

class HelmDeploymentStrategySpec extends PipelineSpockTestBase {
    private Logger logger = Mock(Logger)
    @Shared
    def contextData = [
        gitUrl: 'https://example.com/scm/foo/bar.git',
        gitCommit: 'cd3e9082d7466942e1de86902bb9e663751dae8e',
        gitCommitMessage: 'Foo',
        gitCommitAuthor: 'John Doe',
        gitCommitTime: '2020-03-23 12:27:08 +0100',
        gitBranch: 'master',
        buildUrl: 'https://jenkins.example.com/job/foo-cd/job/foo-cd-bar-master/11/console',
        buildTime: '2020-03-23 12:27:08 +0100',
        odsSharedLibVersion: '2.x',
        projectId: 'foo',
        componentId: 'bar',
        cdProject: 'foo-cd',
        artifactUriStore: [builds: [bar: [:]]]
    ]

    def "rollout: check deploymentMean"() {
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
                    "podMetaDataCreationTimestamp": null,
                    "deploymentId": "bar-124",
                    "podNode": null,
                    "podIp": null,
                    "podStatus": null,
                    "podStartupTimeStamp": null,
                    "containers": null,
                ]
            ]
        ]
        def config = [:]

        def ctxData = contextData + [environment: 'dev', targetProject: 'foo-dev', openshiftRolloutTimeoutRetries: 5, chartDir: 'chart']
        IContext context = new Context(null, ctxData, logger)
        OpenShiftService openShiftService = Mock(OpenShiftService.class)
        openShiftService.checkForPodData(*_) >> [new PodData([deploymentId: "${contextData.componentId}-124"])]
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
