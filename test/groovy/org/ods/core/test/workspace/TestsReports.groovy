package org.ods.core.test.workspace

import groovy.util.logging.Slf4j
import org.ods.orchestration.usecase.JUnitTestReportsUseCase
import org.ods.orchestration.util.PipelineUtil
import org.ods.orchestration.util.Project
import org.ods.util.IPipelineSteps

/**
 * Tests results should be at "${steps.env.WORKSPACE}/xunit"
 */
@Slf4j
class TestsReports {
    private final static List TYPES = [Project.TestType.INSTALLATION, Project.TestType.INTEGRATION, Project.TestType.ACCEPTANCE, Project.TestType.UNIT]
    private final JUnitTestReportsUseCase jUnitTestReport
    private final IPipelineSteps steps

    TestsReports(IPipelineSteps steps, JUnitTestReportsUseCase jUnitTestReportsUseCase){
        this.steps = steps
        this.jUnitTestReport = jUnitTestReportsUseCase
    }

    Map getAllResults(List<Map> repos){
        Map data = [:]
        data.tests = [:]
        TYPES.each { type ->
            repos.each { repo ->
                data.tests << [(type.toLowerCase()): getResults(repo.id, type.toLowerCase())]
            }
        }
        return data
    }

    /**
     * see @org.ods.orchestration.Stage#getTestResults
     */
    Map getResults(String repoId, String type) {
        log.debug("Collecting JUnit XML Reports ('${type}') for ${repoId}")

        def testReportsPath = "${PipelineUtil.XUNIT_DOCUMENTS_BASE_DIR}/${repoId}/${type}"
        def testReportFiles = jUnitTestReport.loadTestReportsFromPath( "${steps.env.WORKSPACE}/${testReportsPath}")

        return [
            testReportFiles: testReportFiles,
            testResults: jUnitTestReport.parseTestReportFiles(testReportFiles),
        ]
    }
}
