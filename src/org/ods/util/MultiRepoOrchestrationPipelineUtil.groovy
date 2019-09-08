package org.ods.util

@Grab('org.yaml:snakeyaml:1.24')

import groovy.transform.InheritConstructors

import org.ods.dependency.DependencyGraph
import org.ods.dependency.Node
import org.ods.phase.PipelinePhases
import org.yaml.snakeyaml.Yaml

@InheritConstructors
class MultiRepoOrchestrationPipelineUtil extends PipelineUtil {

    static final String PIPELINE_CONIFG_FILE_NAME = ".pipeline-config.yml"
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

                if (repo.type?.toLowerCase() == 'ods') {
                    if (name == PipelinePhases.BUILD_PHASE) {
                        this.script.stage('ODS') {
                            this.script.dir(baseDir) {
                                loadGroovySourceFile("${baseDir}/Jenkinsfile")
                            }
                        }
                    } else {
                        def skipEchoMessage = "ODS build ${repo.id} - skipping phase ${name}"
                        this.script.stage('ODS') {
                            this.script.echo(skipEchoMessage)
                        }
                    }
                } else {
                    def phaseConfig = repo.pipelineConfig.phases ? repo.pipelineConfig.phases[name] : null
                    if (phaseConfig) {
                        def label = "${repo.id} (${repo.url})"

                        if (phaseConfig.type == 'Makefile') {
                            this.script.dir("${baseDir}") {
                                def script = "make ${phaseConfig.task}"
                                this.script.sh script: script, label: label
                            }
                        } else if (phaseConfig.type == 'ShellScript') {
                            this.script.dir("${baseDir}") {
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
        if (PipelinePhases.ALWAYS_PARALLEL_PHASES.contains(name)) {
            repos = [repos.flatten() as Set<Map>]
        }

        repos.collect { group ->
            group.collectEntries { repo ->
                prepareExecutePhaseForRepoNamedJob(name, repo, preExecute, postExecute)
            }
        }
    }

    Map readPipelineConfig(String path, Map repo) {
        def file = new File("${path}/${PIPELINE_CONIFG_FILE_NAME}")
        def config = file.exists() ? new Yaml().load(file.text) : [:]
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
