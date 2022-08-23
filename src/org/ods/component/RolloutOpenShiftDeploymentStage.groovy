package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.util.ILogger

@SuppressWarnings('ParameterCount')
@TypeChecked
class RolloutOpenShiftDeploymentStage extends Stage {

    public final String STAGE_NAME = 'Deploy to OpenShift'
    private final OpenShiftService openShift
    private final JenkinsService jenkins
    private final RolloutOpenShiftDeploymentOptions options
    private final IDeploymentStrategy deploymentStrategy

    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity'])
    @TypeChecked(TypeCheckingMode.SKIP)
    RolloutOpenShiftDeploymentStage(
        def script,
        IContext context,
        Map<String, Object> config,
        OpenShiftService openShift,
        JenkinsService jenkins,
        ILogger logger) {
        super(script, context, logger)
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
        // Helm options
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
        // Tailor options
        if (!config.openshiftDir) {
            config.openshiftDir = 'openshift'
        }
        if (!config.tailorPrivateKeyCredentialsId) {
            config.tailorPrivateKeyCredentialsId = "${context.cdProject}-tailor-private-key"
        }
        if (!config.tailorSelector) {
            config.tailorSelector = config.selector
        }
        if (!config.containsKey('tailorVerify')) {
            config.tailorVerify = false
        }
        if (!config.containsKey('tailorExclude')) {
            config.tailorExclude = 'bc,is'
        }
        if (!config.containsKey('tailorParamFile')) {
            config.tailorParamFile = '' // none apart the automatic param file
        }
        if (!config.containsKey('tailorPreserve')) {
            config.tailorPreserve = [] // do not preserve any paths in live config
        }
        if (!config.containsKey('tailorParams')) {
            config.tailorParams = []
        }

        this.options = new RolloutOpenShiftDeploymentOptions(config)
        this.openShift = openShift
        this.jenkins = jenkins

        def isHelmDeployment = steps.fileExists(options.chartDir + '/Chart.yaml')
        def isTailorDeployment = steps.fileExists(options.openshiftDir)

        if (isTailorDeployment && isHelmDeployment){
            steps.error("Must be either a Tailor based deployment or a Helm based deployment")
            throw new IllegalStateException("Must be either a Tailor based deployment or a Helm based deployment")
        }

        if (isTailorDeployment){
            deploymentStrategy = new TailorDeploymentStrategy(script, context, config, openShift, jenkins, logger)
        }
        if (isHelmDeployment) {
            deploymentStrategy = new HelmDeploymentStrategy(script, context, config, openShift, jenkins, logger)
        }
    }

    // This is called from Stage#execute if the branch being built is eligible.
    protected run() {
        if (!context.environment) {
            logger.warn 'Skipping because of empty (target) environment ...'
            return
        }
        return deploymentStrategy.deploy()
    }
}
