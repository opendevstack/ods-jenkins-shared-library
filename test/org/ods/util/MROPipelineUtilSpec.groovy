package org.ods.util

import java.nio.file.Files
import java.nio.file.Paths

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class MROPipelineUtilSpec extends SpecHelper {

    MROPipelineUtil createUtil(IPipelineSteps steps) {
        return new MROPipelineUtil(steps)
    }

    def "get environment for DEBUG"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        when:
        def result = util.getEnvironment()

        then:
        result.find { it == "DEBUG=false" }

        when:
        result = util.getEnvironment(true)

        then:
        result.find { it == "DEBUG=true" }

        when:
        result = util.getEnvironment(false)

        then:
        result.find { it == "DEBUG=false" }
    }

    def "get environment for MULTI_REPO_BUILD"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        when:
        def result = util.getEnvironment()

        then:
        result.find { it == "MULTI_REPO_BUILD=true" }
    }

    def "get environment for MULTI_REPO_ENV"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        when:
        steps.env.environment = null
        def result = util.getEnvironment()

        then:
        result.find { it == "MULTI_REPO_ENV=dev" }

        when:
        steps.env.environment = ""
        result = util.getEnvironment()

        then:
        result.find { it == "MULTI_REPO_ENV=dev" }

        when:
        steps.env.environment = "myEnv"
        result = util.getEnvironment()

        then:
        result.find { it == "MULTI_REPO_ENV=myEnv" }
    }

    def "get environment for RELEASE_PARAM_CHANGE_ID"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        when:
        steps.env.changeId = null
        steps.env.environment = "myEnv"
        steps.env.version = "0.1"
        def result = util.getEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_ID=0.1-myEnv" }

        when:
        steps.env.changeId = ""
        steps.env.environment = "myEnv"
        steps.env.version = "0.1"
        result = util.getEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_ID=0.1-myEnv" }

        when:
        steps.env.changeId = "myId"
        steps.env.environment = "myEnv"
        steps.env.version = "0.1"
        result = util.getEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_ID=myId" }
    }

    def "get environment for RELEASE_PARAM_CHANGE_DESC"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        when:
        steps.env.changeDescription = null
        def result = util.getEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_DESC=UNDEFINED" }

        when:
        steps.env.changeDescription = ""
        result = util.getEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_DESC=UNDEFINED" }

        when:
        steps.env.changeDescription = "myDescription"
        result = util.getEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_DESC=myDescription" }
    }

    def "get environment for RELEASE_PARAM_CONFIG_ITEM"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        when:
        steps.env.configItem = null
        def result = util.getEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CONFIG_ITEM=UNDEFINED" }

        when:
        steps.env.configItem = ""
        result = util.getEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CONFIG_ITEM=UNDEFINED" }

        when:
        steps.env.configItem = "myItem"
        result = util.getEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_CONFIG_ITEM=myItem" }
    }

    def "get environment for RELEASE_PARAM_VERSION"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        when:
        steps.env.version = null
        def result = util.getEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_VERSION=WIP" }

        when:
        steps.env.version = ""
        result = util.getEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_VERSION=WIP" }

        when:
        steps.env.version = "0.1"
        result = util.getEnvironment()

        then:
        result.find { it == "RELEASE_PARAM_VERSION=0.1" }
    }

    def "get environment for SOURCE_CLONE_ENV"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        when:
        steps.env.environment = "myEnv"
        steps.env.sourceEnvironmentToClone = null
        def result = util.getEnvironment()

        then:
        result.find { it == "SOURCE_CLONE_ENV=myEnv" }

        when:
        steps.env.environment = "myEnv"
        steps.env.sourceEnvironmentToClone = ""
        result = util.getEnvironment()

        then:
        result.find { it == "SOURCE_CLONE_ENV=myEnv" }

        when:
        steps.env.environment = "mvEnv"
        steps.env.sourceEnvironmentToClone = "mySourceEnv"
        result = util.getEnvironment()

        then:
        result.find { it == "SOURCE_CLONE_ENV=mySourceEnv" }
    }

    def "load a repo's pipeline config"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        def repoPath = Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "A").toString()
        def repoDir = util.createDirectory(repoPath)
        def repos = createProject().repositories

        def file = Paths.get(repoPath, MROPipelineUtil.PipelineConfig.FILE_NAME)

        when:
        file << """
        dependencies:
          - B
        
        phases:
          build:
            type: Makefile
            target: build
          test:
            type: ShellScript
            script: test.sh
        """

        def result = util.loadPipelineConfig(repoPath, repos[0].clone())

        then:
        def expected = repos[0] << [
            pipelineConfig: [
                dependencies: ["B"],
                phases: [
                    build: [
                        type: "Makefile",
                        target: "build"
                    ],
                    test: [
                        type: "ShellScript",
                        script: "test.sh"
                    ]
                ]
            ]
        ]

        result == expected

        cleanup:
        repoDir.deleteDir()
    }

    def "load a repo's pipeline config with invalid path"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        def repos = createProject().repositories

        when:
        util.loadPipelineConfig(null, repos[0])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline config. 'path' is undefined"

        when:
        util.loadPipelineConfig("", repos[0])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline config. 'path' is undefined"

        when:
        def path = "myPath"
        util.loadPipelineConfig(path, repos[0])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline config. 'path' must be inside the Jenkins workspace: ${path}"
    }

    def "load a repo's pipeline config with invalid repo"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        def repoPath = Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "A").toString()
        def repoDir = util.createDirectory(repoPath)
        def repos = createProject().repositories

        def file = Paths.get(repoPath, MROPipelineUtil.PipelineConfig.FILE_NAME)

        when:
        file << """
        phases:
          build:
        """

        util.loadPipelineConfig(repoPath, repos[0])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline phase config. Required attribute 'phase.type' is undefined in phase 'build'."

        when:
        file.text = """
        phases:
          build:
            type: someType
        """

        util.loadPipelineConfig(repoPath, repos[0])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline phase config. Attribute 'phase.type' contains an unsupported value 'someType' in phase 'build'. Supported types are: ${MROPipelineUtil.PipelineConfig.PHASE_EXECUTOR_TYPES}."

        cleanup:
        repoDir.deleteDir()
    }

    def "load a repo's pipeline config with invalid phase type"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        when:
        util.loadPipelineConfig(steps.env.WORKSPACE, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline config. 'repo' is undefined"

        when:
        util.loadPipelineConfig(steps.env.WORKSPACE, [:])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline config. 'repo' is undefined"
    }

    def "load a repo's pipeline config with invalid target for phase type Makefile"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        def repoPath = Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "A").toString()
        def repoDir = util.createDirectory(repoPath)
        def repos = createProject().repositories

        def file = Paths.get(repoPath, MROPipelineUtil.PipelineConfig.FILE_NAME)

        when:
        file << """
        phases:
          build:
            type: Makefile
        """

        util.loadPipelineConfig(repoPath, repos[0])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline phase config. Required attribute 'phase.target' is undefined in phase 'build'."

        when:
        file.text = """
        phases:
          build:
            type: Makefile
            target: 
        """

        util.loadPipelineConfig(repoPath, repos[0])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline phase config. Required attribute 'phase.target' is undefined in phase 'build'."

        cleanup:
        repoDir.deleteDir()
    }

    def "load a repo's pipeline config with invalid target for phase type ShellScript"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        def repoPath = Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "A").toString()
        def repoDir = util.createDirectory(repoPath)
        def repos = createProject().repositories

        def file = Paths.get(repoPath, MROPipelineUtil.PipelineConfig.FILE_NAME)

        when:
        file << """
        phases:
          build:
            type: ShellScript
        """

        util.loadPipelineConfig(repoPath, repos[0])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline phase config. Required attribute 'phase.script' is undefined in phase 'build'."

        when:
        file.text = """
        phases:
          build:
            type: ShellScript
            script: 
        """

        util.loadPipelineConfig(repoPath, repos[0])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline phase config. Required attribute 'phase.script' is undefined in phase 'build'."

        cleanup:
        repoDir.deleteDir()
    }

    def "load multiple repos' pipeline configs"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        def repoPathA = Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "A").toString()
        def repoDirA = util.createDirectory(repoPathA)
        def fileA = Paths.get(repoPathA, MROPipelineUtil.PipelineConfig.FILE_NAME)

        def repoPathB = Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "B").toString()
        def repoDirB = util.createDirectory(repoPathB)
        def fileB = Paths.get(repoPathB, MROPipelineUtil.PipelineConfig.FILE_NAME)

        def repoPathC = Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "C").toString()
        def repoDirC = util.createDirectory(repoPathC)
        def fileC = Paths.get(repoPathC, MROPipelineUtil.PipelineConfig.FILE_NAME)

        def repos = createProject().repositories

        when:
        fileA << ""

        fileB << """
        dependencies:
          - A
        
        phases:
          build:
            type: Makefile
            target: build
        """

        fileC << """
        dependencies:
          - B
        
        phases:
          test:
            type: ShellScript
            script: test.sh
        """

        def result = util.loadPipelineConfigs(repos.clone())

        then:
        def expected = [
            repos[0] << [
                pipelineConfig: []
            ],
            repos[1] << [
                pipelineConfig: [
                    dependencies: ["A"],
                    phases: [
                        build: [
                            type: "Makefile",
                            target: "build"
                        ]
                    ]
                ]
            ],
            repos[2] << [
                pipelineConfig: [
                    dependencies: ["B"],
                    phases: [
                        test: [
                            type: "ShellScript",
                            script: "test.sh"
                        ]
                    ]
                ]
            ]
        ]

        result == expected

        cleanup:
        repoDirA.deleteDir()
        repoDirB.deleteDir()
        repoDirC.deleteDir()
    }

    def "load project metadata"() {
        given:
        def steps = Spy(util.PipelineSteps)
        steps.sh(_) >> "https://github.com/my-org/my-pipeline-repo.git"
        def util = createUtil(steps)

        def file = Files.createTempFile(Paths.get(steps.env.WORKSPACE), "metadata-", ".yml").toFile()

        when:
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
        def result = util.loadProjectMetadata(file.name)

        then:
        def expected = [
            id: "myId",
            name: "myName",
            description: "myDescription",
            repositories: [
                [
                    id: "A",
                    url: "https://github.com/my-org/my-repo-A.git",
                    branch: "master"
                ],
                [
                    id: "B",
                    url: "https://github.com/my-org/my-repo-B.git",
                    branch: "master"
                ],
                [
                    id: "C",
                    url: "https://github.com/my-org/myid-C.git",
                    branch: "master"
                ]
            ]
        ]

        result == expected

        cleanup:
        file.delete()
    }

    def "load project metadata with undefined file"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        when:
        util.loadProjectMetadata(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. 'filename' is undefined."
    }

    def "load project metadata with non-existent file"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        when:
        def filename = "myFile"
        util.loadProjectMetadata(filename)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to load project meta data. File '${steps.env.WORKSPACE}/${filename}' does not exist."
    }

    def "load project metadata with undefined id"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        def file = Files.createTempFile(Paths.get(steps.env.WORKSPACE), "metadata-", ".yml").toFile()

        when:
        file << """
            name: myName
        """
        util.loadProjectMetadata(file.name)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'id' is undefined."

        cleanup:
        file.delete()
    }

    def "load project metadata with undefined name"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        def file = Files.createTempFile(Paths.get(steps.env.WORKSPACE), "metadata-", ".yml").toFile()

        when:
        file << """
            id: myId
        """
        util.loadProjectMetadata(file.name)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'name' is undefined."

        cleanup:
        file.delete()
    }
    
    def "load project metadata with undefined description"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        def file = Files.createTempFile(Paths.get(steps.env.WORKSPACE), "metadata-", ".yml").toFile()

        when:
        file << """
            id: myId
            name: myName
            repositories:
              - id: A
        """
        def result = util.loadProjectMetadata(file.name)

        then:
        result.description == ""

        cleanup:
        file.delete()
    }
    
    def "load project metadata with undefined repositories"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        def file = Files.createTempFile(Paths.get(steps.env.WORKSPACE), "metadata-", ".yml").toFile()

        when:
        file << """
            id: myId
            name: myName
        """
        util.loadProjectMetadata(file.name)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'repositories' is undefined."

        cleanup:
        file.delete()
    }

    def "load project metadata with undefined repository id"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        def file = Files.createTempFile(Paths.get(steps.env.WORKSPACE), "metadata-", ".yml").toFile()

        when:
        file << """
            id: myId
            name: myName
            repositories:
              - name: A
        """
        util.loadProjectMetadata(file.name)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'repositories[0].id' is undefined."

        when:
        file.text = """
            id: myId
            name: myName
            repositories:
              - id: A
              - name: B
        """
        util.loadProjectMetadata(file.name)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'repositories[1].id' is undefined."

        cleanup:
        file.delete()
    }

    def "load project metadata with undefined repository url"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        def file = Files.createTempFile(Paths.get(steps.env.WORKSPACE), "metadata-", ".yml").toFile()

        when:
        file << """
            id: myId
            name: myName
            repositories:
              - name: A
        """
        util.loadProjectMetadata(file.name)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'repositories[0].id' is undefined."

        when:
        file.text = """
            id: myId
            name: myName
            repositories:
              - id: A
              - name: B
        """
        util.loadProjectMetadata(file.name)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'repositories[1].id' is undefined."

        cleanup:
        file.delete()
    }

    def "walk repo directories"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def util = createUtil(steps)

        def repoDirA = util.createDirectory(Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "A").toString())
        def repoDirB = util.createDirectory(Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "B").toString())
        def repoDirC = util.createDirectory(Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "C").toString())

        def repos = createProject().repositories
        def visitor = Mock(Closure)

        when:
        util.walkRepoDirectories(repos, visitor)

        then:
        1 * visitor("${steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/A", repos[0])
        1 * visitor("${steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/B", repos[1])
        1 * visitor("${steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/C", repos[2])

        cleanup:
        repoDirA.deleteDir()
        repoDirB.deleteDir()
        repoDirC.deleteDir()
    }
}
