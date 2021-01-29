package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.OpenShiftService
import org.ods.util.ILogger

@TypeChecked
class ImportOpenShiftImageStage extends Stage {

    public final String STAGE_NAME = 'Import OpenShift image'
    private final OpenShiftService openShift
    private final ImportOpenShiftImageOptions options

    @TypeChecked(TypeCheckingMode.SKIP)
    ImportOpenShiftImageStage(
        def script,
        IContext context,
        Map<String, Object> config,
        OpenShiftService openShift,
        ILogger logger) {
        super(script, context, logger)
        if (!config.resourceName) {
            config.resourceName = context.componentId
        }
        if (!config.sourceTag) {
            config.sourceTag = context.shortGitCommit
        }
        if (!config.targetTag) {
            config.targetTag = config.sourceTag
        }

        this.options = new ImportOpenShiftImageOptions(config)
        this.openShift = openShift
    }

    protected run() {
        if (!context.environment) {
            logger.warn('Skipping image import because of empty (target) environment ...')
            return
        }

        if (!options.sourceProject) {
            steps.error '''Param 'sourceProject' is required'''
            return
        }

        if (options.imagePullerSecret) {
            openShift.importImageTagFromSourceRegistry(
                context.targetProject,
                options.resourceName,
                options.imagePullerSecret,
                options.sourceProject,
                options.sourceTag,
                options.targetTag
            )
        } else {
            openShift.importImageTagFromProject(
                context.targetProject,
                options.resourceName,
                options.sourceProject,
                options.sourceTag,
                options.targetTag
            )
        }
        logger.info(
            "Imported image '${options.sourceProject}/${options.resourceName}:${options.sourceTag}' into " +
            "'${context.targetProject}/${options.resourceName}:${options.targetTag}'."
        )
    }

    protected String stageLabel() {
        if (options.resourceName != context.componentId) {
            return "${STAGE_NAME} (${options.resourceName})"
        }
        STAGE_NAME
    }

}
