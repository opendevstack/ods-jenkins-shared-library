package org.ods.util

@Grab('org.yaml:snakeyaml:1.24')

import groovy.transform.InheritConstructors

import hudson.Functions

import java.nio.file.Paths

import org.ods.dependency.DependencyGraph
import org.ods.dependency.Node
import org.yaml.snakeyaml.Yaml

@InheritConstructors
class MROPipelineUtil extends PipelineUtil {

    class PipelineConfig {
        // TODO: deprecate .pipeline-config.yml in favor of release-manager.yml
        static final List FILE_NAMES = ["release-manager.yml", ".pipeline-config.yml"]

        static final String REPO_TYPE_ODS = "ods"
        static final String REPO_TYPE_ODS_SERVICE = "ods-service"

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
        static final String RELEASE = "Release"
        static final String TEST = "Test"

        static final List ALWAYS_PARALLEL = []
    }

    static final String PROJECT_METADATA_FILE_NAME = "metadata.yml"
    static final String REPOS_BASE_DIR = "repositories"

    private GitUtil git

    MROPipelineUtil(IPipelineSteps steps, GitUtil git) {
        super(steps)
        this.git = git
    }

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

    private void executeBlockWithFailFast(Closure block) {
        try {
            block()
        } catch (ConcurrentModificationException e) {
            // FIXME: shut up for the moment
        } catch (e) {
            this.steps.currentBuild.result = "FAILURE"
            this.steps.echo(e.message)
            hudson.Functions.printThrowable(e)
            throw e
        }
    }

    List getBuildEnvironment(boolean debug = false) {
        def params = this.getBuildParams()

        return [
            "DEBUG=${debug}",
            "MULTI_REPO_BUILD=true",
            "MULTI_REPO_ENV=${params.targetEnvironment}",
            "RELEASE_PARAM_CHANGE_ID=${params.changeId}",
            "RELEASE_PARAM_CHANGE_DESC=${params.changeDescription}",
            "RELEASE_PARAM_CONFIG_ITEM=${params.configItem}",
            "RELEASE_PARAM_VERSION=${params.version}",
            "SOURCE_CLONE_ENV=${params.sourceEnvironmentToClone}"
        ]
    }

    Map getBuildParams() {
        def version = this.steps.env.version?.trim() ?: "WIP"
        def targetEnvironment = this.steps.env.environment?.trim() ?: "dev"
        def sourceEnvironmentToClone = this.steps.env.sourceEnvironmentToClone?.trim() ?: targetEnvironment

        def changeId = this.steps.env.changeId?.trim() ?: "${version}-${targetEnvironment}"
        def configItem = this.steps.env.configItem?.trim() ?: "UNDEFINED"
        def changeDescription = this.steps.env.changeDescription?.trim() ?: "UNDEFINED"

        return [
            changeDescription: changeDescription,
            changeId: changeId,
            configItem: configItem,
            sourceEnvironmentToClone: sourceEnvironmentToClone,
            targetEnvironment: targetEnvironment,
            version: version
        ]
    }

    boolean isTriggeredByChangeManagementProcess() {
        def changeId = this.steps.env.changeId?.trim()
        def configItem = this.steps.env.configItem?.trim()
        return changeId && configItem
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

                if (!config.metadata) {
                    throw new IllegalArgumentException("Error: unable to parse pipeline config. Required attribute 'metadata' is undefined for repository '${repo.id}'.")
                }

                // Resolve pipeline metadata, if provided
                if (config.metadata) {
                    if (!config.metadata.name?.trim()) {
                        throw new IllegalArgumentException("Error: unable to parse pipeline config. Required attribute 'metadata.name' is undefined for repository '${repo.id}'.")
                    }

                    if (!config.metadata.description?.trim()) {
                        throw new IllegalArgumentException("Error: unable to parse pipeline config. Required attribute 'metadata.description' is undefined for repository '${repo.id}'.")
                    }

                    if (!config.metadata.supplier?.trim()) {
                        throw new IllegalArgumentException("Error: unable to parse pipeline config. Required attribute 'metadata.supplier' is undefined for repository '${repo.id}'.")
                    }

                    if (!config.metadata.version?.trim()) {
                        throw new IllegalArgumentException("Error: unable to parse pipeline config. Required attribute 'metadata.version' is undefined for repository '${repo.id}'.")
                    }
                }

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

        return repo
    }

    List<Map> loadPipelineConfigs(List<Map> repos) {
        def visitor = { baseDir, repo ->
            loadPipelineConfig(baseDir, repo)
        }

        walkRepoDirectories(repos, visitor)
        return repos
    }

    Map loadProjectMetadata(String filename = PROJECT_METADATA_FILE_NAME) {
        if (filename == null) {
            throw new IllegalArgumentException("Error: unable to parse project meta data. 'filename' is undefined.")
        }

        def file = Paths.get(this.steps.env.WORKSPACE, filename).toFile()
        if (!file.exists()) {
            throw new RuntimeException("Error: unable to load project meta data. File '${this.steps.env.WORKSPACE}/${filename}' does not exist.")
        }

        def result = new Yaml().load(file.text)

        // Check for existence of required attribute 'id'
        if (!result?.id?.trim()) {
            throw new IllegalArgumentException("Error: unable to parse project meta data. Required attribute 'id' is undefined.")
        }

        // Check for existence of required attribute 'name'
        if (!result?.name?.trim()) {
            throw new IllegalArgumentException("Error: unable to parse project meta data. Required attribute 'name' is undefined.")
        }

        if (result.description == null) {
            result.description = ""
        }

        // Check for existence of required attribute 'repositories'
        if (!result.repositories) {
            throw new IllegalArgumentException("Error: unable to parse project meta data. Required attribute 'repositories' is undefined.")
        }

        result.data = [:]
        result.data.documents = [:]

        result.data.git = [
            commit: this.git.getCommit(),
            url: this.git.getURL()
        ]

        result.repositories.eachWithIndex { repo, index ->
            // Check for existence of required attribute 'repositories[i].id'
            if (!repo.id?.trim()) {
                throw new IllegalArgumentException("Error: unable to parse project meta data. Required attribute 'repositories[${index}].id' is undefined.")
            }

            repo.data = [:]
            repo.data.documents = [:]

            // Resolve repo URL, if not provided
            if (!repo.url?.trim()) {
                this.steps.echo("Could not determine Git URL for repo '${repo.id}' from project meta data. Attempting to resolve automatically...")

                def gitURL = this.getGitURL(this.steps.env.WORKSPACE, "origin")
                if (repo.name?.trim()) {
                    repo.url = gitURL.resolve("${repo.name}.git").toString()
                    repo.remove("name")
                } else {
                    repo.url = gitURL.resolve("${result.id.toLowerCase()}-${repo.id}.git").toString()
                }

                this.steps.echo("Resolved Git URL for repo '${repo.id}' to '${repo.url}'")
            }

            // Resolve repo branch, if not provided
            if (!repo.branch?.trim()) {
                this.steps.echo("Could not determine Git branch for repo '${repo.id}' from project meta data. Assuming 'master'.")
                repo.branch = "master"
            }
        }

        return result
    }

    Closure prepareCheckoutRepoNamedJob(Map repo, Closure preExecute = null, Closure postExecute = null) {
        def project = loadProjectMetadata()

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
                        [ credentialsId: project.services.bitbucket.credentials.id, url: repo.url ]
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
            prepareCheckoutRepoNamedJob(repo, preExecute, postExecute)
        }
    }

    Set<Closure> prepareExecutePhaseForRepoNamedJob(String name, Map repo, Closure preExecute = null, Closure postExecute = null) {
        return [
            repo.id,
            {
                executeBlockWithFailFast {
                    def baseDir = "${this.steps.env.WORKSPACE}/${REPOS_BASE_DIR}/${repo.id}"

                    if (preExecute) {
                        preExecute(this.steps, repo)
                    }

                    if (repo.type?.toLowerCase() == PipelineConfig.REPO_TYPE_ODS) {
                        if (name == PipelinePhases.BUILD) {
                            this.steps.stage('ODS') {
                                this.steps.dir(baseDir) {
                                    def job = loadGroovySourceFile("${baseDir}/Jenkinsfile")

                                    // Collect ODS build artifacts for repo
                                    repo.data.odsBuildArtifacts = job.getBuildArtifactURIs()
                                    this.steps.echo("Collected ODS build artifacts for repo '${repo.id}': ${repo.data.odsBuildArtifacts}")

                                    if (repo.data.odsBuildArtifacts?.failedStage) {
                                        throw new RuntimeException("Error: aborting due to previous errors in repo '${repo.id}'.")
                                    }
                                }
                            }
                        } else {
                            this.steps.stage('ODS') {
                                this.steps.echo("Repo '${repo.id}' is of type ODS Component. Nothing to do in phase '${name}'")
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
}
