package org.ods.orchestration.util

import org.ods.util.IPipelineSteps
import util.*

import java.nio.file.Files

class ProjectSpec {

    IPipelineSteps steps
    Project project

    def setup() {
        steps = Spy(util.PipelineSteps)
        steps.env.WORKSPACE = ""

        project = new FakeProject()
    }

    def "load metadata with invalid file"() {
        when:
        project.loadMetadata(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. 'filename' is undefined."
    }

    def "load project metadata with non-existent file"() {
        when:
        def filename = "non-existent"
        project.loadMetadata(filename)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to load project meta data. File '${steps.env.WORKSPACE}/${filename}' does not exist."
    }

    def "load project metadata with invalid id"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()

        when:
        metadataFile.text = """
            name: myName
        """

        project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'id' is undefined."

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with invalid name"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()

        when:
        metadataFile.text = """
            id: myId
        """

        project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'name' is undefined."

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with invalid description"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()

        when:
        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
        """

        def result = project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        result.description == ""

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with undefined repositories"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()

        when:
        metadataFile.text = """
            id: myId
            name: myName
        """

        def result = project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        result.repositories == []

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with invalid repository id"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()

        when:
        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - name: A
        """

        project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'repositories[0].id' is undefined."

        when:
        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
              - name: B
        """

        project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'repositories[1].id' is undefined."

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with invalid repository url"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()

        when:
        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - name: A
        """

        project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'repositories[0].id' is undefined."

        when:
        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
              - name: B
        """

        project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'repositories[1].id' is undefined."

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with LeVADocs"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()
        project.METADATA_FILE_NAME = metadataFile.getAbsolutePath()

        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
                name: A
                url: http://git.com
            capabilities:
              - LeVADocs:
                  GAMPCategory: 5
                  templatesVersion: "2.0"
        """

        when:
        def result = project.init()

        then:
        result.getCapability("LeVADocs").GAMPCategory == 5
        result.getCapability("LeVADocs").templatesVersion == "2.0"

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with LeVADocs capabilities but without templatesVersion"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()
        project.METADATA_FILE_NAME = metadataFile.getAbsolutePath()

        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
                name: A
                url: http://git.com
            capabilities:
              - LeVADocs:
                  GAMPCategory: 5
        """

        when:
        def result = project.init()

        then:
        result.getCapability("LeVADocs").templatesVersion == "1.0"

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with multiple LeVADocs capabilities"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()
        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
                name: A
                url: http://git.com
            capabilities:
              - LeVADocs:
                  GAMPCategory: 1
              - LeVADocs:
                  GAMPCategory: 3
        """

        when:
        project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project metadata. More than one LeVADoc capability has been defined."

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with LeVADocs capabilities but without GAMPCategory"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()
        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
                name: A
                url: http://git.com
            capabilities:
              - LeVADocs:
        """

        when:
        project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: LeVADocs capability has been defined but contains no GAMPCategory."

        cleanup:
        metadataFile.delete()
    }

    def "get Git URL from path"() {
        given:
        def project = new Project(steps)

        def path = "${steps.env.WORKSPACE}/a/b/c"
        def origin = "upstream"

        when:
        def result = project.getGitURLFromPath(path, origin)

        then:
        1 * steps.dir(path, _)

        then:
        1 * steps.sh({ it.script == "git config --get remote.${origin}.url" && it.returnStdout }) >> new URI("https://github.com/my-org/my-repo.git").toString()

        then:
        result == new URI("https://github.com/my-org/my-repo.git")
    }

    def "get Git URL from path without origin"() {
        given:
        def project = new Project(steps)

        def path = "${steps.env.WORKSPACE}/a/b/c"

        when:
        def result = project.getGitURLFromPath(path)

        then:
        1 * steps.dir(path, _)

        then:
        1 * steps.sh({ it.script == "git config --get remote.origin.url" && it.returnStdout }) >> new URI("https://github.com/my-org/my-repo.git").toString()

        then:
        result == new URI("https://github.com/my-org/my-repo.git")
    }

    def "get Git URL from path with invalid path"() {
        given:
        def project = new Project(steps)

        when:
        project.getGitURLFromPath(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Git URL. 'path' is undefined."

        when:
        project.getGitURLFromPath("")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Git URL. 'path' is undefined."

        when:
        steps.env.WORKSPACE = "myWorkspace"
        def path = "myPath"
        project.getGitURLFromPath(path)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Git URL. 'path' must be inside the Jenkins workspace: ${path}"
    }

    def "get Git URL from path with invalid remote"() {
        given:
        def project = new Project(steps)

        def path = "${steps.env.WORKSPACE}/a/b/c"

        when:
        project.getGitURLFromPath(path, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Git URL. 'remote' is undefined."

        when:
        project.getGitURLFromPath(path, "")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Git URL. 'remote' is undefined."
    }
}
