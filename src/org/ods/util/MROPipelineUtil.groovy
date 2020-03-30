package org.ods.util

@Grab('org.yaml:snakeyaml:1.24')

import groovy.transform.InheritConstructors

import hudson.Functions

import java.nio.file.Paths

import org.ods.dependency.DependencyGraph
import org.ods.dependency.Node
import org.ods.service.OpenShiftService
import org.ods.service.ServiceRegistry
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

    private void finalizeODSComponent(Map repo, String baseDir) {
        def os = ServiceRegistry.instance.get(OpenShiftService)

        def targetEnvironment = this.project.buildParams.targetEnvironment
        def imageShaFile = 'image-sha'
        def targetProject = this.project.targetProject
        def envParamsFile = this.project.environmentParamsFile
        def envParams = this.project.getEnvironmentParams(envParamsFile)
        def componentSelector = "app=${this.project.key}-${repo.id}"

        steps.dir(baseDir) {
            def openshiftDir = 'openshift-exported'
            def exportRequired = true
            if (steps.fileExists('openshift')) {
                steps.echo("Found 'openshift' folder, current OpenShift state will not be exported into 'openshift-exported'.")
                openshiftDir = 'openshift'
                exportRequired = false
            } else {
                steps.sh(
                    script: "mkdir -p ${openshiftDir}",
                    label: "Ensure ${openshiftDir} exists"
                )
            }
            steps.dir(openshiftDir) {
                def filesToStage = []
                def commitMessage = ''
                if (exportRequired) {
                    commitMessage = 'Export configuration'
                    steps.echo("Exporting current OpenShift state to folder '${openshiftDir}'.")
                    def targetFile = 'template.yml'
                    os.tailorExport(
                        targetProject,
                        componentSelector,
                        envParams,
                        targetFile
                    )
                    filesToStage << targetFile
                } else {
                    commitMessage = 'Export image sha'
                    // TODO: Display drift?
                    // if (os.tailorHasDrift(targetProject, componentSelector, envParamsFile)) {
                    //     throw new RuntimeException("Error: environment '${targetProject}' is not in sync with definition in 'openshift' folder.")
                    // }
                }

                def collectedImage = repo?.data.odsBuildArtifacts?."OCP Docker image"
                def imageSha = collectedImage.substring(collectedImage.lastIndexOf("@sha256:") + 1)
                steps.writeFile(file: imageShaFile, text: imageSha)
                filesToStage << imageShaFile

                if (this.project.isWorkInProgress) {
                    steps.sh(
                        script: """
                        git add ${filesToStage.join(' ')}
                        git commit -m "${commitMessage} [ci skip]"
                        git push origin ${repo.branch}
                        """,
                        label: "commit and push new state"
                    )
                } else {
                    os.tagImageSha(repo.id, targetProject, imageSha, this.project.targetTag)
                    steps.sh(
                        script: """
                        git add ${filesToStage.join(' ')}
                        git commit -m "${commitMessage} [ci skip]"
                        """,
                        label: "commit new state"
                    )
                    tagAndPushBranch(this.project.gitReleaseBranch, this.project.targetTag)
                }
            }
        }
    }

    private void deployODSComponent(Map repo, String baseDir) {
        def os = ServiceRegistry.instance.get(OpenShiftService)

        def targetEnvironment = this.project.buildParams.targetEnvironment
        def imageShaFile = 'image-sha'
        def targetProject = this.project.targetProject
        def envParamsFile = this.project.environmentParamsFile
        def envParams = this.project.getEnvironmentParams(envParamsFile)
        def openshiftRolloutTimeoutMinutes = this.project.environmentConfig?.openshiftRolloutTimeoutMinutes ?: 5

        def componentSelector = "app=${this.project.key}-${repo.id}"

        steps.dir(baseDir) {
            def openshiftDir = 'openshift-exported'
            if (steps.fileExists('openshift')) {
                openshiftDir = 'openshift'
            }

            steps.dir(openshiftDir) {
                steps.echo("Applying desired OpenShift state defined in ${openshiftDir}@${this.project.baseTag} to ${this.project.targetProject}.")
                os.tailorApply(
                    targetProject,
                    componentSelector,
                    'bc', // exclude build configs
                    envParamsFile,
                    true
                )
            }

            def definedImageSha = steps.readFile("${openshiftDir}/${imageShaFile}")
            def sourceProject = "${this.project.key}-${Project.getConcreteEnvironment(this.project.sourceEnv, this.project.buildParams.version, this.project.versionedDevEnvsEnabled)}"
            if (this.project.targetClusterIsExternal) {
                os.importImageFromSourceRegistry(
                    repo.id,
                    sourceProject,
                    definedImageSha,
                    targetProject,
                    this.project.targetTag
                )
            } else {
                os.importImageFromProject(
                    repo.id,
                    sourceProject,
                    definedImageSha,
                    targetProject,
                    this.project.targetTag
                )
            }

            // tag with latest, which triggers rollout
            os.tagImageWithLatest(repo.id, targetProject, this.project.targetTag)

            // verify that image sha is running
            // caution: relies on an image trigger being present ...
            os.watchRollout(targetProject, repo.id, openshiftRolloutTimeoutMinutes)
            def latestVersion = os.getLatestVersion(targetProject, repo.id)
            def runningImageSha = os.getRunningImageSha(targetProject, repo.id, latestVersion)

            if (definedImageSha != runningImageSha) {
                throw new RuntimeException("Error: running image '${runningImageSha}' is not the same as the defined image '${definedImageSha}'.")
            } else {
                steps.echo("Running container is using defined image ${definedImageSha}.")
            }

            // collect data required for documents
            def pod = os.getPodDataForDeployment(repo.id, latestVersion)
            repo.data.pod = pod
            repo.data.odsBuildArtifacts = [
                "OCP Build Id": "N/A",
                "OCP Docker image": runningImageSha.split(':').last(),
                "OCP Deployment Id": latestVersion,
            ]

            if (git.remoteTagExists(this.project.targetTag)) {
                steps.echo("Skipping tag because it already exists.")
            } else {
                tagAndPush(this.project.targetTag)
            }
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

    def tagAndPushBranch(String branch, String tag) {
        if (this.git.remoteTagExists(tag)) {
            this.steps.echo("Skipping tag because it already exists.")
        } else {
            this.git.createTag(tag)
            this.git.pushBranchWithTags(branch)
        }
    }

    def tagAndPush(String tag) {
        git.createTag(tag)
        git.pushTag(tag)
    }

    Closure prepareCheckoutRepoNamedJob(Map repo, Closure preExecute = null, Closure postExecute = null) {
        return [
            repo.id,
            {
                if (preExecute) {
                    preExecute(this.steps, repo)
                }

                def scm = null
                def scmBranch = repo.branch
                if (this.project.isPromotionMode) {
                    scm = checkoutTagInRepoDir(repo, this.project.baseTag)
                    scmBranch = this.project.gitReleaseBranch
                } else {
                    if (this.project.isWorkInProgress) {
                        scm = checkoutBranchInRepoDir(repo, repo.branch)
                    } else {
                        // check if release manager repo already has a release branch
                        if (git.remoteBranchExists(this.project.gitReleaseBranch)) {
                            scm = checkoutBranchInRepoDir(repo, this.project.gitReleaseBranch)
                        } else {
                            scm = checkoutBranchInRepoDir(repo, repo.branch)
                            steps.dir("${REPOS_BASE_DIR}/${repo.id}") {
                                git.checkoutNewLocalBranch(this.project.gitReleaseBranch)
                            }
                        }
                        scmBranch = this.project.gitReleaseBranch
                    }
                }

                repo.data.git = [
                    branch: scmBranch,
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

    def checkoutTagInRepoDir(Map repo, String tag) {
        steps.echo("Checkout ${repo.id}@${tag}")
        def credentialsId = this.project.services.bitbucket.credentials.id
        git.checkout(
            tag,
            [[ $class: 'RelativeTargetDirectory', relativeTargetDir: "${REPOS_BASE_DIR}/${repo.id}" ]],
            [[ credentialsId: credentialsId, url: repo.url ]]
        )
    }

    def checkoutBranchInRepoDir(Map repo, String branch) {
        steps.echo("Checkout ${repo.id}@${branch}")
        def credentialsId = this.project.services.bitbucket.credentials.id
        git.checkout(
            branch,
            [
                [ $class: 'RelativeTargetDirectory', relativeTargetDir: "${REPOS_BASE_DIR}/${repo.id}" ],
                [ $class: 'LocalBranch', localBranch: "**" ]
            ],
            [[ credentialsId: credentialsId, url: repo.url ]]
        )
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
                    def targetEnvToken = this.project.buildParams.targetEnvironmentToken

                    if (preExecute) {
                        preExecute(this.steps, repo)
                    }

                    if (repo.type?.toLowerCase() == PipelineConfig.REPO_TYPE_ODS_CODE) {
                        this.steps.stage('ODS Code Component') {
                            if (this.project.isAssembleMode && name == PipelinePhases.BUILD) {
                                executeODSComponent(repo, baseDir)
                            } else if (this.project.isPromotionMode && name == PipelinePhases.DEPLOY) {
                                deployODSComponent(repo, baseDir)
                            } else if (this.project.isAssembleMode && name == PipelinePhases.FINALIZE) {
                                finalizeODSComponent(repo, baseDir)
                            } else {
                                this.steps.echo("Repo '${repo.id}' is of type ODS Code Component. Nothing to do in phase '${name}' for target environment '${targetEnvToken}'.")
                            }
                        }
                    } else if (repo.type?.toLowerCase() == PipelineConfig.REPO_TYPE_ODS_SERVICE) {
                        this.steps.stage('ODS Service Component') {
                            if (this.project.isAssembleMode && name == PipelinePhases.BUILD) {
                                executeODSComponent(repo, baseDir)
                            } else if (this.project.isPromotionMode && name == PipelinePhases.DEPLOY) {
                                deployODSComponent(repo, baseDir)
                            } else if (this.project.isAssembleMode && name == PipelinePhases.FINALIZE) {
                                finalizeODSComponent(repo, baseDir)
                            } else {
                                this.steps.echo("Repo '${repo.id}' is of type ODS Service Component. Nothing to do in phase '${name}' for target environment '${targetEnvToken}'.")
                            }
                        }
                    } else if (repo.type?.toLowerCase() == PipelineConfig.REPO_TYPE_ODS_TEST) {
                        this.steps.stage('ODS Test Component') {
                            if (name == PipelinePhases.TEST) {
                                executeODSComponent(repo, baseDir)
                            } else if (this.project.isPromotionMode && name == PipelinePhases.DEPLOY) {
                                tagAndPushBranch(this.project.gitReleaseBranch, this.project.targetTag)
                            } else if (this.project.isAssembleMode && !this.project.isWorkInProgress && name == PipelinePhases.FINALIZE) {
                                tagAndPushBranch(this.project.gitReleaseBranch, this.project.targetTag)
                            } else {
                                this.steps.echo("Repo '${repo.id}' is of type ODS Test Component. Nothing to do in phase '${name}' for target environment'${targetEnvToken}'.")
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
