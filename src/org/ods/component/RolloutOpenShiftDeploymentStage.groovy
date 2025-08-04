package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import org.ods.util.PipelineSteps
import org.ods.util.IPipelineSteps

@SuppressWarnings('ParameterCount')
@TypeChecked
class RolloutOpenShiftDeploymentStage extends Stage {

    public final String STAGE_NAME = 'Deploy to OpenShift'
    private final OpenShiftService openShift
    private final JenkinsService jenkins
    private final RolloutOpenShiftDeploymentOptions options
    private IDeploymentStrategy deploymentStrategy
    private Map<String, Object> config

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
        
            // TODO
       // DeploymentConfig deploymentConfig = new DeploymentConfig()
        // deploymentConfig.updateCommonConfig(context, config)
        // deploymentConfig.updateHelmConfig(context, config)
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
            config.helmValuesFiles = [ 'values.yaml' ]
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
            config.tailorVerify = true
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

        this.config = config
        this.options = new RolloutOpenShiftDeploymentOptions(config)
        this.openShift = openShift
        this.jenkins = jenkins
    }

    // This is called from Stage#execute if the branch being built is eligible.
    protected run() {
        if (!context.environment) {
            logger.warn 'Skipping because of empty (target) environment ...'
            return
        }

        // We have to move everything here,
        // otherwise Jenkins will complain
        // about: "hudson.remoting.ProxyException: CpsCallableInvocation{methodName=fileExists, ..."
        def isHelmDeployment = this.steps.fileExists(options.chartDir + '/Chart.yaml')
        logger.info("isHelmDeployment: ${isHelmDeployment}")
        def isTailorDeployment = this.steps.fileExists(options.openshiftDir)

        if (isTailorDeployment && isHelmDeployment) {
            this.steps.error("Must be either a Tailor based deployment or a Helm based deployment")
            throw new IllegalStateException("Must be either a Tailor based deployment or a Helm based deployment")
        }

        IPipelineSteps steps = new PipelineSteps(script)
        IImageRepository imageRepository = new ImageRepositoryOpenShift(steps, context, openShift)

        // Use tailorDeployment in the following cases:
        // (1) We have an openshiftDir
        // (2) We do not have an openshiftDir but neither do we have an indication that it is Helm
        if (isTailorDeployment || (!isHelmDeployment && !isTailorDeployment)) {
            deploymentStrategy = new TailorDeploymentStrategy(steps, context, config, openShift, jenkins, imageRepository, logger)
            String resourcePath = 'org/ods/component/RolloutOpenShiftDeploymentStage.deprecate-tailor.GString.txt'
            def msg = this.steps.libraryResource(resourcePath)
            logger.warn(msg)
        }
        if (isHelmDeployment) {
            deploymentStrategy = new HelmDeploymentStrategy(steps, context, config, openShift, jenkins, imageRepository, logger)
        }
        logger.info("deploymentStrategy: ${deploymentStrategy} -- ${deploymentStrategy.class.name}")
        return deploymentStrategy.deploy()
    }

    protected String stageLabel() {
        if (options.selector != context.selector) {
            return "${STAGE_NAME} (${options.selector})"
        }
        STAGE_NAME
    }

}
