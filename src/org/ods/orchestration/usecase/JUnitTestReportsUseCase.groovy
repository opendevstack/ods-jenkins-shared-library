package org.ods.orchestration.usecase

import com.cloudbees.groovy.cps.NonCPS

import org.ods.orchestration.parser.JUnitParser
import org.ods.util.IPipelineSteps
import org.ods.orchestration.util.Context

@SuppressWarnings(['JavaIoPackageAccess', 'EmptyCatchBlock'])
class JUnitTestReportsUseCase {

    private Context context
    private IPipelineSteps steps

    JUnitTestReportsUseCase(Context context, IPipelineSteps steps) {
        this.context = context
        this.steps = steps
    }

    Map combineTestResults(List<Map> testResults) {
        def result = [ testsuites: [] ]

        testResults.each { testResult ->
            result.testsuites.addAll(testResult.testsuites)
        }

        return result
    }

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

    Map parseTestReportFiles(List<File> files) {
        def testResults = files.collect { file ->
            JUnitParser.parseJUnitXML(file.text)
        }

        return this.combineTestResults(testResults)
    }

    void reportTestReportsFromPathToJenkins(String path) {
        this.steps.junit("${path}/**/*.xml")
    }
}
