package org.ods.orchestration.util

import java.nio.file.Files
import java.nio.file.Paths

import org.ods.orchestration.parser.JUnitParser
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import org.ods.orchestration.util.Project
import org.ods.services.GitService

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class MROPipelineUtilSpec extends SpecHelper {

    Project project
    IPipelineSteps steps
    MROPipelineUtil util
    GitService gitService
    def logger

    def setup() {
        project = createProject()
        steps = Spy(util.PipelineSteps)
        gitService = Mock(GitService)
        logger = Mock(Logger)
        util = new MROPipelineUtil(project, steps, gitService, logger)
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

        def repos = project.repositories
        def repoDirs = []
        def componentMetadataFileMap = [:]
        def pipelineConfigFileMap = [:]

        repos.each { repo ->
            def repoPath = Paths.get(steps.env.WORKSPACE, MROPipelineUtil.REPOS_BASE_DIR, "${repo.id}").toString()
            repoDirs << util.createDirectory(repoPath)
            componentMetadataFileMap[repo.id] = Paths.get(repoPath, MROPipelineUtil.COMPONENT_METADATA_FILE_NAME)
            pipelineConfigFileMap[repo.id] = Paths.get(repoPath, MROPipelineUtil.PipelineConfig.FILE_NAMES.first())
        }

        when:
        componentMetadataFileMap[repos[0].id] << """
        id: demo-app-carts
        name: demo-app-carts
        description: demo-app-carts
        supplier: mySupplier-demo-app-carts
        version: myVersion-A
        references: myReferences-A
        """

        pipelineConfigFileMap[repos[0].id] << """
        dependencies:
          - A

        phases:
          build:
            type: Makefile
            target: build
        """

        componentMetadataFileMap[repos[1].id] << """
        id: demo-app-catalogue
        name: demo-app-catalogue
        description: demo-app-catalogue
        supplier: mySupplier-demo-app-catalogue
        version: myVersion-B
        references: myReferences-B
        """

        pipelineConfigFileMap[repos[2].id] << """
        dependencies:
          - B

        phases:
          test:
            type: ShellScript
            script: test.sh
        """

        componentMetadataFileMap[repos[2].id] << """
        id: demo-app-front-end
        name: demo-app-front-end
        description: demo-app-front-end
        supplier: mySupplier-demo-app-front-end
        version: myVersion-C
        references: myReferences-C
        """

        componentMetadataFileMap[repos[3].id] << """
        id: demo-app-test
        name: demo-app-test
        description: demo-app-test
        supplier: mySupplier-demo-app-test
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
            ],
            repos[3] << [
                metadata: [
                    id: "demo-app-test",
                    name: "demo-app-test",
                    description: "demo-app-test",
                    supplier: "mySupplier-demo-app-test",
                    version: "myVersion-C",
                    references: "myReferences-C"
                ],
                pipelineConfig: [:]
            ]
        ]

        result == expected

        cleanup:
        repoDirs.each { repoDir ->
            repoDir.deleteDir()
        }
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
        1 * visitor("${steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/${repos[0].id}", repos[0])
        1 * visitor("${steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/${repos[1].id}", repos[1])
        1 * visitor("${steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/${repos[2].id}", repos[2])
        1 * visitor("${steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/${repos[3].id}", repos[3])

        cleanup:
        repoDirA.deleteDir()
        repoDirB.deleteDir()
        repoDirC.deleteDir()
    }

    def "warn if test results contain failure"() {
        given:
        def testResults = [
            testsuites: [
                [
                    name: "my-suite",
                    errors: 1
                ]
            ]
        ]

        when:
        util.warnBuildIfTestResultsContainFailure(testResults)

        then:
        project.hasFailingTests() == true

        then:
        steps.currentBuild.result == "UNSTABLE"
        1 * logger.warn("Found failing tests in test reports.")

        then:
        noExceptionThrown() // pipeline does not stop here
    }

    def "warn if jira tests are unexecuted"() {
        given:
        def unexecutedJiraTests = [
            [ key: "KEY-1"], [ key: "KEY-2"], [ key: "KEY-3"]
        ]

        when:
        util.warnBuildAboutUnexecutedJiraTests(unexecutedJiraTests)

        then:
        project.hasUnexecutedJiraTests() == true

        then:
        steps.currentBuild.result == "UNSTABLE"
        1 * logger.warn("Found unexecuted Jira tests: KEY-1, KEY-2, KEY-3.")

        then:
        noExceptionThrown() // pipeline does not stop here
    }

    def "checkOutNotReleaseManagerRepoInNotPromotionMode_whenBranchExists"() {
        given:
        Map repo = [:]
        boolean isWorkInProgress = true
        this.project.gitReleaseBranch = "release/1.0.0"
        when:
        util.checkOutNotReleaseManagerRepoInNotPromotionMode(repo, isWorkInProgress)
        then:
        1 * gitService.remoteBranchExists("${this.project.gitReleaseBranch}") >> true
        1 * gitService.checkout("*/${this.project.gitReleaseBranch}", _, _)
    }

    def "checkOutNotReleaseManagerRepoInNotPromotionMode_whenBranchNotExistsAndWIP"() {
        given:
        Map repo = [:]
        repo.branch = "forTesting"
        boolean isWorkInProgress = true
        this.project.gitReleaseBranch = "release/1.0.0"
        when:
        util.checkOutNotReleaseManagerRepoInNotPromotionMode(repo, isWorkInProgress)
        then:
        1 * gitService.remoteBranchExists("${this.project.gitReleaseBranch}") >> false
        1 * gitService.checkout("*/${repo.branch}", _, _)
    }

    def "checkOutNotReleaseManagerRepoInNotPromotionMode_whenBranchNotExistsAndNotWIP"() {
        given:
        Map repo = [:]
        repo.branch = "forTesting"
        boolean isWorkInProgress = false
        this.project.gitReleaseBranch = "release/1.0.0"
        when:
        util.checkOutNotReleaseManagerRepoInNotPromotionMode(repo, isWorkInProgress)
        then:
        1 * gitService.remoteBranchExists("${this.project.gitReleaseBranch}") >> false
        1 * gitService.checkout("*/${repo.branch}", _, _)
        1 * gitService.checkoutNewLocalBranch("${this.project.gitReleaseBranch}")
    }

}
