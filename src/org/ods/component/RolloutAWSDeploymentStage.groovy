package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.AWSService
import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger

@SuppressWarnings('ParameterCount')
@TypeChecked
    class RolloutAWSDeploymentStage extends Stage {

    public final String STAGE_NAME = 'Deploy to OpenShift'
    private final OpenShiftService openShift
    private final JenkinsService jenkins
    private final RolloutOpenShiftDeploymentOptions options
    private IDeploymentStrategy depStr
    private Map<String, Object> config
    private Map<String, Object> awsEnvironmentVars

    @TypeChecked(TypeCheckingMode.SKIP)
    RolloutAWSDeploymentStage(
        def script,
        IContext context,
        Map<String, Object> config,
        OpenShiftService openShift,
        JenkinsService jenkins,
        Map<String, Object> awsEnvironmentVars,
        ILogger logger) {
        super(script, context, logger)
        HelmDeploymentConfig.applyDefaults(context, config)
        this.config = config
        this.options = new RolloutOpenShiftDeploymentOptions(config)
        this.openShift = openShift
        this.jenkins = jenkins
        this.awsEnvironmentVars = awsEnvironmentVars
    }

    // This is called from Stage#execute if the branch being built is eligible.
    protected run() {
        ImageECR imageECR = new ImageECR(steps, context, logger, awsEnvironmentVars)
        imageECR.fetchOCToken()
        AWSService aWSService = new AWSService(steps, context, awsEnvironmentVars, logger)
        aWSService.awsLogin()
        if (config.helmWithOnlyECR) {
            logger.info('ECR-only deployment configured (no EKS rollout)')
            depStr = new ECROnlyDeploymentStrategy(context, config, imageECR, logger)
        } else {
            logger.info('Full EKS deployment configured')
            aWSService.setEKSCluster()
            depStr = new HelmDeploymentStrategy(steps, context, config, openShift, jenkins, imageECR, logger)
        }

        logger.info("deploymentStrategy: ${depStr} -- ${depStr.class.name}")
        return depStr.deploy()
    }

    protected String stageLabel() {
        if (options.selector != context.selector) {
            return "${STAGE_NAME} (${options.selector})"
        }
        STAGE_NAME
    }

}
