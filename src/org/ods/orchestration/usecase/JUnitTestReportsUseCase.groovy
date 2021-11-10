package org.ods.orchestration.usecase

import com.cloudbees.groovy.cps.NonCPS

import org.ods.orchestration.parser.JUnitParser
import org.ods.util.IPipelineSteps
import org.ods.orchestration.util.Project

@SuppressWarnings(['JavaIoPackageAccess', 'EmptyCatchBlock'])
class JUnitTestReportsUseCase {

    private final Project project
    private final IPipelineSteps steps

    JUnitTestReportsUseCase(Project project, IPipelineSteps steps) {
        this.project = project
        this.steps = steps
    }

    @NonCPS
    Map combineTestResults(List<Map> testResults) {
        def result = [ testsuites: [] ]
        for (def i = 0; i < testResults.size(); i++) {
            result.testsuites.addAll(testResults[i].testsuites)
        }
        return result
    }

    @NonCPS
    int getNumberOfTestCases(Map testResults) {
        def result = 0

        testResults.testsuites.each { testsuite ->
            result += testsuite.testcases.size()
        }

        return result
    }

    @NonCPS
    List<File> loadTestReportsFromPath(String path) {
        def result = []

        try {
            new File(path).traverse(nameFilter: ~/.*\.xml$/, type: groovy.io.FileType.FILES) { file ->
                result << file
            }
        } catch (FileNotFoundException e) {}

        return result
    }

    @NonCPS
    Map parseTestReportFiles(List<File> files) {
        List<Map> testResults = []
        for (def i = 0; i < files.size(); i++) {
            testResults.add(JUnitParser.parseJUnitXML(files[i].text))
        }
        return this.combineTestResults(testResults)
    }

    void reportTestReportsFromPathToJenkins(String path) {
        this.steps.junit("${path}/**/*.xml")
    }

}
