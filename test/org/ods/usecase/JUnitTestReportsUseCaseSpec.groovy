package org.ods.usecase

import java.nio.file.Files

import org.ods.parser.JUnitParser

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class JUnitTestReportsUseCaseSpec extends SpecHelper {

    JUnitTestReportsUseCase createUseCase(PipelineSteps steps) {
        return new JUnitTestReportsUseCase(steps)
    }

    def "load test reports from path"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def usecase = createUseCase(steps)

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
        def steps = Spy(util.PipelineSteps)
        def usecase = createUseCase(steps)

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
        def steps = Spy(util.PipelineSteps)
        def usecase = createUseCase(steps)

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
        def steps = Spy(util.PipelineSteps)
        def usecase = createUseCase(steps)

        def path = "myPath"

        when:
        usecase.reportTestReportsFromPathToJenkins(path)

        then:
        1 * steps.junit("${path}/**/*.xml")
    }
}
