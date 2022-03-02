package org.ods.orchestration.mapper

import org.ods.orchestration.util.Project
import org.ods.util.IPipelineSteps

class TestDataLeVADocumentParamsMapper extends DefaultLeVADocumentParamsMapper {

    TestDataLeVADocumentParamsMapper(Project project, IPipelineSteps steps, Map tests) {
        super(project, steps, [tests: tests])
    }

}
