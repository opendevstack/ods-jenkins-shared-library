package org.ods.component

import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase

class HelmDeploymentConfigSpec extends PipelineSpockTestBase {

    private static final Map<String, Object> DEFAULT_CONTEXT = [
        componentId: 'component-a',
        projectId: 'foo',
        cdProject: 'foo-cd',
        gitCommit: 'abc12345678',
        openshiftRolloutTimeout: 20,
        openshiftRolloutTimeoutRetries: 7,
    ]

    private IContext createContext(Map<String, Object> overrides = [:]) {
        def script = loadScript('vars/withStage.groovy')
        def logger = new Logger(script, false)
        new Context(script, DEFAULT_CONTEXT + overrides, logger)
    }

    def "applyDefaults fills all missing config values from context"() {
        given:
        def context = createContext()
        def config = [:]

        when:
        HelmDeploymentConfig.applyDefaults(context, config)

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
    }

    def "applyDefaults preserves explicitly set config values"() {
        given:
        def context = createContext()
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
            helmPrivateKeyCredentialsId: 'custom-cred',
        ]

        when:
        HelmDeploymentConfig.applyDefaults(context, config)

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

    def "applyDefaults uses fallback when context timeout values are not set"() {
        given:
        // Use different timeout values to verify they flow through
        def context = createContext(openshiftRolloutTimeout: 30, openshiftRolloutTimeoutRetries: 10)
        def config = [:]

        when:
        HelmDeploymentConfig.applyDefaults(context, config)

        then:
        config.deployTimeoutMinutes == 30
        config.deployTimeoutRetries == 10
    }
}
