package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

/**
 * Centralises the default configuration values shared by Helm-based deployment
 * stages and strategies.  Call {@link #applyDefaults} once with the pipeline
 * context and the user-supplied config map; values that were not explicitly set
 * by the user will be filled in with sensible defaults.
 */
@TypeChecked
class HelmDeploymentConfig {

    @TypeChecked(TypeCheckingMode.SKIP)
    static void applyDefaults(IContext context, Map<String, Object> config) {
        if (!config.selector) {
            config.selector = context.selector
        }
        if (!config.imageTag) {
            config.imageTag = context.shortGitCommit
        }
        if (!config.deployTimeoutMinutes) {
            config.deployTimeoutMinutes = context.openshiftRolloutTimeout ?: 15
        }
        if (!config.deployTimeoutRetries) {
            config.deployTimeoutRetries = context.openshiftRolloutTimeoutRetries ?: 5
        }
        if (!config.chartDir) {
            config.chartDir = 'chart'
        }
        if (!config.containsKey('helmReleaseName')) {
            config.helmReleaseName = context.componentId
        }
        if (!config.containsKey('helmValues')) {
            config.helmValues = [:]
        }
        if (!config.containsKey('helmValuesFiles')) {
            config.helmValuesFiles = ['values.yaml']
        }
        if (!config.containsKey('helmEnvBasedValuesFiles')) {
            config.helmEnvBasedValuesFiles = []
        }
        if (!config.containsKey('helmDefaultFlags')) {
            config.helmDefaultFlags = ['--install', '--atomic']
        }
        if (!config.containsKey('helmAdditionalFlags')) {
            config.helmAdditionalFlags = []
        }
        if (!config.containsKey('helmDiff')) {
            config.helmDiff = true
        }
        if (!config.helmPrivateKeyCredentialsId) {
            config.helmPrivateKeyCredentialsId = "${context.cdProject}-helm-private-key"
        }
    }

}
