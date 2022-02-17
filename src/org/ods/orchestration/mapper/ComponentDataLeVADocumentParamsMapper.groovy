package org.ods.orchestration.mapper

import org.ods.orchestration.util.Project
import org.ods.util.IPipelineSteps

class ComponentDataLeVADocumentParamsMapper extends DefaultLeVADocumentParamsMapper {

    ComponentDataLeVADocumentParamsMapper(Project project, IPipelineSteps steps, Map tests, Map repo) {
        super(project, steps, [tests: tests, repo: repo])
    }
}
