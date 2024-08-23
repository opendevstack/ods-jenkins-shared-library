package org.ods.component

import groovy.json.JsonSlurperClassic
import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.HelmStatusSimpleData
import org.ods.util.Logger
import org.ods.util.PodData
import spock.lang.Shared
import util.FixtureHelper
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
        projectId: 'guardians',
        componentId: 'core',
        cdProject: 'guardians-cd',
        artifactUriStore: [builds: [bar: [:]]]
    ]

    def "rollout: check deploymentMean"() {
        given:

        def expectedDeploymentMean = [
            type                   : "helm",
            selector               : "app=guardians-core",
            chartDir               : "chart",
            helmReleaseName        : "core",
            helmEnvBasedValuesFiles: [],
            helmValuesFiles        : ["values.yaml"],
            helmValues             : [:],
            helmDefaultFlags       : ["--install", "--atomic"],
            helmAdditionalFlags    : [],
            helmStatus             : [
                name      : "standalone-app",
                version  : "43",
                namespace        : "guardians-test",
                status     : "deployed",
                description: "Upgrade complete",
                lastDeployed     : "2024-03-04T15:21:09.34520527Z",
                resourcesByKind        : [
                    ConfigMap: ['core-appconfig-configmap'],
                    Deployment: ['core', 'standalone-gateway'],
                    Service: ['core', 'standalone-gateway'],
                    Cluster: ['edb-cluster'],
                    Secret: ['core-rsa-key-secret', 'core-security-exandradev-secret', 'core-security-unify-secret']
                ]
            ]
        ]
        def expectedDeploymentMeans = [
            builds     : [:],
            deployments: [
                "core-deploymentMean"              : expectedDeploymentMean,
                "core"                             : [
                    podName                     : null,
                    podNamespace                : null,
                    podMetaDataCreationTimestamp: null,
                    deploymentId                : "core-124",
                    podStatus                   : null,
                    containers                  : null
                ],
                "standalone-gateway-deploymentMean": expectedDeploymentMean,
                "standalone-gateway"               : [
                    podName                     : null,
                    podNamespace                : null,
                    podMetaDataCreationTimestamp: null,
                    deploymentId                : "core-124",
                    podStatus                   : null,
                    containers                  : null,
                ],
            ]
        ]

        def config = [:]

        def helmStatusFile = new FixtureHelper().getResource("helmstatus.json")
        def helmStatus = HelmStatusSimpleData.fromJsonObject(new JsonSlurperClassic().parseText(helmStatusFile.text))

        def ctxData = contextData + [environment: 'test', targetProject: 'guardians-test', openshiftRolloutTimeoutRetries: 5, chartDir: 'chart']
        IContext context = new Context(null, ctxData, logger)
        OpenShiftService openShiftService = Mock(OpenShiftService.class)
        openShiftService.checkForPodData(*_) >> [new PodData([deploymentId: "${contextData.componentId}-124"])]
        ServiceRegistry.instance.add(OpenShiftService, openShiftService)

        JenkinsService jenkinsService = Stub(JenkinsService.class)
        jenkinsService.maybeWithPrivateKeyCredentials(*_) >> { args -> args[1]('/tmp/file') }
        ServiceRegistry.instance.add(JenkinsService, jenkinsService)

        HelmDeploymentStrategy strategy = Spy(HelmDeploymentStrategy, constructorArgs: [null, context, config, openShiftService, jenkinsService, logger])

        when:
        strategy.getRolloutData(helmStatus)
        def actualDeploymentMeans = context.getBuildArtifactURIs()


        then:
        printCallStack()
        assertJobStatusSuccess()

        assert expectedDeploymentMeans == actualDeploymentMeans
    }
}
