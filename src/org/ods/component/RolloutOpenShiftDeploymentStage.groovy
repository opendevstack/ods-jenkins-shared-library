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

        DeploymentConfig.updateCommonConfig(context, config)
        DeploymentConfig.updateHelmConfig(context, config)
        DeploymentConfig.updateTailorConfig(config)

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
