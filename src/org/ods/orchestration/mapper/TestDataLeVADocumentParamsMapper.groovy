package org.ods.orchestration.mapper

import org.ods.orchestration.util.Project
import org.ods.util.IPipelineSteps

class TestDataLeVADocumentParamsMapper extends DefaultLeVADocumentParamsMapper {
    private final Map testData

    TestDataLeVADocumentParamsMapper(Project project, IPipelineSteps steps, Map testData) {
        super(project, steps)
        this.testData = testData
    }

    Map build() {
        Map data = super.build()
        data << [tests: testData]
        return data
    }
}
