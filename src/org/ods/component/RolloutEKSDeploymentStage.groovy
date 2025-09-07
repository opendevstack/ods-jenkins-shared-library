package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import org.ods.util.PipelineSteps
import org.ods.util.IPipelineSteps
import org.ods.services.EKSService
import com.cloudbees.groovy.cps.NonCPS

@SuppressWarnings('ParameterCount')
@TypeChecked
class RolloutEKSDeploymentStage extends Stage {

    public final String STAGE_NAME = 'Deploy to OpenShift'
    private final OpenShiftService openShift
    private final JenkinsService jenkins
    private final RolloutOpenShiftDeploymentOptions options
    private IDeploymentStrategy deploymentStrategy
    private Map<String, Object> config
    private Map<String, Object> awsEnvironmentVars

    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity'])
    @TypeChecked(TypeCheckingMode.SKIP)
    RolloutEKSDeploymentStage(
        def script,
        IContext context,
        Map<String, Object> config,
        OpenShiftService openShift,
        JenkinsService jenkins,
        Map<String, Object> awsEnvironmentVars,
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
        this.config = config
        this.options = new RolloutOpenShiftDeploymentOptions(config)
        this.openShift = openShift
        this.jenkins = jenkins
        this.awsEnvironmentVars = awsEnvironmentVars
    }

    // This is called from Stage#execute if the branch being built is eligible.
    protected run() {
        EKSService eks = new EKSService(steps, context, awsEnvironmentVars,  logger)
        String ocToken = eks.getOCTOken()
        eks.setEKSCluster() // This should be set after getOCTOken!!!
        String awsPassword = eks.getLoginPassword()
        ImageRepositoryEKS imageRepository = new ImageRepositoryEKS(steps, context, eks, ocToken, awsPassword)
        deploymentStrategy = new HelmDeploymentStrategy(steps, context, config, openShift, jenkins, imageRepository, logger)
        
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
