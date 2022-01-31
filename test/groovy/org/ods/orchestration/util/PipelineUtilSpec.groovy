package org.ods.orchestration.util

import org.junit.Rule
import org.junit.rules.TemporaryFolder

import java.nio.file.Files
import java.nio.file.Paths

import org.apache.pdfbox.pdmodel.PDDocument
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import org.ods.orchestration.util.Project
import org.ods.services.GitService

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class PipelineUtilSpec extends SpecHelper {

    @Rule
    public TemporaryFolder tempFolder

    Project project
    IPipelineSteps steps
    PipelineUtil util
    Logger logger

    def setup() {
        project = createProject()
        steps = Spy(util.PipelineSteps)
        def git = Mock(GitService)
        logger = Mock(Logger)
        util = Spy(new PipelineUtil(project, steps, git, logger))
    }

    def "archive artifact"() {
        given:
        steps.env.WORKSPACE = tempFolder.getRoot().absolutePath
        def path = "${steps.env.WORKSPACE}/myPath"
        def data = "data".bytes

        when:
        util.archiveArtifact(path, data)

        then:
        1 * steps.archiveArtifacts("myPath")
    }

    def "archive artifact with invalid path"() {
        when:
        util.archiveArtifact(null, new byte[0])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to archive artifact. 'path' is undefined."

        when:
        util.archiveArtifact("", new byte[0])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to archive artifact. 'path' is undefined."

        when:
        def path = "myPath"
        def data = "data".bytes
        util.archiveArtifact(path, data)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to archive artifact. 'path' must be inside the Jenkins workspace: ${path}"
    }

    def "create directory"() {
        when:
        def path = Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString(), "a", "b", "c")
        util.createDirectory(path.toString())

        then:
        path.toFile().exists()

        cleanup:
        path.deleteDir()
    }

    def "create directory with invalid path"() {
        when:
        util.createDirectory(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create directory. 'path' is undefined."

        when:
        util.createDirectory("")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create directory. 'path' is undefined."
    }

    def "create Zip artifact"() {
        given:
        def logFile1 = Files.createTempFile("log-", ".log").toFile() << "Log File 1"
        def logFile2 = Files.createTempFile("log-", ".log").toFile() << "Log File 2"

        when:
        def name = "myZipArtifact"
        def files = ["logFile1": logFile1.bytes, "logFile2": logFile2.bytes]
        def result = util.createZipArtifact(name, files)

        then:
        1 * util.createZipFile(*_)

        then:
        1 * util.archiveArtifact(*_)

        then:
        result.size() != 0

        cleanup:
        logFile1.delete()
        logFile2.delete()
    }

    def "create Zip artifact with invalid name"() {
        when:
        util.createZipArtifact(null, [:])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Zip artifact. 'name' is undefined."

        when:
        util.createZipArtifact("", [:])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Zip artifact. 'name' is undefined."
    }

    def "create Zip artifact with invalid files"() {
        when:
        util.createZipArtifact("myZipArtifact", null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Zip artifact. 'files' is undefined."
    }

    def "create Zip file"() {
        given:
        def logFile1 = Files.createTempFile("log-", ".log").toFile() << "Log File 1"
        def logFile2 = Files.createTempFile("log-", ".log").toFile() << "Log File 2"

        when:
        def path = "${steps.env.WORKSPACE}/my.zip"
        def files = ["logFile1": logFile1.bytes, "logFile2": logFile2.bytes]
        def result = util.createZipFile(path, files)

        then:
        result.size() != 0

        cleanup:
        logFile1.delete()
        logFile2.delete()
        Paths.get(path).toFile().delete()
    }

    def "create Zip file with invalid name"() {
        when:
        util.createZipFile(null, [:])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Zip file. 'path' is undefined."

        when:
        util.createZipFile("", [:])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Zip file. 'path' is undefined."
    }

    def "create Zip file with invalid files"() {
        when:
        util.createZipFile("my.zip", null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Zip file. 'files' is undefined."
    }

    def "execute block and fail build"() {
        given:
        def block = { throw new RuntimeException("some error") }

        when:
        util.executeBlockAndFailBuild(block)

        then:
        1 * util.failBuild("some error")

        then:
        def e = thrown(RuntimeException)
        e.message == "some error"
    }

    def "failBuild"() {
        when:
        util.failBuild("some error")

        then:
        steps.currentBuild.result == "FAILURE"
        1 * logger.warn("some error")
    }

    def "warnBuild"() {
        when:
        util.warnBuild("some warning")

        then:
        steps.currentBuild.result == "UNSTABLE"
        1 * logger.warn("some warning")
    }

    def "load Groovy source file"() {
        given:
        def groovyFile = Files.createTempFile(Paths.get(steps.env.WORKSPACE), "groovy-", ".groovy").toFile() << "Groovy Script"

        when:
        def path = groovyFile.toString()
        util.loadGroovySourceFile(path)

        then:
        1 * steps.load({ path })

        cleanup:
        groovyFile.delete()
    }

    def "load Groovy source file with invalid path"() {
        when:
        util.loadGroovySourceFile(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to load Groovy source file. 'path' is undefined."

        when:
        util.loadGroovySourceFile("")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to load Groovy source file. 'path' is undefined."

        when:
        def path = "${steps.env.WORKSPACE}/my.groovy"
        util.loadGroovySourceFile(path)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to load Groovy source file. Path ${path} does not exist."
    }
}
