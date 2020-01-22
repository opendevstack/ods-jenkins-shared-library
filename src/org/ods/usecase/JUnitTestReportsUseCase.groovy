package org.ods.usecase

import org.ods.parser.JUnitParser
import org.ods.util.IPipelineSteps

import groovy.json.JsonOutput

class JUnitTestReportsUseCase {

    private IPipelineSteps steps

    JUnitTestReportsUseCase(IPipelineSteps steps) {
        this.steps = steps
    }

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
        def result = [ testsuites: [] ]

        files.inject(result) { sum, current ->
            def testResult = JUnitParser.parseJUnitXML(current.text)
            sum.testsuites.addAll(testResult.testsuites)
        }

        return result
    }

    void reportTestReportsFromPathToJenkins(String path) {
        this.steps.junit("${path}/**/*.xml")
    }
}
