package org.ods.util

import java.nio.file.Paths

import org.apache.http.client.utils.URIBuilder

import spock.lang.*

import util.*

class ProjectSpec extends SpecHelper {

    GitUtil git
    File metadataFile
    Project project
    IPipelineSteps steps

    def setup() {
        steps = Spy(util.PipelineSteps)
        git = Mock(GitUtil)
        metadataFile = createProjectMetadataFile(this.steps.env.WORKSPACE, steps)
        project = new Project(this.steps, this.git)
    }

    def cleanup() {
        metadataFile.delete()
    }

    File createProjectMetadataFile(String path, IPipelineSteps steps) {
        def file = Paths.get(path, "metadata.yml").toFile()

        file << """
            id: myId
            name: myName
            description: myDescription
            repositories:
              - id: A
                url: https://github.com/my-org/my-repo-A.git
                branch: master
              - id: B
                name: my-repo-B
                branch: master
              - id: C
        """

        return file
    }

    def "get build environment for DEBUG"() {
        when:
        def result = project.getBuildEnvironment()

        then:
        result.find { it == "DEBUG=false" }

        when:
        result = project.getBuildEnvironment(true)

        then:
        result.find { it == "DEBUG=true" }

        when:
        result = project.getBuildEnvironment(false)

        then:
        result.find { it == "DEBUG=false" }
    }

    def "get build environment for MULTI_REPO_BUILD"() {
        when:
        def result = project.getBuildEnvironment()

        then:
        result.find { it == "MULTI_REPO_BUILD=true" }
    }

    def "get build environment for MULTI_REPO_ENV"() {
        when:
        steps.env.environment = null
        def result = project.getBuildEnvironment()

        then:
        result.find { it == "MULTI_REPO_ENV=dev" }

        when:
        steps.env.environment = ""
        result = project.getBuildEnvironment()

        then:
        result.find { it == "MULTI_REPO_ENV=dev" }

        when:
        steps.env.environment = "myEnv"
        result = project.getBuildEnvironment()

        then:
        result.find { it == "MULTI_REPO_ENV=myEnv" }
    }

    def "get build environment for MULTI_REPO_ENV_TOKEN"() {
        when:
        steps.env.environment = "dev"
        def result = project.getBuildEnvironment()

        then:
        result.find { it == "MULTI_REPO_ENV_TOKEN=D" }

        when:
        steps.env.environment = "qa"
        result = project.getBuildEnvironment()

        then:
        result.find { it == "MULTI_REPO_ENV_TOKEN=Q" }

        when:
        steps.env.environment = "prod"
        result = project.getBuildEnvironment()

        then:
        result.find { it == "MULTI_REPO_ENV_TOKEN=P" }
    }

    def "get build environment for RELEASE_PARAM_CHANGE_ID"() {
        when:
        steps.env.changeId = null
        steps.env.environment = "myEnv"
        steps.env.version = "0.1"
        def result = project.getBuildEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_ID=0.1-myEnv" }

        when:
        steps.env.changeId = ""
        steps.env.environment = "myEnv"
        steps.env.version = "0.1"
        result = project.getBuildEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_ID=0.1-myEnv" }

        when:
        steps.env.changeId = "myId"
        steps.env.environment = "myEnv"
        steps.env.version = "0.1"
        result = project.getBuildEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_ID=myId" }
    }

    def "get build environment for RELEASE_PARAM_CHANGE_DESC"() {
        when:
        steps.env.changeDescription = null
        def result = project.getBuildEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_DESC=UNDEFINED" }

        when:
        steps.env.changeDescription = ""
        result = project.getBuildEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_DESC=UNDEFINED" }

        when:
        steps.env.changeDescription = "myDescription"
        result = project.getBuildEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_DESC=myDescription" }
    }

    def "get build environment for RELEASE_PARAM_CONFIG_ITEM"() {
        when:
        steps.env.configItem = null
        def result = project.getBuildEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CONFIG_ITEM=UNDEFINED" }

        when:
        steps.env.configItem = ""
        result = project.getBuildEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CONFIG_ITEM=UNDEFINED" }

        when:
        steps.env.configItem = "myItem"
        result = project.getBuildEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CONFIG_ITEM=myItem" }
    }

    def "get build environment for RELEASE_PARAM_VERSION"() {
        when:
        steps.env.version = null
        def result = project.getBuildEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_VERSION=WIP" }

        when:
        steps.env.version = ""
        result = project.getBuildEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_VERSION=WIP" }

        when:
        steps.env.version = "0.1"
        result = project.getBuildEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_VERSION=0.1" }
    }

    def "get build environment for SOURCE_CLONE_ENV"() {
        when:
        steps.env.environment = "myEnv"
        steps.env.sourceEnvironmentToClone = null
        def result = project.getBuildEnvironment()

        then:
        result.find { it == "SOURCE_CLONE_ENV=myEnv" }

        when:
        steps.env.environment = "myEnv"
        steps.env.sourceEnvironmentToClone = ""
        result = project.getBuildEnvironment()

        then:
        result.find { it == "SOURCE_CLONE_ENV=myEnv" }

        when:
        steps.env.environment = "mvEnv"
        steps.env.sourceEnvironmentToClone = "mySourceEnv"
        result = project.getBuildEnvironment()

        then:
        result.find { it == "SOURCE_CLONE_ENV=mySourceEnv" }
    }

    def "get build environment for SOURCE_CLONE_ENV_TOKEN"() {
        when:
        steps.env.sourceEnvironmentToClone = "dev"
        def result = project.getBuildEnvironment()

        then:
        result.find { it == "SOURCE_CLONE_ENV_TOKEN=D" }

        when:
        steps.env.sourceEnvironmentToClone = "qa"
        result = project.getBuildEnvironment()

        then:
        result.find { it == "SOURCE_CLONE_ENV_TOKEN=Q" }

        when:
        steps.env.sourceEnvironmentToClone = "prod"
        result = project.getBuildEnvironment()

        then:
        result.find { it == "SOURCE_CLONE_ENV_TOKEN=P" }
    }

    def "get Git URL from path"() {
        given:
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

        cleanup:
        metadataFile.delete()
    }

    def "get Git URL from path without origin"() {
        given:
        def path = "${steps.env.WORKSPACE}/a/b/c"

        when:
        def result = project.getGitURLFromPath(path)

        then:
        1 * steps.dir(path, _)

        then:
        1 * steps.sh({ it.script == "git config --get remote.origin.url" && it.returnStdout }) >> new URI("https://github.com/my-org/my-repo.git").toString()

        then:
        result == new URI("https://github.com/my-org/my-repo.git")

        cleanup:
        metadataFile.delete()
    }

    def "get Git URL from path with invalid path"() {
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
        def path = "myPath"
        project.getGitURLFromPath(path)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Git URL. 'path' must be inside the Jenkins workspace: ${path}"

        cleanup:
        metadataFile.delete()
    }

    def "get Git URL from path with invalid remote"() {
        given:
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

        cleanup:
        metadataFile.delete()
    }

    def "load build param changeDescription"() {
        when:
        steps.env.changeDescription = null
        def result = project.loadBuildParams()

        then:
        result.changeDescription == "UNDEFINED"

        when:
        steps.env.changeDescription = ""
        result = project.loadBuildParams()

        then:
        result.changeDescription == "UNDEFINED"

        when:
        steps.env.changeDescription = "myDescription"
        result = project.loadBuildParams()

        then:
        result.changeDescription == "myDescription"
    }

    def "load build param changeId"() {
        when:
        steps.env.changeId = null
        steps.env.environment = "myEnv"
        steps.env.version = "0.1"
        def result = project.loadBuildParams()

        then:
        result.changeId == "0.1-myEnv"

        when:
        steps.env.changeId = ""
        steps.env.environment = "myEnv"
        steps.env.version = "0.1"
        result = project.loadBuildParams()

        then:
        result.changeId == "0.1-myEnv"

        when:
        steps.env.changeId = "myId"
        steps.env.environment = "myEnv"
        steps.env.version = "0.1"
        result = project.loadBuildParams()

        then:
        result.changeId == "myId"
    }

    def "load build param configItem"() {
        when:
        steps.env.configItem = null
        def result = project.loadBuildParams()

        then:
        result.configItem == "UNDEFINED"

        when:
        steps.env.configItem = ""
        result = project.loadBuildParams()

        then:
        result.configItem == "UNDEFINED"

        when:
        steps.env.configItem = "myItem"
        result = project.loadBuildParams()

        then:
        result.configItem == "myItem"
    }

    def "load build param sourceEnvironmentToClone"() {
        when:
        steps.env.environment = "myEnv"
        steps.env.sourceEnvironmentToClone = null
        def result = project.loadBuildParams()

        then:
        result.sourceEnvironmentToClone == "myEnv"

        when:
        steps.env.environment = "myEnv"
        steps.env.sourceEnvironmentToClone = ""
        result = project.loadBuildParams()

        then:
        result.sourceEnvironmentToClone == "myEnv"

        when:
        steps.env.environment = "mvEnv"
        steps.env.sourceEnvironmentToClone = "mySourceEnv"
        result = project.loadBuildParams()

        then:
        result.sourceEnvironmentToClone == "mySourceEnv"
    }

    def "load build param sourceEnvironmentToCloneToken"() {
        when:
        steps.env.sourceEnvironmentToClone = "dev"
        def result = project.loadBuildParams()

        then:
        result.sourceEnvironmentToCloneToken == "D"

        when:
        steps.env.sourceEnvironmentToClone = "qa"
        result = project.loadBuildParams()

        then:
        result.sourceEnvironmentToCloneToken == "Q"

        when:
        steps.env.sourceEnvironmentToClone = "prod"
        result = project.loadBuildParams()

        then:
        result.sourceEnvironmentToCloneToken == "P"
    }

    def "load build param targetEnvironment"() {
        when:
        steps.env.environment = null
        def result = project.loadBuildParams()

        then:
        result.targetEnvironment == "dev"

        when:
        steps.env.environment = ""
        result = project.loadBuildParams()

        then:
        result.targetEnvironment == "dev"

        when:
        steps.env.environment = "myEnv"
        result = project.loadBuildParams()

        then:
        result.targetEnvironment == "myEnv"
    }

    def "load build param targetEnvironmentToken"() {
        when:
        steps.env.environment = "dev"
        def result = project.loadBuildParams()

        then:
        result.targetEnvironmentToken == "D"

        when:
        steps.env.environment = "qa"
        result = project.loadBuildParams()

        then:
        result.targetEnvironmentToken == "Q"

        when:
        steps.env.environment = "prod"
        result = project.loadBuildParams()

        then:
        result.targetEnvironmentToken == "P"
    }

    def "load build param version"() {
        when:
        steps.env.version = null
        def result = project.loadBuildParams()

        then:
        result.version == "WIP"

        when:
        steps.env.version = ""
        result = project.loadBuildParams()

        then:
        result.version == "WIP"

        when:
        steps.env.version = "0.1"
        result = project.loadBuildParams()

        then:
        result.version == "0.1"
    }

    def "load metadata"() {
        given:
        steps.sh(_) >> "https://github.com/my-org/my-pipeline-repo.git"

        when:
        def result = project.loadMetadata()

        then:
        def expected = [
            id: "myId",
            name: "myName",
            description: "myDescription",
            repositories: [
                [
                    id: "A",
                    url: "https://github.com/my-org/my-repo-A.git",
                    branch: "master",
                    type: MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE,
                    data: [
                        documents: [:]
                    ]
                ],
                [
                    id: "B",
                    url: "https://github.com/my-org/my-repo-B.git",
                    branch: "master",
                    type: MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE,
                    data: [
                        documents: [:]
                    ]
                ],
                [
                    id: "C",
                    url: "https://github.com/my-org/myid-C.git",
                    branch: "master",
                    type: MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE,
                    data: [
                        documents: [:]
                    ]
                ]
            ],
            capabilities: []
        ]

        result == expected
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
        when:
        metadataFile.text = """
            name: myName
        """

        project.loadMetadata()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'id' is undefined."
    }

    def "load project metadata with invalid name"() {
        when:
        metadataFile.text = """
            id: myId
        """

        project.loadMetadata()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'name' is undefined."
    }
    
    def "load project metadata with invalid description"() {
        when:
        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
        """

        def result = project.loadMetadata()

        then:
        result.description == ""
    }
    
    def "load project metadata with undefined repositories"() {
        when:
        metadataFile.text = """
            id: myId
            name: myName
        """

        def result = project.loadMetadata()

        then:
        result.repositories == []
    }

    def "load project metadata with invalid repository id"() {
        when:
        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - name: A
        """

        project.loadMetadata()

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

        project.loadMetadata()

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'repositories[1].id' is undefined."
    }

    def "load project metadata with invalid repository url"() {
        when:
        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - name: A
        """

        project.loadMetadata()

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

        project.loadMetadata()

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'repositories[1].id' is undefined."
    }
}
