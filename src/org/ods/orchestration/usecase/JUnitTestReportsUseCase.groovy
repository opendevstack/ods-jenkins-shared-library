package org.ods.orchestration.usecase

import com.cloudbees.groovy.cps.NonCPS
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

    Map parseTestReportFiles(List<File> files, Map<String, Map<Integer, Integer>> testStatus_Number_Jira = [:]) {
        def testResults = files.collect { file ->
            JUnitParser.parseJUnitXML(replaceTestIDsInFile(file, testStatus_Number_Jira))
        }

        return this.combineTestResults(testResults)
    }

    private String replaceTestIDsInFile(File file, Map<String, Map<Integer, Integer>> testStatus_Number_Jira) {
        String content = file.text.replaceAll('\\%PROJECT\\%', project.key)
        int max = 0
        testStatus_Number_Jira.each{
            String status = it.key
            it.value.each{
                if ( max < it.value ) {
                    max = it.value
                }
                content = content.replaceAll("\\%TEST_${status}_${it.key}\\%", String.valueOf(it.value))
            }
        }
        while(content.matches('(?s).+\\%TEST_OK_\\d\\%.+')){
            content = content.replaceFirst("\\%TEST_OK_\\d\\%", String.valueOf(++max))
        }
        while(content.matches('(?s).+\\%TEST_KO_\\d\\%.+')){
            content = content.replaceFirst("\\%TEST_KO_\\d\\%", String.valueOf(++max))
        }
        while(content.matches('(?s).+\\%TEST_SKIPPED_\\d\\%.+')){
            content = content.replaceFirst("\\%TEST_SKIPPED_\\d\\%", String.valueOf(++max))
        }
        file.text = content
        return content
    }

    void reportTestReportsFromPathToJenkins(String path) {
        this.steps.junit("${path}/**/*.xml")
    }
}
