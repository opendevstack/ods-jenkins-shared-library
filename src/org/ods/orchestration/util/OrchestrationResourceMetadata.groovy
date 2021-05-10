package org.ods.orchestration.util

import org.ods.util.OpenShiftResourceMetadata

class OrchestrationResourceMetadata extends OpenShiftResourceMetadata {
    def project
    OrchestrationResourceMetadata(script, context, openShift, project) {
        super(script, context, openShift)
        this.project = project
    }

    @Override
    def getForcedMetadata() {
        def metadata = super.getForcedMetadata()
        metadata.configItem = project.buildParams.configItem
        metadata.changeId = project.buildParams.changeId
        return metadata
    }
}
