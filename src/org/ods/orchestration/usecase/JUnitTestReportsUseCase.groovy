package org.ods.orchestration.usecase

import com.cloudbees.groovy.cps.NonCPS
import groovy.text.GStringTemplateEngine
import org.ods.orchestration.parser.JUnitParser
import org.ods.orchestration.util.Project
import org.ods.util.IPipelineSteps

@SuppressWarnings(['JavaIoPackageAccess', 'EmptyCatchBlock'])
class JUnitTestReportsUseCase {

    private Project project
    private IPipelineSteps steps

    JUnitTestReportsUseCase(Project project, IPipelineSteps steps) {
        this.project = project
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

    Map parseTestReportFiles(List<File> files, Map<String, String> linkTestsInJira = [:]) {
        def testResults = files.collect { file ->
            JUnitParser.parseJUnitXML(replaceTestIDsInFile(file, linkTestsInJira))
        }
        return this.combineTestResults(testResults)
    }

    void reportTestReportsFromPathToJenkins(String path) {
        this.steps.junit("${path}/**/*.xml")
    }

    private String replaceTestIDsInFile(File file, Map<String, String> linkTestsInJira = [:]) {
        if(linkTestsInJira) {
            Map<String, String> bindMap = linkTestsInJira.clone()
            bindMap.put('project', project.key)
            def content = new GStringTemplateEngine().createTemplate(file).make(bindMap)
            file.text = content.toString()
        }
        return file.text
    }
}
