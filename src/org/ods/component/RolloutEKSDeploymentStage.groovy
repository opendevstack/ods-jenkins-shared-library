package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import org.ods.util.PipelineSteps
import org.ods.util.IPipelineSteps
import org.ods.services.EKSService

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

        DeploymentConfig.updateCommonConfig(context, config)
        DeploymentConfig.updateHelmConfig(context, config)
       
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
        imageRepository = new ImageRepositoryEKS(steps, context, eks, ocToken, awsPassword)
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
