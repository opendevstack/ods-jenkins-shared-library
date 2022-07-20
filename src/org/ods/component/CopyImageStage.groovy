package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.util.ILogger

@SuppressWarnings('ParameterCount')
@TypeChecked
class CopyImageStage extends Stage {

    public final String STAGE_NAME = 'Copy Container Image'
    private final OpenShiftService openShift
    private final JenkinsService jenkins
    private final ILogger logger
    private final CopyImageOptions options

    @TypeChecked(TypeCheckingMode.SKIP)
    CopyImageStage(
        def script,
        IContext context,
        Map<String, Object> config,
        OpenShiftService openShift,
        JenkinsService jenkins,
        ILogger logger) {
        super(script, context, logger)
        // If user did not explicitly define which branches to build images for,
        // build images for all branches which are mapped (deployed) to an environment.
        // In orchestration pipelines, always build the image.
        if (!config.containsKey('branches') && !config.containsKey('branch')) {
            if (context.triggeredByOrchestrationPipeline) {
                config.branch = context.gitBranch
            } else {
                config.branches = context.branchToEnvironmentMapping.keySet().toList()
            }
        }
        if (!config.resourceName) {
            config.resourceName = context.componentId
        }
        // if (!config.imageTag) {
        //     config.imageTag = context.shortGitCommit
        // }
        // if (!config.imageLabels) {
        //     config.imageLabels = [:]
        // }
        // if (context.extensionImageLabels) {
        //     config.imageLabels << context.extensionImageLabels
        // }
        // if (!config.buildArgs) {
        //     config.buildArgs = [:]
        // }
        // if (!config.buildTimeoutMinutes) {
        //     config.buildTimeoutMinutes = context.openshiftBuildTimeout
        // }
        // if (!config.buildTimeoutRetries) {
        //     config.buildTimeoutRetries = context.openshiftBuildTimeoutRetries
        // }
        // if (!config.dockerDir) {
        //     config.dockerDir = context.dockerDir
        // }

        this.options = new CopyImageOptions(config)
        this.openShift = openShift
        this.jenkins = jenkins
        this.logger = logger
    }

    // This is called from Stage#execute if the branch being built is eligible.
    protected run() {
        logger.info("Copy the image ${config.sourceImageUrlIncludingRegistry}!")
    }

    protected String stageLabel() {
        if (options.resourceName != context.componentId) {
            return "${STAGE_NAME} (${options.resourceName})"
        }
        STAGE_NAME
    }
}
