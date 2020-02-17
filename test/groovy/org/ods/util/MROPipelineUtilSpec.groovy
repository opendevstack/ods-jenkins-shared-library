package org.ods.util

import java.nio.file.Paths

import org.ods.util.IPipelineSteps
import org.ods.util.Project

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class MROPipelineUtilSpec extends SpecHelper {

    Project project
    IPipelineSteps steps
    MROPipelineUtil util

    def setup() {
        project = createProject()
        steps = Spy(util.PipelineSteps)
        util = new MROPipelineUtil(project, steps)
    }

    def "load a repo's pipeline config"() {
        given:
        def repoPath = Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "A").toString()
        def repoDir = util.createDirectory(repoPath)
        def repos = createProject().repositories

        def componentMetadataFile = Paths.get(repoPath, MROPipelineUtil.COMPONENT_METADATA_FILE_NAME)
        def pipelineConfigFile = Paths.get(repoPath, MROPipelineUtil.PipelineConfig.FILE_NAMES.first())

        when:
        componentMetadataFile << """
        id: myId
        name: myName
        description: myDescription
        supplier: mySupplier
        version: myVersion
        references: myReferences
        """

        pipelineConfigFile << """
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
            metadata: [
                id: "myId",
                name: "myName",
                description: "myDescription",
                supplier: "mySupplier",
                version: "myVersion",
                references: "myReferences"
            ],
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
        def repos = createProject().repositories

        when:
        util.loadPipelineConfig(null, repos[0])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline config. 'path' is undefined."

        when:
        util.loadPipelineConfig("", repos[0])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline config. 'path' is undefined."

        when:
        def path = "myPath"
        util.loadPipelineConfig(path, repos[0])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline config. 'path' must be inside the Jenkins workspace: ${path}"
    }

    def "load a repo's pipeline config with invalid repo"() {
        given:
        def repoPath = Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "A").toString()
        def repoDir = util.createDirectory(repoPath)
        def repos = createProject().repositories

        def componentMetadataFile = Paths.get(repoPath, MROPipelineUtil.PipelineConfig.COMPONENT_METADATA_FILE_NAME)
        def pipelineConfigFile = Paths.get(repoPath, MROPipelineUtil.PipelineConfig.FILE_NAMES.first())

        when:
        pipelineConfigFile << """
        phases:
          build:
        """

        util.loadPipelineConfig(repoPath, repos[0])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline phase config. Required attribute 'phase.type' is undefined in phase 'build'."

        when:
        pipelineConfigFile.text = """
        phases:
          build:
            type: someType
        """

        util.loadPipelineConfig(repoPath, repos[0])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline phase config. Attribute 'phase.type' contains an unsupported value 'someType' in phase 'build'. Supported types are: ${MROPipelineUtil.PipelineConfig.PHASE_EXECUTOR_TYPES}."

        when:
        pipelineConfigFile.text = """
        dependencies: []
        """

        componentMetadataFile << """
        id: myId
        """

        util.loadPipelineConfig(repoPath, repos[0])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse component metadata. Required attribute 'name' is undefined for repository '${repos.first().id}'."

        when:
        componentMetadataFile.text = """
        id: myId
        name: myName
        """

        util.loadPipelineConfig(repoPath, repos[0])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse component metadata. Required attribute 'description' is undefined for repository '${repos.first().id}'."

        when:
        componentMetadataFile.text = """
        id: myId
        name: myName
        description: myDescription
        """

        util.loadPipelineConfig(repoPath, repos[0])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse component metadata. Required attribute 'supplier' is undefined for repository '${repos.first().id}'."

        when:
        componentMetadataFile.text = """
        id: myId
        name: myName
        description: myDescription
        supplier: mySupplier
        """

        util.loadPipelineConfig(repoPath, repos[0])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse component metadata. Required attribute 'version' is undefined for repository '${repos.first().id}'."

        cleanup:
        repoDir.deleteDir()
    }

    def "load a repo's pipeline config with invalid phase type"() {
        when:
        util.loadPipelineConfig(steps.env.WORKSPACE, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline config. 'repo' is undefined."

        when:
        util.loadPipelineConfig(steps.env.WORKSPACE, [:])

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline config. 'repo' is undefined."
    }

    def "load a repo's pipeline config with invalid target for phase type Makefile"() {
        given:
        def repoPath = Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "A").toString()
        def repoDir = util.createDirectory(repoPath)
        def repos = createProject().repositories

        def componentMetadataFile = Paths.get(repoPath, MROPipelineUtil.COMPONENT_METADATA_FILE_NAME)
        def pipelineConfigFile = Paths.get(repoPath, MROPipelineUtil.PipelineConfig.FILE_NAMES.first())

        when:
        componentMetadataFile << """
        id: myId
        name: myName
        description: myDescription
        supplier: mySupplier
        version: myVersion
        references: myReferences
        """

        pipelineConfigFile << """
        phases:
          build:
            type: Makefile
        """

        util.loadPipelineConfig(repoPath, repos[0])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline phase config. Required attribute 'phase.target' is undefined in phase 'build'."

        when:
        componentMetadataFile.text = """
        id: myId
        name: myName
        description: myDescription
        supplier: mySupplier
        version: myVersion
        references: myReferences
        """

        pipelineConfigFile.text = """
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
        def repoPath = Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "A").toString()
        def repoDir = util.createDirectory(repoPath)
        def repos = createProject().repositories

        def pipelineConfigFile = Paths.get(repoPath, MROPipelineUtil.PipelineConfig.FILE_NAMES.first())
        def componentMetadataFile = Paths.get(repoPath, MROPipelineUtil.COMPONENT_METADATA_FILE_NAME)

        when:
        componentMetadataFile << """
        id: myId
        name: myName
        description: myDescription
        supplier: mySupplier
        version: myVersion
        references: myReferences
        """

        pipelineConfigFile << """
        phases:
          build:
            type: ShellScript
        """

        util.loadPipelineConfig(repoPath, repos[0])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse pipeline phase config. Required attribute 'phase.script' is undefined in phase 'build'."

        when:
        componentMetadataFile.text = """
        id: myId
        name: myName
        description: myDescription
        supplier: mySupplier
        version: myVersion
        references: myReferences
        """

        pipelineConfigFile.text = """
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

    def "load a repo's pipeline config with missing metadata.yml"() {
        given:
        def repoPath = Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "A").toString()
        def repoDir = util.createDirectory(repoPath)
        def repos = createProject().repositories

        when:
        util.loadPipelineConfig(repoPath, repos[0])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse component metadata. Required file '${MROPipelineUtil.COMPONENT_METADATA_FILE_NAME}' does not exist in repository '${repos[0].id}'."

        cleanup:
        repoDir.deleteDir()
    }

    def "load multiple repos' pipeline configs"() {
        given:
        def repoPathA = Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "A").toString()
        def repoDirA = util.createDirectory(repoPathA)
        def componentMetadataFileA = Paths.get(repoPathA, MROPipelineUtil.COMPONENT_METADATA_FILE_NAME)
        def pipelineConfigFileA = Paths.get(repoPathA, MROPipelineUtil.PipelineConfig.FILE_NAMES.first())

        def repoPathB = Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "B").toString()
        def repoDirB = util.createDirectory(repoPathB)
        def componentMetadataFileB = Paths.get(repoPathB, MROPipelineUtil.COMPONENT_METADATA_FILE_NAME)
        def pipelineConfigFileB = Paths.get(repoPathB, MROPipelineUtil.PipelineConfig.FILE_NAMES.first())

        def repoPathC = Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "C").toString()
        def repoDirC = util.createDirectory(repoPathC)
        def componentMetadataFileC = Paths.get(repoPathC, MROPipelineUtil.COMPONENT_METADATA_FILE_NAME)
        def pipelineConfigFileC = Paths.get(repoPathC, MROPipelineUtil.PipelineConfig.FILE_NAMES.first())

        def repos = createProject().repositories

        when:
        componentMetadataFileA << """
        id: myId-A
        name: myName-A
        description: myDescription-A
        supplier: mySupplier-A
        version: myVersion-A
        references: myReferences-A
        """

        pipelineConfigFileB << """
        dependencies:
          - A
        
        phases:
          build:
            type: Makefile
            target: build
        """

        componentMetadataFileB << """
        id: myId-B
        name: myName-B
        description: myDescription-B
        supplier: mySupplier-B
        version: myVersion-B
        references: myReferences-B
        """

        pipelineConfigFileC << """
        dependencies:
          - B
        
        phases:
          test:
            type: ShellScript
            script: test.sh
        """

        componentMetadataFileC << """
        id: myId-C
        name: myName-C
        description: myDescription-C
        supplier: mySupplier-C
        version: myVersion-C
        references: myReferences-C
        """

        def result = util.loadPipelineConfigs(repos.clone())

        then:
        def expected = [
            repos[0] << [
                metadata: [
                    id: "myId-A",
                    name: "myName-A",
                    description: "myDescription-A",
                    supplier: "mySupplier-A",
                    version: "myVersion-A",
                    references: "myReferences-A"
                ],
                pipelineConfig: []
            ],
            repos[1] << [
                metadata: [
                    id: "myId-B",
                    name: "myName-B",
                    description: "myDescription-B",
                    supplier: "mySupplier-B",
                    version: "myVersion-B",
                    references: "myReferences-B"
                ],
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
                metadata: [
                    id: "myId-C",
                    name: "myName-C",
                    description: "myDescription-C",
                    supplier: "mySupplier-C",
                    version: "myVersion-C",
                    references: "myReferences-C"
                ],
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

    def "walk repo directories"() {
        given:
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
