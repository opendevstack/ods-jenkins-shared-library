package org.ods.orchestration.mapper

import org.ods.orchestration.util.Project
import org.ods.util.IPipelineSteps

class ComponentDataLeVADocumentParamsMapper extends TestDataLeVADocumentParamsMapper {
    private final Map repo

    ComponentDataLeVADocumentParamsMapper(Project project, IPipelineSteps steps, Map testData, Map repo) {
        super(project, steps, testData)
        this.repo = repo
    }

    Map build() {
        Map data = super.build()
        data << [repo: repo]
        return data
    }
}
