package org.ods.usecase

import com.cloudbees.groovy.cps.NonCPS

import org.ods.parser.JUnitParser
import org.ods.util.IPipelineSteps
import org.ods.util.MROPipelineUtil
import org.ods.util.Project

class JUnitTestReportsUseCase {

    private Project project
    private IPipelineSteps steps
    private MROPipelineUtil util

    JUnitTestReportsUseCase(Project project, IPipelineSteps steps, MROPipelineUtil util) {
        this.project = project
        this.steps = steps
        this.util = util
    }

    void warnBuildIfTestResultsContainFailure(Map testResults) {
        if (testResults.testsuites.find { (it.errors && it.errors.toInteger() > 0) || (it.failures && it.failures.toInteger() > 0) }) {
            this.project.setHasFailingTests(true)
            this.util.warnBuild("Warning: found failing tests in test reports.")
        }
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
        def result = [ testsuites: [] ]

        files.each { file ->
            def testResult = JUnitParser.parseJUnitXML(file.text)
            result.testsuites.addAll(testResult.testsuites)
        }

        return result
    }

    void reportTestReportsFromPathToJenkins(String path) {
        this.steps.junit("${path}/**/*.xml")
    }
}
