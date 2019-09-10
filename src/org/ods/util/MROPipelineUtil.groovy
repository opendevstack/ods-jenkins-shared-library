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

    private static final String REPOS_BASE_DIR = "repositories"

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
        def version = this.script.env.version?.trim() ?: "WIP"
        def targetEnvironment = this.script.env.environment?.trim() ?: "dev"
        def sourceEnvironmentToClone = this.script.env.sourceEnvironmentToClone?.trim() ?: targetEnvironment

        def changeId = this.script.env.changeId?.trim() ?: "${version}-${targetEnvironment}"
        def configItem = this.script.env.configItem?.trim() ?: "UNDEFINED"
        def changeDescription = this.script.env.changeDescription?.trim() ?: "UNDEFINED"

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

    Closure prepareCheckoutRepoNamedJob(Map repo, Closure preExecute = null, Closure postExecute = null) {
        def project = loadProjectMetadata()

        return [
            repo.id,
            {
                if (preExecute) {
                    preExecute(this.script, repo)
                }

                this.script.checkout([
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
                    postExecute(this.script, repo)
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
                def baseDir = "${this.script.WORKSPACE}/${REPOS_BASE_DIR}/${repo.id}"

                if (preExecute) {
                    preExecute(this.script, repo)
                }

                if (repo.type?.toLowerCase() == PipelineConfig.REPO_TYPE_ODS) {
                    if (name == PipelinePhases.BUILD) {
                        this.script.stage('ODS') {
                            this.script.dir(baseDir) {
                                def job = loadGroovySourceFile("${baseDir}/Jenkinsfile")

                                // Collect ODS build artifact URIs for repo
                                repo.buildArtifactURIs = job.getBuildArtifactURIs()
                                this.script.echo("Collected ODS build artifact URIs for repo '${repo.id}': ${repo.buildArtifactURIs}")
                            }
                        }
                    } else {
                        this.script.stage('ODS') {
                            this.script.echo("Repo '${repo.id}' is of type ODS Component. Nothing to do in phase '${name}'")
                        }
                    }
                } else {
                    def phaseConfig = repo.pipelineConfig.phases ? repo.pipelineConfig.phases[name] : null
                    if (phaseConfig) {
                        def label = "${repo.id} (${repo.url})"

                        if (phaseConfig.type == PipelineConfig.PHASE_EXECUTOR_TYPE_MAKEFILE) {
                            this.script.dir(baseDir) {
                                def script = "make ${phaseConfig.task}"
                                this.script.sh script: script, label: label
                            }
                        } else if (phaseConfig.type == PipelineConfig.PHASE_EXECUTOR_TYPE_SHELLSCRIPT) {
                            this.script.dir(baseDir) {
                                def script = "./scripts/${phaseConfig.script}"
                                this.script.sh script: script, label: label
                            }
                        }
                    } else {
                        // Ignore undefined phases
                    }
                }

                if (postExecute) {
                    postExecute(this.script, repo)
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

    Map readPipelineConfig(String path, Map repo) {
        def file = new File("${path}/${PipelineConfig.FILE_NAME}")
        def config = file.exists() ? new Yaml().load(file.text) : [:]

        // Resolve pipeline phase config, if provided
        if (config.phases) {
            config.phases.each { name, phase ->
                // Check for existence of required attribute 'type'
                if (phase.type == null || !phase.type.trim()) {
                    throw new RuntimeException("Error: unable to parse pipeline phase config. Required attribute 'type' is undefined in ${phase}.")
                }

                // Check for validity of required attribute 'type'
                if (!PipelineConfig.PHASE_EXECUTOR_TYPES.contains(phase.type)) {
                    throw new RuntimeException("Error: unable to parse pipeline phase config. Attribute 'type' contains an unsupported value '${phase.type}'.")
                }
            }
        }

        repo.pipelineConfig = config
        return repo
    }

    List<Map> readPipelineConfigs(List<Map> repos) {
        def visitor = { baseDir, repo ->
            readPipelineConfig(baseDir, repo)
        }

        walkRepoDirectories(repos, visitor)
        return repos
    }

    private void walkRepoDirectories(List<Map> repos, Closure visitor) {
        repos.each { repo ->
            // Apply the visitor to the repo at the repo's base dir
            visitor("${this.script.WORKSPACE}/${REPOS_BASE_DIR}/${repo.id}", repo)
        }
    }
}
