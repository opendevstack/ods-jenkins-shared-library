package org.ods.util

@Grab('org.yaml:snakeyaml:1.24')

import groovy.transform.InheritConstructors

import hudson.Functions

import java.nio.file.Paths

import org.ods.dependency.DependencyGraph
import org.ods.dependency.Node
import org.ods.util.Project
import org.yaml.snakeyaml.Yaml

@InheritConstructors
class MROPipelineUtil extends PipelineUtil {

    class PipelineConfig {
        // TODO: deprecate .pipeline-config.yml in favor of release-manager.yml
        static final List FILE_NAMES = ["release-manager.yml", ".pipeline-config.yml"]

        static final String REPO_TYPE_ODS_CODE = "ods"
        static final String REPO_TYPE_ODS_SERVICE = "ods-service"
        static final String REPO_TYPE_ODS_TEST = "ods-test"

        static final String PHASE_EXECUTOR_TYPE_MAKEFILE = "Makefile"
        static final String PHASE_EXECUTOR_TYPE_SHELLSCRIPT = "ShellScript"

        static final List PHASE_EXECUTOR_TYPES = [
            PHASE_EXECUTOR_TYPE_MAKEFILE,
            PHASE_EXECUTOR_TYPE_SHELLSCRIPT
        ]
    }

    class PipelineEnvs {
        static final String DEV = "dev"
        static final String QA = "qa"
        static final String PROD = "prod"
    }

    class PipelinePhases {
        static final String BUILD = "Build"
        static final String DEPLOY = "Deploy"
        static final String FINALIZE = "Finalize"
        static final String INIT = "Init"
        static final String RELEASE = "Release"
        static final String TEST = "Test"

        static final List ALWAYS_PARALLEL = []
    }

    enum PipelinePhaseLifecycleStage {
        POST_START,
        PRE_EXECUTE_REPO,
        POST_EXECUTE_REPO,
        PRE_END
    }

    static final String COMPONENT_METADATA_FILE_NAME = "metadata.yml"
    static final String REPOS_BASE_DIR = "repositories"

    List<Set<Map>> computeRepoGroups(List<Map> repos) {
        // Transform the list of repository configs into a list of graph nodes
        def nodes = repos.collect { new Node(it) }

        nodes.each { node ->
            node.data.pipelineConfig.dependencies.each { dependency ->
                // Find all nodes that the current node depends on (by repo id)
                nodes.findAll { it.data.id == dependency }.each {
                    // Add a relation between dependent nodes
                    node.addTo(it)
                }
            }
        }

        // Transform sets of graph nodes into a sets of repository configs
        return DependencyGraph.resolveGroups(nodes).nodes.collect { group ->
            group.collect { it.data }
        }
    }

    private void executeODSComponent(Map repo, String baseDir) {
        this.steps.dir(baseDir) {
            def job = this.loadGroovySourceFile("${baseDir}/Jenkinsfile")

            // Collect ODS build artifacts for repo
            repo.data.odsBuildArtifacts = job.getBuildArtifactURIs()
            this.steps.echo("Collected ODS build artifacts for repo '${repo.id}': ${repo.data.odsBuildArtifacts}")

            if (repo.data.odsBuildArtifacts?.failedStage) {
                throw new RuntimeException("Error: aborting due to previous errors in repo '${repo.id}'.")
            }
        }
    }

    Map loadPipelineConfig(String path, Map repo) {
        if (!path?.trim()) {
            throw new IllegalArgumentException("Error: unable to parse pipeline config. 'path' is undefined.")
        }

        if (!path.startsWith(this.steps.env.WORKSPACE)) {
            throw new IllegalArgumentException("Error: unable to parse pipeline config. 'path' must be inside the Jenkins workspace: ${path}")
        }

        if (!repo) {
            throw new IllegalArgumentException("Error: unable to parse pipeline config. 'repo' is undefined.")
        }

        repo.pipelineConfig = [:]

        PipelineConfig.FILE_NAMES.each { filename ->
            def file = Paths.get(path, filename).toFile()
            if (file.exists()) {
                def config = new Yaml().load(file.text) ?: [:]

                // Resolve pipeline phase config, if provided
                if (config.phases) {
                    config.phases.each { name, phase ->
                        // Check for existence of required attribute 'type'
                        if (!phase?.type?.trim()) {
                            throw new IllegalArgumentException("Error: unable to parse pipeline phase config. Required attribute 'phase.type' is undefined in phase '${name}'.")
                        }

                        // Check for validity of required attribute 'type'
                        if (!PipelineConfig.PHASE_EXECUTOR_TYPES.contains(phase.type)) {
                            throw new IllegalArgumentException("Error: unable to parse pipeline phase config. Attribute 'phase.type' contains an unsupported value '${phase.type}' in phase '${name}'. Supported types are: ${PipelineConfig.PHASE_EXECUTOR_TYPES}.")
                        }

                        // Check for validity of an executor type's supporting attributes
                        if (phase.type == PipelineConfig.PHASE_EXECUTOR_TYPE_MAKEFILE) {
                            if (!phase.target?.trim()) {
                                throw new IllegalArgumentException("Error: unable to parse pipeline phase config. Required attribute 'phase.target' is undefined in phase '${name}'.")
                            }
                        } else if (phase.type == PipelineConfig.PHASE_EXECUTOR_TYPE_SHELLSCRIPT) {
                            if (!phase.script?.trim()) {
                                throw new IllegalArgumentException("Error: unable to parse pipeline phase config. Required attribute 'phase.script' is undefined in phase '${name}'.")
                            }
                        }
                    }
                }

                repo.pipelineConfig = config
            }
        }

        def file = Paths.get(path, COMPONENT_METADATA_FILE_NAME).toFile()
        if (!file.exists()) {
            throw new IllegalArgumentException("Error: unable to parse component metadata. Required file '${COMPONENT_METADATA_FILE_NAME}' does not exist in repository '${repo.id}'.")
        }

        // Resolve component metadata
        def metadata = new Yaml().load(file.text) ?: [:]
        if (!metadata.name?.trim()) {
            throw new IllegalArgumentException("Error: unable to parse component metadata. Required attribute 'name' is undefined for repository '${repo.id}'.")
        }

        if (!metadata.description?.trim()) {
            throw new IllegalArgumentException("Error: unable to parse component metadata. Required attribute 'description' is undefined for repository '${repo.id}'.")
        }

        if (!metadata.supplier?.trim()) {
            throw new IllegalArgumentException("Error: unable to parse component metadata. Required attribute 'supplier' is undefined for repository '${repo.id}'.")
        }

        if (!metadata.version?.toString()?.trim()) {
            throw new IllegalArgumentException("Error: unable to parse component metadata. Required attribute 'version' is undefined for repository '${repo.id}'.")
        }

        repo.metadata = metadata

        return repo
    }

    List<Map> loadPipelineConfigs(List<Map> repos) {
        def visitor = { baseDir, repo ->
            loadPipelineConfig(baseDir, repo)
        }

        walkRepoDirectories(repos, visitor)
        return repos
    }

    Closure prepareCheckoutRepoNamedJob(Map repo, Closure preExecute = null, Closure postExecute = null) {
        return [
            repo.id,
            {
                if (preExecute) {
                    preExecute(this.steps, repo)
                }

                def scm = this.steps.checkout([
                    $class: 'GitSCM',
                    branches: [
                        [ name: repo.branch ]
                    ],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [
                        [ $class: 'RelativeTargetDirectory', relativeTargetDir: "${REPOS_BASE_DIR}/${repo.id}" ]
                    ],
                    submoduleCfg: [],
                    userRemoteConfigs: [
                        [ credentialsId: this.project.services.bitbucket.credentials.id, url: repo.url ]
                    ]
                ])

                repo.data.git = [
                    branch: scm.GIT_BRANCH,
                    commit: scm.GIT_COMMIT,
                    previousCommit: scm.GIT_PREVIOUS_COMMIT,
                    previousSucessfulCommit: scm.GIT_PREVIOUS_SUCCESSFUL_COMMIT,
                    url: scm.GIT_URL
                ]

                if (postExecute) {
                    postExecute(this.steps, repo)
                }
            }
        ]
    }

    void prepareCheckoutReposNamedJob(List<Map> repos, Closure preExecute = null, Closure postExecute = null) {
        repos.collectEntries { repo ->
            this.prepareCheckoutRepoNamedJob(repo, preExecute, postExecute)
        }
    }

    Set<Closure> prepareExecutePhaseForRepoNamedJob(String name, Map repo, Closure preExecute = null, Closure postExecute = null) {
        return [
            repo.id,
            {
                this.executeBlockAndFailBuild {
                    def baseDir = "${this.steps.env.WORKSPACE}/${REPOS_BASE_DIR}/${repo.id}"

                    if (preExecute) {
                        preExecute(this.steps, repo)
                    }

                    if (repo.type?.toLowerCase() == PipelineConfig.REPO_TYPE_ODS_CODE) {
                        this.steps.stage('ODS Code Component') {
                            if (name == PipelinePhases.BUILD) {
                                executeODSComponent(repo, baseDir)
                            } else {
                                this.steps.echo("Repo '${repo.id}' is of type ODS Code Component. Nothing to do in phase '${name}'")
                            }
                        }
                    } else if (repo.type?.toLowerCase() == PipelineConfig.REPO_TYPE_ODS_SERVICE) {
                        this.steps.stage('ODS Service Component') {
                            if (name == PipelinePhases.BUILD) {
                                executeODSComponent(repo, baseDir)
                            } else {
                                this.steps.echo("Repo '${repo.id}' is of type ODS Service Component. Nothing to do in phase '${name}'")
                            }
                        }
                    } else if (repo.type?.toLowerCase() == PipelineConfig.REPO_TYPE_ODS_TEST) {
                        this.steps.stage('ODS Test Component') {
                            if (name == PipelinePhases.TEST) {
                                executeODSComponent(repo, baseDir)
                            } else {
                                this.steps.echo("Repo '${repo.id}' is of type ODS Test Component. Nothing to do in phase '${name}'")
                            }
                        }
                    } else {
                        def phaseConfig = repo.pipelineConfig.phases ? repo.pipelineConfig.phases[name] : null
                        if (phaseConfig) {
                            def label = "${repo.id} (${repo.url})"

                            if (phaseConfig.type == PipelineConfig.PHASE_EXECUTOR_TYPE_MAKEFILE) {
                                this.steps.dir(baseDir) {
                                    def steps = "make ${phaseConfig.target}"
                                    this.steps.sh script: steps, label: label
                                }
                            } else if (phaseConfig.type == PipelineConfig.PHASE_EXECUTOR_TYPE_SHELLSCRIPT) {
                                this.steps.dir(baseDir) {
                                    def steps = "./scripts/${phaseConfig.steps}"
                                    this.steps.sh script: steps, label: label
                                }
                            }
                        } else {
                            // Ignore undefined phases
                        }
                    }

                    if (postExecute) {
                        postExecute(this.steps, repo)
                    }
                }
            }
        ]
    }

    List<Set<Closure>> prepareExecutePhaseForReposNamedJob(String name, List<Set<Map>> repos, Closure preExecute = null, Closure postExecute = null) {
        // In some phases, we can run all repos in parallel
        if (PipelinePhases.ALWAYS_PARALLEL.contains(name)) {
            repos = [repos.flatten() as Set<Map>]
        }

        repos.collect { group ->
            group.collectEntries { repo ->
                prepareExecutePhaseForRepoNamedJob(name, repo, preExecute, postExecute)
            }
        }
    }

    private void walkRepoDirectories(List<Map> repos, Closure visitor) {
        repos.each { repo ->
            // Apply the visitor to the repo at the repo's base dir
            visitor("${this.steps.env.WORKSPACE}/${REPOS_BASE_DIR}/${repo.id}", repo)
        }
    }

    void warnBuildAboutUnexecutedJiraTests(List unexecutedJiraTests) {
        this.project.setHasUnexecutedJiraTests(true)
        def unexecutedJiraTestKeys = unexecutedJiraTests.collect { it.key }.join(", ")
        this.warnBuild("Warning: found unexecuted Jira tests: ${unexecutedJiraTestKeys}.")
    }

    void warnBuildIfTestResultsContainFailure(Map testResults) {
        if (testResults.testsuites.find { (it.errors && it.errors.toInteger() > 0) || (it.failures && it.failures.toInteger() > 0) }) {
            this.project.setHasFailingTests(true)
            this.warnBuild("Warning: found failing tests in test reports.")
        }
    }
}
