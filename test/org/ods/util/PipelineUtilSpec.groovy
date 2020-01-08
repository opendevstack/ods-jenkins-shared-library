package org.ods.util

import java.nio.file.Files
import java.nio.file.Paths

import org.apache.pdfbox.pdmodel.PDDocument

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class PipelineUtilSpec extends SpecHelper {

    def "archive artifact"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = new PipelineUtil(steps)

        when:
        def path = "${steps.env.WORKSPACE}/myPath"
        def data = "data".bytes
        util.archiveArtifact(path, data)

        then:
        1 * steps.archiveArtifacts("myPath")
    }

    def "archive artifact with invalid path"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = new PipelineUtil(steps)

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
        given:
        def steps = Spy(util.PipelineSteps)
        def util = new PipelineUtil(steps)

        when:
        def path = Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString(), "a", "b", "c")
        util.createDirectory(path.toString())

        then:
        path.toFile().exists()

        cleanup:
        path.deleteDir()
    }

    def "create directory with invalid path"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = new PipelineUtil(steps)

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
        def steps = Spy(util.PipelineSteps)
        def util = Spy(new PipelineUtil(steps))

        def logFile1 = Files.createTempFile("log-", ".log").toFile() << "Log File 1"
        def logFile2 = Files.createTempFile("log-", ".log").toFile() << "Log File 2"

        GroovySpy(PipelineUtil, global: true)

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
        given:
        def steps = Spy(util.PipelineSteps)
        def util = new PipelineUtil(steps)

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
        given:
        def steps = Spy(util.PipelineSteps)
        def util = new PipelineUtil(steps)

        when:
        util.createZipArtifact("myZipArtifact", null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Zip artifact. 'files' is undefined."
    }

    def "create Zip file"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = new PipelineUtil(steps)

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
        given:
        def steps = Spy(util.PipelineSteps)
        def util = new PipelineUtil(steps)

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
        given:
        def steps = Spy(util.PipelineSteps)
        def util = new PipelineUtil(steps)

        when:
        util.createZipFile("my.zip", null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to create Zip file. 'files' is undefined."
    }

    def "get Git URL"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = new PipelineUtil(steps)

        def path = "${steps.env.WORKSPACE}/a/b/c"
        def origin = "upstream"

        when:
        def result = util.getGitURL(path, origin)

        then:
        1 * steps.dir(path, _)

        then:
        1 * steps.sh({ it.script == "git config --get remote.${origin}.url" && it.returnStdout }) >> new URI("https://github.com/my-org/my-repo.git").toString()

        then:
        result == new URI("https://github.com/my-org/my-repo.git")
    }

    def "get Git URL with default arguments"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = new PipelineUtil(steps)

        when:
        def result = util.getGitURL()

        then:
        1 * steps.dir(steps.env.WORKSPACE, _)

        then:
        1 * steps.sh({ it.script == "git config --get remote.origin.url" && it.returnStdout }) >> new URI("https://github.com/my-org/my-repo.git").toString()

        then:
        result == new URI("https://github.com/my-org/my-repo.git")
    }

    def "get Git URL with invalid path"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = new PipelineUtil(steps)

        when:
        util.getGitURL(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Git URL. 'path' is undefined."

        when:
        util.getGitURL("")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Git URL. 'path' is undefined."

        when:
        def path = "myPath"
        util.getGitURL(path)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Git URL. 'path' must be inside the Jenkins workspace: ${path}"
    }

    def "get Git URL with invalid remote"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = new PipelineUtil(steps)

        when:
        util.getGitURL(steps.env.WORKSPACE, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Git URL. 'remote' is undefined."

        when:
        util.getGitURL(steps.env.WORKSPACE, "")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Git URL. 'remote' is undefined."
    }

    def "load Groovy source file"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = new PipelineUtil(steps)

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
        given:
        def steps = Spy(util.PipelineSteps)
        def util = new PipelineUtil(steps)

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
