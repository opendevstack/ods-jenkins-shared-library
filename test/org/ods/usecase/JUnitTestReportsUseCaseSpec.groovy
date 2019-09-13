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
        def tmpDir = Files.createTempDirectory("junit-test-reports-")
        def tmpFile1 = Files.createTempFile(tmpDir, "junit", ".xml") << '<?xml version="1.0" ?>'
        def tmpFile2 = Files.createTempFile(tmpDir, "junit", ".xml") << '<?xml version="2.0" ?>'

        def steps = Spy(PipelineSteps)
        def usecase = createUseCase(steps)

        when:
        def result = usecase.loadTestReportsFromPath(tmpDir.toString())

        then:
        result.size() == 2
        result.collect { it.text }.sort() == ['<?xml version="1.0" ?>', '<?xml version="2.0" ?>']

        cleanup:
        tmpDir.toFile().deleteDir()
    }

    def "load test reports from path with empty path"() {
        given:
        def tmpDir = Files.createTempDirectory("junit-test-reports-")

        def steps = Spy(PipelineSteps)
        def usecase = createUseCase(steps)

        when:
        def result = usecase.loadTestReportsFromPath(tmpDir.toString())

        then:
        result.isEmpty()

        cleanup:
        tmpDir.toFile().deleteDir()
    }

    def "parse test report files"() {
        given:
        def tmpDir = Files.createTempDirectory("junit-test-reports-")
        def tmpFile = Files.createTempFile(tmpDir, "junit", ".xml")
        tmpFile << "<?xml version='1.0' ?>\n" + createJUnitXMLTestResults()

        def steps = Spy(PipelineSteps)
        def usecase = createUseCase(steps)

        when:
        def result = usecase.parseTestReportFiles([tmpFile.toFile()])

        then:
        def expected = [
            testsuites: JUnitParser.parseJUnitXML(tmpFile.text).testsuites
        ] 

        result == expected

        cleanup:
        tmpDir.toFile().deleteDir()
    }

    def "report test reports from path to Jenkins"() {
        given:
        def steps = Spy(PipelineSteps)
        def usecase = createUseCase(steps)
        def path = "myPath"

        when:
        usecase.reportTestReportsFromPathToJenkins(path)

        then:
        1 * steps.junit("${path}/**/*.xml")
    }
}
