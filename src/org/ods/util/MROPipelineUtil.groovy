package org.ods.util

@Grab('org.yaml:snakeyaml:1.24')

import groovy.transform.InheritConstructors

import org.ods.dependency.DependencyGraph
import org.ods.dependency.Node
import org.yaml.snakeyaml.Yaml

@InheritConstructors
class MROPipelineUtil extends PipelineUtil {

    class PipelineConfig {
        static final String FILE_NAME = ".pipeline-config.yml"

        static final String REPO_TYPE_ODS = "ods"

        static final String PHASE_EXECUTOR_TYPE_MAKEFILE    = "Makefile"
        static final String PHASE_EXECUTOR_TYPE_SHELLSCRIPT = "ShellScript"

        static final List PHASE_EXECUTOR_TYPES = [
            PHASE_EXECUTOR_TYPE_MAKEFILE,
            PHASE_EXECUTOR_TYPE_SHELLSCRIPT
        ]
    }

    class PipelinePhases {
        static final String BUILD    = "Build"
        static final String DEPLOY   = "Deploy"
        static final String FINALIZE = "Finalize"
        static final String RELEASE  = "Release"
        static final String TEST     = "Test"

        static final List ALWAYS_PARALLEL = []
    }

    static final String PROJECT_METADATA_FILE_NAME = "metadata.yml"
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

    List getEnvironment(boolean debug = false) {
        def version = this.steps.env.version?.trim() ?: "WIP"
        def targetEnvironment = this.steps.env.environment?.trim() ?: "dev"
        def sourceEnvironmentToClone = this.steps.env.sourceEnvironmentToClone?.trim() ?: targetEnvironment

        def changeId = this.steps.env.changeId?.trim() ?: "${version}-${targetEnvironment}"
        def configItem = this.steps.env.configItem?.trim() ?: "UNDEFINED"
        def changeDescription = this.steps.env.changeDescription?.trim() ?: "UNDEFINED"

        return [
            "DEBUG=${debug}",
            "MULTI_REPO_BUILD=true",
            "MULTI_REPO_ENV=${targetEnvironment}",
            "RELEASE_PARAM_CHANGE_ID=${changeId}",
            "RELEASE_PARAM_CHANGE_DESC=${changeDescription}",
            "RELEASE_PARAM_CONFIG_ITEM=${configItem}",
            "RELEASE_PARAM_VERSION=${version}",
            "SOURCE_CLONE_ENV=${sourceEnvironmentToClone}"
        ]
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

        def file = new File("${path}/${PipelineConfig.FILE_NAME}")
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

        def file = new File("${this.steps.env.WORKSPACE}/${filename}")
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

        result.repositories.eachWithIndex { repo, index ->
            // Check for existence of required attribute 'repositories[i].id'
            if (!repo.id?.trim()) {
                throw new IllegalArgumentException("Error: unable to parse project meta data. Required attribute 'repositories[${index}].id' is undefined.")
            }

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

                this.steps.checkout([
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
                def baseDir = "${this.steps.env.WORKSPACE}/${REPOS_BASE_DIR}/${repo.id}"

                if (preExecute) {
                    preExecute(this.steps, repo)
                }

                if (repo.type?.toLowerCase() == PipelineConfig.REPO_TYPE_ODS) {
                    if (name == PipelinePhases.BUILD) {
                        this.steps.stage('ODS') {
                            this.steps.dir(baseDir) {
                                def job = loadGroovySourceFile("${baseDir}/Jenkinsfile")

                                // Collect ODS build artifact URIs for repo
                                repo.buildArtifactURIs = job.getBuildArtifactURIs()
                                this.steps.echo("Collected ODS build artifact URIs for repo '${repo.id}': ${repo.buildArtifactURIs}")
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
