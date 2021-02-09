package org.ods.orchestration.util

@Grab('org.yaml:snakeyaml:1.24')

import groovy.transform.InheritConstructors

import java.nio.file.Paths

import org.ods.orchestration.dependency.DependencyGraph
import org.ods.orchestration.dependency.Node
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.orchestration.util.DeploymentDescriptor
import org.ods.orchestration.phases.DeployOdsComponent
import org.ods.orchestration.phases.FinalizeOdsComponent
import org.yaml.snakeyaml.Yaml

@InheritConstructors
@SuppressWarnings(['LineLength', 'AbcMetric', 'NestedBlockDepth', 'EmptyElseBlock', 'CyclomaticComplexity', 'GStringAsMapKey', 'UseCollectNested'])
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

    static final String COMPONENT_METADATA_FILE_NAME = 'metadata.yml'
    static final String REPOS_BASE_DIR = 'repositories'
    static final List EXCLUDE_NAMESPACES_FROM_IMPORT = ['openshift']
    static final String ODS_STATE_DIR = 'ods-state'

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

    private void executeODSComponent(Map repo, String baseDir, boolean failfast = true) {
        this.steps.dir(baseDir) {
            if (repo.data.openshift.resurrectedBuild) {
                logger.info("Repository '${repo.id}' is in sync with OpenShift, no need to rebuild")
                return
            }

            def job
            List<String> mainEnv = this.project.getMainReleaseManagerEnv()
            mainEnv << "NOTIFY_BB_BUILD=${!project.isWorkInProgress}"
            this.steps.withEnv (mainEnv) {
                job = this.loadGroovySourceFile("${baseDir}/Jenkinsfile")
            }
            // Collect ODS build artifacts for repo.
            // We get a map with at least two keys ("build" and "deployments").
            def buildArtifacts = job.getBuildArtifactURIs()
            buildArtifacts.each { k, v ->
                if (k != 'failedStage') {
                    repo.data.openshift[k] = v
                }
            }
            def versionAndBuild = "${this.project.buildParams.version}/${this.steps.env.BUILD_NUMBER}"
            repo.data.openshift[DeploymentDescriptor.CREATED_BY_BUILD_STR] = versionAndBuild
            this.logger.debug("Collected ODS build artifacts for repo '${repo.id}': ${repo.data.openshift}")

            if (buildArtifacts.failedStage) {
                repo.data << ['failedStage': buildArtifacts.failedStage]
                if (failfast) {
                    throw new RuntimeException("Error: aborting due to previous errors in repo '${repo.id}'.")
                } else {
                    this.logger.warn("Got errors in repo '${repo.id}', will fail delayed.")
                }
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

        // for those repos (= quickstarters) we supply we want to own the type
        if (metadata.type?.toString()?.trim()) {
            this.logger.debug ("Repository type '${metadata.type}' configured on '${repo.id}' thru component's metadata.yml")
            repo.type = metadata.type
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

    Map<String, Closure> prepareCheckoutRepoNamedJob(Map repo) {
        return [
            repo.id,
            {
                this.logger.startClocked("${repo.id}-scm-checkout")
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
                            try {
                                scm = checkoutBranchInRepoDir(repo, this.project.gitReleaseBranch)
                            } catch (ex) {
                                this.logger.warn """
                                Checkout of '${this.project.gitReleaseBranch}' for repo '${repo.id}' failed.
                                Attempting to checkout '${repo.branch}' and create the release branch from it.
                                """
                                // Possible reasons why this might happen:
                                // * Release branch manually created in RM repo
                                // * Repo is added to metadata.yml file on a release branch
                                // * Release branch has been deleted in repo
                                scm = checkoutBranchInRepoDir(repo, repo.branch)
                                steps.dir("${REPOS_BASE_DIR}/${repo.id}") {
                                    git.checkoutNewLocalBranch(this.project.gitReleaseBranch)
                                }
                            }
                        } else {
                            scm = checkoutBranchInRepoDir(repo, repo.branch)
                            steps.dir("${REPOS_BASE_DIR}/${repo.id}") {
                                git.checkoutNewLocalBranch(this.project.gitReleaseBranch)
                            }
                        }
                        scmBranch = this.project.gitReleaseBranch
                    }
                }
                this.logger.debugClocked("${repo.id}-scm-checkout")

                repo.data.git = [
                    branch: scmBranch,
                    commit: scm.GIT_COMMIT,
                    previousCommit: scm.GIT_PREVIOUS_COMMIT,
                    previousSucessfulCommit: scm.GIT_PREVIOUS_SUCCESSFUL_COMMIT,
                    url: scm.GIT_URL,
                    baseTag: this.project.baseTag,
                    targetTag: this.project.targetTag
                ]
                def repoPath = "${this.steps.env.WORKSPACE}/${REPOS_BASE_DIR}/${repo.id}"
                loadPipelineConfig(repoPath, repo)
                if (this.project.isAssembleMode) {
                    if (this.project.forceGlobalRebuild) {
                        this.logger.debug('Project forces global rebuild ...')
                    } else {
                        this.steps.dir(repoPath) {
                            this.logger.startClocked("${repo.id}-resurrect-data")
                            this.logger.debug('Checking if repo can be resurrected from previous build ...')
                            amendRepoForResurrectionIfEligible(repo)
                            this.logger.debugClocked("${repo.id}-resurrect-data")
                        }
                    }
                }
            }
        ]
    }

    def checkoutTagInRepoDir(Map repo, String tag) {
        this.logger.info("Checkout tag ${repo.id}@${tag}")
        def credentialsId = this.project.services.bitbucket.credentials.id
        git.checkout(
            "refs/tags/${tag}",
            [[ $class: 'RelativeTargetDirectory', relativeTargetDir: "${REPOS_BASE_DIR}/${repo.id}" ]],
            [[ credentialsId: credentialsId, url: repo.url ]]
        )
    }

    def checkoutBranchInRepoDir(Map repo, String branch) {
        this.logger.info("Checkout branch ${repo.id}@${branch}")
        def credentialsId = this.project.services.bitbucket.credentials.id
        git.checkout(
            "*/${branch}",
            [
                [ $class: 'RelativeTargetDirectory', relativeTargetDir: "${REPOS_BASE_DIR}/${repo.id}" ],
                [ $class: 'LocalBranch', localBranch: "**" ]
            ],
            [[ credentialsId: credentialsId, url: repo.url ]]
        )
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
                        if (this.project.isAssembleMode && name == PipelinePhases.BUILD) {
                            executeODSComponent(repo, baseDir, false)
                        } else if (this.project.isPromotionMode && name == PipelinePhases.DEPLOY) {
                            new DeployOdsComponent(project, steps, git, logger).run(repo, baseDir)
                        } else if (this.project.isAssembleMode && name == PipelinePhases.FINALIZE) {
                            new FinalizeOdsComponent(project, steps, git, logger).run(repo, baseDir)
                        } else {
                            this.logger.debug("Repo '${repo.id}' is of type ODS Code Component. Nothing to do in phase '${name}' for target environment '${targetEnvToken}'.")
                        }
                    } else if (repo.type?.toLowerCase() == PipelineConfig.REPO_TYPE_ODS_SERVICE) {
                        if (this.project.isAssembleMode && name == PipelinePhases.BUILD) {
                            executeODSComponent(repo, baseDir, false)
                        } else if (this.project.isPromotionMode && name == PipelinePhases.DEPLOY) {
                            new DeployOdsComponent(project, steps, git, logger).run(repo, baseDir)
                        } else if (this.project.isAssembleMode && PipelinePhases.FINALIZE) {
                            new FinalizeOdsComponent(project, steps, git, logger).run(repo, baseDir)
                        } else {
                            this.logger.debug("Repo '${repo.id}' is of type ODS Service Component. Nothing to do in phase '${name}' for target environment '${targetEnvToken}'.")
                        }
                    } else if (repo.type?.toLowerCase() == PipelineConfig.REPO_TYPE_ODS_TEST) {
                        if (name == PipelinePhases.TEST) {
                            executeODSComponent(repo, baseDir)
                        } else {
                            this.logger.debug("Repo '${repo.id}' is of type ODS Test Component. Nothing to do in phase '${name}' for target environment '${targetEnvToken}'.")
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
                            this.logger.debug("Repo '${repo.id}' is of type '${repo.type}'. Nothing to do in phase '${name}' for target environment '${targetEnvToken}'.")
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

    void warnBuildAboutUnexecutedJiraTests(List unexecutedJiraTests) {
        this.project.setHasUnexecutedJiraTests(true)
        def unexecutedJiraTestKeys = unexecutedJiraTests.collect { it.key }.join(", ")
        this.warnBuild("Found unexecuted Jira tests: ${unexecutedJiraTestKeys}.")
    }

    void warnBuildIfTestResultsContainFailure(Map testResults) {
        if (testResults.testsuites.find { (it.errors && it.errors.toInteger() > 0) || (it.failures && it.failures.toInteger() > 0) }) {
            this.project.setHasFailingTests(true)
            this.warnBuild('Found failing tests in test reports.')
        }
    }

    private void walkRepoDirectories(List<Map> repos, Closure visitor) {
        repos.each { repo ->
            // Apply the visitor to the repo at the repo's base dir
            visitor("${this.steps.env.WORKSPACE}/${REPOS_BASE_DIR}/${repo.id}", repo)
        }
    }

    private boolean isRepoModified(Map repo) {
        if (!repo.data.envStateCommit) {
            logger.debug("Last recorded commit of '${repo.id}' cannot be retrieved.")
            return true // Treat no recorded commit as being modified
        }
        def currentCommit = git.commitSha
        logger.debug(
            "Last recorded commit of '${repo.id}' in '${project.targetProject}': " +
            "${repo.data.envStateCommit}, current commit: ${currentCommit}"
        )
        currentCommit != repo.data.envStateCommit
    }

    private void amendRepoForResurrectionIfEligible(Map repo) {
        def os = ServiceRegistry.instance.get(OpenShiftService)

        if (repo.type?.toLowerCase() != PipelineConfig.REPO_TYPE_ODS_CODE) {
            logger.info(
                "Resurrection of previous build for '${repo.id}' not possible as " +
                "type '${repo.type}' is not eligible."
            )
            return
        }

        if (repo.forceRebuild) {
            logger.info(
                "Resurrection of previous build for '${repo.id}' not possible as " +
                "repo '${repo.id}' is forced to rebuild."
            )
            return
        }

        def openshiftDir = 'openshift-exported'
        if (steps.fileExists('openshift')) {
            openshiftDir = 'openshift'
        }
        if (!steps.fileExists("${openshiftDir}/${DeploymentDescriptor.FILE_NAME}")) {
            logger.info(
                "Resurrection of previous build for '${repo.id}' not possible as file " +
                "${openshiftDir}/${DeploymentDescriptor.FILE_NAME} does not exist."
            )
            return
        }

        if (isRepoModified(repo)) {
            logger.info(
                "Resurrection of previous build for '${repo.id}' not possible as " +
                "files have been modified."
            )
            return
        }

        DeploymentDescriptor deploymentDescriptor
        steps.dir(openshiftDir) {
            deploymentDescriptor = DeploymentDescriptor.readFromFile(steps)
        }

        if (!deploymentDescriptor.buildValidForVersion(project.buildParams.version)) {
            logger.info(
                "Resurrection of previous build for '${repo.id}' not possible as file " +
                "${openshiftDir}/${DeploymentDescriptor.FILE_NAME} contains invalid build information " +
                "for version ${project.buildParams.version}."
            )
            return
        }

        // TODO: If templates are exported, then we could check if a fresh export
        // has the same hash as the one in the repository. This would prevent
        // resurrecting a component which has changed in the OpenShift UI.
        // However, doing that is tricky as it would need to be executed from
        // an agent node with Tailor pre-installed. We should do this
        // once the whole orchestration pipeline runs on an agent node.
        // For now, we're not doing the check and simply re-export in the
        // finalize stage.

        def numDeployments = deploymentDescriptor.deployments.size()
        logger.info(
            "Checking if image SHAs for '${repo.id}' of ${numDeployments} deployment(s) are up-to-date ..."
        )
        Map deployments
        try {
            deployments = os.getPodDataForDeployments(
                project.targetProject,
                OpenShiftService.DEPLOYMENTCONFIG_KIND,
                deploymentDescriptor.deploymentNames
            )
        } catch(ex) {
            logger.info(
                "Resurrection of previous build for '${repo.id}' not possible as " +
                "not all deployments could be retrieved: ${ex.message}"
            )
            return
        }

        for (def deploymentName in deploymentDescriptor.deployments.keySet()) {
            if (!deployments[deploymentName]) {
                logger.info(
                    "Resurrection of previous build for '${repo.id}' not possible as " +
                    "deployment '${deploymentName}' was not found in OpenShift."
                )
                return
            }
            if (!os.areImageShasUpToDate(deploymentDescriptor.deployments[deploymentName], deployments[deploymentName])) {
                logger.info(
                    "Resurrection of previous build for '${repo.id}' not possible as " +
                    "current image SHAs of '${deploymentName}' do not match latest committed state."
                )
                return
            }
        }

        logger.info("Resurrection of previous build for '${repo.id}' possible.")
        repo.data.openshift.resurrectedBuild = deploymentDescriptor.createdByBuild
        repo.data.openshift.deployments = deployments
        logger.debug("Data from previous Jenkins build:\r${repo.data.openshift}")
    }
}
