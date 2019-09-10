package org.ods.usecase

import org.ods.parser.JUnitParser

class JUnitTestReportsUseCase {

    private def script

    JUnitTestReportsUseCase(def script) {
        this.script = script
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
            def testReport = JUnitParser.parseJUnitXML(current.text)
            sum.testsuites.addAll(testReport.testsuites)
        }

        return result
    }

    void reportTestReportsFromPathToJenkins(String path) {
        this.script.junit("${path}/**/*.xml")
    }
}
