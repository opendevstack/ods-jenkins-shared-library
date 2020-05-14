package org.ods.orchestration.usecase

import java.nio.file.Files

import org.ods.orchestration.parser.*
import org.ods.orchestration.util.*
import org.ods.util.IPipelineSteps

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class JUnitTestReportsUseCaseSpec extends SpecHelper {

    Project project
    IPipelineSteps steps
    JUnitTestReportsUseCase usecase

    def setup() {
        project = createProject()
        steps = Spy(util.PipelineSteps)
        usecase = new JUnitTestReportsUseCase(project, steps)
    }

    def "combine test results"() {
        given:
        def testResult1 = [
            testsuites: [
                [
                    testcases: [
                        [ a: 1 ]
                    ]
                ]
            ]
        ]

        def testResult2 = [
            testsuites: [
                [
                    testcases: [
                        [ b: 2 ]
                    ]
                ]
            ]
        ]

        def testResult3 = [
            testsuites: [
                [
                    testcases: [
                        [ c: 3 ]
                    ]
                ]
            ]
        ]

        when:
        def result = usecase.combineTestResults([ testResult1, testResult2, testResult3 ])

        then:
        result == [
            testsuites: [
                [ 
                    testcases: [
                        [ a: 1 ]
                    ]
                ],
                [
                    testcases: [
                        [ b: 2 ]
                    ]
                ],
                [
                    testcases: [
                        [ c: 3 ]
                    ]
                ]
            ]
        ]
    }

    def "get number of test cases"() {
        given:
        def testResults = [
            testsuites: [
                [
                    testcases: [
                        [ a: 1 ], [ b: 2 ], [ c: 3 ]
                    ]
                ]
            ]
        ]

        when:
        def result = usecase.getNumberOfTestCases(testResults)

        then:
        result == 3
    }

    def "load test reports from path"() {
        given:
        def xmlFiles = Files.createTempDirectory("junit-test-reports-")
        def xmlFile1 = Files.createTempFile(xmlFiles, "junit", ".xml") << "JUnit XML Report 1"
        def xmlFile2 = Files.createTempFile(xmlFiles, "junit", ".xml") << "JUnit XML Report 2"

        when:
        def result = usecase.loadTestReportsFromPath(xmlFiles.toString())

        then:
        result.size() == 2
        result.collect { it.text }.sort() == ["JUnit XML Report 1", "JUnit XML Report 2"]

        cleanup:
        xmlFiles.toFile().deleteDir()
    }

    def "load test reports from path with empty path"() {
        given:
        def xmlFiles = Files.createTempDirectory("junit-test-reports-")

        when:
        def result = usecase.loadTestReportsFromPath(xmlFiles.toString())

        then:
        result.isEmpty()

        cleanup:
        xmlFiles.toFile().deleteDir()
    }

    def "parse test report files"() {
        given:
        def xmlFiles = Files.createTempDirectory("junit-test-reports-")
        def xmlFile = Files.createTempFile(xmlFiles, "junit", ".xml").toFile()
        xmlFile << "<?xml version='1.0' ?>\n" + createJUnitXMLTestResults()

        when:
        def result = usecase.parseTestReportFiles([xmlFile])

        then:
        def expected = [
            testsuites: JUnitParser.parseJUnitXML(xmlFile.text).testsuites
        ] 

        result == expected

        cleanup:
        xmlFiles.deleteDir()
    }

    def "report test reports from path to Jenkins"() {
        given:
        def path = "myPath"

        when:
        usecase.reportTestReportsFromPathToJenkins(path)

        then:
        1 * steps.junit("${path}/**/*.xml")
    }
}
