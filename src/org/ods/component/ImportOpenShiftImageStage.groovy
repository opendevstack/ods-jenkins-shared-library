package org.ods.component

import org.ods.services.OpenShiftService
import org.ods.util.ILogger

class ImportOpenShiftImageStage extends Stage {

    public final String STAGE_NAME = 'Import OpenShift image'
    private final OpenShiftService openShift

    ImportOpenShiftImageStage(
        def script,
        IContext context,
        Map config,
        OpenShiftService openShift,
        ILogger logger) {
        super(script, context, config, logger)
        if (!config.resourceName) {
            config.resourceName = context.componentId
        }
        if (!config.sourceTag) {
            config.sourceTag = context.shortGitCommit
        }
        if (!config.targetTag) {
            config.targetTag = config.sourceTag
        }
        this.openShift = openShift
    }

    protected run() {
        if (!context.environment) {
            logger.warn('Skipping image import because of empty (target) environment ...')
            return
        }

        if (!config.sourceProject) {
            script.error '''Param 'sourceProject' is required'''
            return
        }

        if (config.imagePullerSecret) {
            openShift.importImageTagFromSourceRegistry(
                config.resourceName,
                config.imagePullerSecret,
                config.sourceProject,
                config.sourceTag,
                config.targetTag
            )
        } else {
            openShift.importImageTagFromProject(
                config.resourceName,
                config.sourceProject,
                config.sourceTag,
                config.targetTag
            )
        }
        logger.info(
            "Imported image '${config.sourceProject}/${config.resourceName}:${config.sourceTag}' into " +
            "'${context.targetProject}/${config.resourceName}:${config.targetTag}'."
        )
    }

    protected String stageLabel() {
        if (config.resourceName != context.componentId) {
            return "${STAGE_NAME} (${config.resourceName})"
        }
        STAGE_NAME
    }

}
