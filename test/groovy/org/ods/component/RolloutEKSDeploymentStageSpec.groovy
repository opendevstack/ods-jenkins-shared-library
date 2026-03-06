package org.ods.component

import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase

class RolloutEKSDeploymentStageSpec extends PipelineSpockTestBase {

    private static final Map<String, Object> DEFAULT_CONTEXT = [
        componentId: 'component-a',
        projectId: 'foo',
        cdProject: 'foo-cd',
        selector: 'app=component-a',
        gitCommit: 'abc12345678',
        openshiftRolloutTimeout: 20,
        openshiftRolloutTimeoutRetries: 7
    ]

    private RolloutEKSDeploymentStage createStage(Map<String, Object> config = [:], Map<String, Object> contextValues = [:]) {
        def script = loadScript('vars/withStage.groovy')
        def logger = Spy(new Logger(script, false))
        IContext context = new Context(script, DEFAULT_CONTEXT + contextValues, logger)
        OpenShiftService openShift = Mock(OpenShiftService)
        JenkinsService jenkins = Mock(JenkinsService)
        new RolloutEKSDeploymentStage(script, context, config, openShift, jenkins, [:], logger)
    }

    def "constructor sets default rollout and helm options from context"() {
        given:
        def config = [:]

        when:
        def stage = createStage(config)

        then:
        config.selector == 'app=foo-component-a'
        config.imageTag == 'abc12345'
        config.deployTimeoutMinutes == 20
        config.deployTimeoutRetries == 7
        config.chartDir == 'chart'
        config.helmReleaseName == 'component-a'
        config.helmValues == [:]
        config.helmValuesFiles == ['values.yaml']
        config.helmEnvBasedValuesFiles == []
        config.helmDefaultFlags == ['--install', '--atomic']
        config.helmAdditionalFlags == []
        config.helmDiff == true
        config.helmPrivateKeyCredentialsId == 'foo-cd-helm-private-key'
        stage != null
    }

    def "constructor keeps explicit options unchanged"() {
        given:
        def config = [
            selector: 'custom=selector',
            imageTag: 'tag-1',
            deployTimeoutMinutes: 3,
            deployTimeoutRetries: 2,
            chartDir: 'helm-chart',
            helmReleaseName: 'custom-release',
            helmValues: [k: 'v'],
            helmValuesFiles: ['a.yaml'],
            helmEnvBasedValuesFiles: ['b.yaml'],
            helmDefaultFlags: ['--wait'],
            helmAdditionalFlags: ['--debug'],
            helmDiff: false,
            helmPrivateKeyCredentialsId: 'custom-cred'
        ]

        when:
        createStage(config)

        then:
        config.selector == 'custom=selector'
        config.imageTag == 'tag-1'
        config.deployTimeoutMinutes == 3
        config.deployTimeoutRetries == 2
        config.chartDir == 'helm-chart'
        config.helmReleaseName == 'custom-release'
        config.helmValues == [k: 'v']
        config.helmValuesFiles == ['a.yaml']
        config.helmEnvBasedValuesFiles == ['b.yaml']
        config.helmDefaultFlags == ['--wait']
        config.helmAdditionalFlags == ['--debug']
        config.helmDiff == false
        config.helmPrivateKeyCredentialsId == 'custom-cred'
    }

    def "stage label includes selector when different from context"() {
        when:
        def stage = createStage([selector: 'other=selector'])

        then:
        stage.stageLabel() == 'Deploy to OpenShift (other=selector)'
    }

    def "stage label falls back to stage name when selector matches context"() {
        when:
        def stage = createStage([selector: 'app=foo-component-a'])

        then:
        stage.stageLabel() == 'Deploy to OpenShift'
    }
}
