package org.ods.orchestration.util

import org.ods.util.OpenShiftResourceMetadata

class OrchestrationResourceMetadata extends OpenShiftResourceMetadata {
    def project
    OrchestrationResourceMetadata(
        project,
        script,
        context,
        config,
        openShift,
        jenkins,
        logger) {
        super(script, context, config, openShift, jenkins, logger)
        this.project = project
    }

    def getForcedMetadata() {
        def metadata = super.getForcedMetadata()
        metadata.configItem = project.buildParams.configItem
        metadata.changeId = project.buildParams.changeId
        return metadata
    }
}
