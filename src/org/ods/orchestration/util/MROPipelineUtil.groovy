package org.ods.orchestration.util

@Grab('org.yaml:snakeyaml:1.24')

import groovy.transform.InheritConstructors

import java.nio.file.Paths

import org.ods.orchestration.dependency.DependencyGraph
import org.ods.orchestration.dependency.Node
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.services.BitbucketService
import org.ods.orchestration.phases.DeployOdsComponent
import org.ods.orchestration.phases.FinalizeOdsComponent
import org.ods.orchestration.phases.FinalizeNonOdsComponent
import org.yaml.snakeyaml.Yaml

@InheritConstructors
@SuppressWarnings(['LineLength', 'AbcMetric', 'NestedBlockDepth', 'EmptyElseBlock', 'CyclomaticComplexity', 'GStringAsMapKey', 'UseCollectNested'])
class MROPipelineUtil extends PipelineUtil {

    class PipelineConfig {
        // TODO: deprecate .pipeline-config.yml in favor of release-manager.yml
        static final List FILE_NAMES = ["release-manager.yml", ".pipeline-config.yml"]

        static final String REPO_TYPE_ODS_CODE = "ods"
        static final String REPO_TYPE_ODS_INFRA = "ods-infra"
        static final String REPO_TYPE_ODS_SAAS_SERVICE = "ods-saas-service"
        static final String REPO_TYPE_ODS_SERVICE = "ods-service"
        static final String REPO_TYPE_ODS_TEST = "ods-test"
        static final String REPO_TYPE_ODS_LIB = "ods-library"

        static final String PHASE_EXECUTOR_TYPE_MAKEFILE = "Makefile"
        static final String PHASE_EXECUTOR_TYPE_SHELLSCRIPT = "ShellScript"

        static final List PHASE_EXECUTOR_TYPES = [
            PHASE_EXECUTOR_TYPE_MAKEFILE,
            PHASE_EXECUTOR_TYPE_SHELLSCRIPT
        ]

        static final List<String> INSTALLABLE_REPO_TYPES = [
            REPO_TYPE_ODS_CODE as String,
            REPO_TYPE_ODS_SERVICE as String,
            REPO_TYPE_ODS_INFRA as String
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

    private void executeODSComponent(Map repo, String baseDir, boolean failfast = true,
        String jenkinsFile = 'Jenkinsfile') {
        this.steps.dir(baseDir) {
            if (repo.data.openshift.resurrectedBuild) {
                logger.info("Repository '${repo.id}' is in sync with OpenShift, no need to rebuild")
                return
            }
            def job
            def env = []
            env.addAll(this.project.getMainReleaseManagerEnv())
            this.project.buildParams.each { key, value ->
                env << "BUILD_PARAM_${key.toUpperCase()}=${value}"
            }
            env << "NOTIFY_BB_BUILD=${!project.isWorkInProgress}"
            this.steps.withEnv (env) {
                job = this.loadGroovySourceFile("${baseDir}/${jenkinsFile}")
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

    Map<String, Closure> prepareCheckoutRepoNamedJob(Map repo, boolean recheckout = false) {
        return [
            repo.id,
            {
                checkoutNotReleaseManagerRepo(repo, recheckout)
            }
        ]
    }

    void checkoutNotReleaseManagerRepo(Map repo, boolean recheckout = false) {
        this.logger.startClocked("${repo.id}-scm-checkout")
        def scm = null
        def scmBranch = ""
        if (this.project.isPromotionMode && repo.include) {
            this.logger.info("Since in promotion mode, checking out tag ${this.project.baseTag}")
            scm = checkoutTagInRepoDir(repo, this.project.baseTag)
            scmBranch = this.project.gitReleaseBranch
        } else {
            Map scmResult = checkOutNotReleaseManagerRepoInNotPromotionMode(repo, this.project.isWorkInProgress)
            scm = scmResult.scm
            scmBranch = scmResult.scmBranch
        }
        this.logger.debugClocked("${repo.id}-scm-checkout")

        // in case of a re-checkout, scm.GIT_COMMIT  still points
        // to the old commit.
        def commit = scm.GIT_COMMIT
        def prevCommit = scm.GIT_PREVIOUS_COMMIT
        def lastSuccessCommit =  scm.GIT_PREVIOUS_SUCCESSFUL_COMMIT
        if (recheckout) {
            steps.dir("${REPOS_BASE_DIR}/${repo.id}") {
                commit = git.getCommitSha()
                prevCommit = scm.GIT_COMMIT
                lastSuccessCommit = scm.GIT_COMMIT
            }
        }

        repo.data.git = [
            branch: scmBranch,
            commit: commit,
            previousCommit: prevCommit,
            previousSucessfulCommit: lastSuccessCommit,
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

    private Map checkOutNotReleaseManagerRepoInNotPromotionMode(Map repo, boolean isWorkInProgress) {
        Map scmResult = [ : ]
        def bbs = ServiceRegistry.instance.get(BitbucketService)
        String gitReleaseBranch = this.project.gitReleaseBranch
        repo.defaultBranch = bbs.getDefaultBranch(repo.id)
        if ("master" == gitReleaseBranch) {
            gitReleaseBranch = repo.defaultBranch
        }
        if(isWorkInProgress && repo.'preview-branch') {
            this.logger.info("Since in WIP and preview-branch has been configured, using preview-branch: ${repo.'preview-branch'}")
            gitReleaseBranch = repo.'preview-branch'
        }

        // check if release manager repo already has a release branch
        if (git.remoteBranchExists(gitReleaseBranch)) {
            try {
                scmResult.scm = checkoutBranchInRepoDir(repo, gitReleaseBranch)
                scmResult.scmBranch = gitReleaseBranch
            } catch (ex) {
                if (! isWorkInProgress) {
                    this.logger.warn """
                                Checkout of '${gitReleaseBranch}' for repo '${repo.id}' failed.
                                Attempting to checkout '${repo.defaultBranch}' and create the release branch from it.
                                """
                    // Possible reasons why this might happen:
                    // * Release branch manually created in RM repo
                    // * Repo is added to metadata.yml file on a release branch
                    // * Release branch has been deleted in repo

                    scmResult.scm = createBranchFromDefaultBranch(repo, gitReleaseBranch)
                    scmResult.scmBranch = gitReleaseBranch
                } else {
                    this.logger.warn """
                                Checkout of '${gitReleaseBranch}' for repo '${repo.id}' failed.
                                Attempting to checkout branch '${repo.defaultBranch}'.
                                """
                    scmResult.scm = checkoutBranchInRepoDir(repo, repo.defaultBranch)
                    scmResult.scmBranch = repo.defaultBranch
                }
            }
        } else {
            if (! isWorkInProgress) {
                scmResult.scm = createBranchFromDefaultBranch(repo, gitReleaseBranch)
                scmResult.scmBranch = gitReleaseBranch
            } else {
                this.logger.info("Since in WIP and no release branch exists (${this.project.gitReleaseBranch})" +
                    "${repo.'preview-branch' ? ' and preview-branch has been configured' : ''}, " +
                    "checking out branch ${repo.'preview-branch' ? repo.'preview-branch' : repo.defaultBranch} for repo ${repo.id}")
                scmResult.scm = checkoutBranchInRepoDir(repo, repo.'preview-branch' ? repo.'preview-branch' : repo.defaultBranch)
                scmResult.scmBranch = repo.'preview-branch' ? repo.'preview-branch' : repo.defaultBranch
            }
        }
        return scmResult
    }

    private def createBranchFromDefaultBranch(Map repo, String branchName) {
        this.logger.info("Creating branch ${branchName} from branch ${repo.defaultBranch} for repo ${repo.id} ")
        def scm = checkoutBranchInRepoDir(repo, repo.defaultBranch)
        if (repo.defaultBranch != branchName) {
            steps.dir("${REPOS_BASE_DIR}/${repo.id}") {
                git.checkoutNewLocalBranch(branchName)
            }
        } else {
            this.logger.info("No need to create branch ${branchName} for repo ${repo.id} ")
        }
        return scm
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
                [ $class: 'LocalBranch', localBranch: "**" ],
            ],
            [[ credentialsId: credentialsId, url: repo.url ]]
        )
    }

    Map.Entry<String, Closure> prepareExecutePhaseForRepoNamedJob(String name, Map repo, Closure preExecute = null, Closure postExecute = null) {
        //noinspection GroovyAssignabilityCheck
        return [
            repo.id,
            {
                this.executeBlockAndFailBuild {
                    def baseDir = "${this.steps.env.WORKSPACE}/${REPOS_BASE_DIR}/${repo.id}"
                    def targetEnvToken = this.project.buildParams.targetEnvironmentToken

                    if (preExecute) {
                        preExecute(this.steps, repo)
                    }
                    if (repo.include) {
                        repo.doInstall = PipelineConfig.INSTALLABLE_REPO_TYPES.contains(repo.type)
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
                        } else if (repo.type?.toLowerCase() == PipelineConfig.REPO_TYPE_ODS_INFRA) {
                            if (this.project.isAssembleMode && name == PipelinePhases.BUILD) {
                                executeODSComponent(repo, baseDir)
                            } else if (this.project.isPromotionMode && name == PipelinePhases.BUILD) {
                                executeODSComponent(repo, baseDir)
                            } else if (this.project.isAssembleMode && name == PipelinePhases.FINALIZE) {
                                new FinalizeNonOdsComponent(project, steps, git, logger).run(repo, baseDir)
                            } else {
                                this.logger.debug("Repo '${repo.id}' is of type ODS Infrastructure as Code Component/Configuration Management. Nothing to do in phase '${name}' for target environment'${targetEnvToken}'.")
                            }
                        } else if (repo.type?.toLowerCase() == PipelineConfig.REPO_TYPE_ODS_LIB) {
                            if (this.project.isAssembleMode && name == PipelinePhases.BUILD) {
                                executeODSComponent(repo, baseDir)
                            } else if (this.project.isAssembleMode && name == PipelinePhases.FINALIZE) {
                                new FinalizeNonOdsComponent(project, steps, git, logger).run(repo, baseDir)
                            } else {
                                this.logger.debug("Repo '${repo.id}' is of type ODS library. Nothing to do in phase '${name}' for target environment'${targetEnvToken}'.")
                            }
                        } else if (repo.type?.toLowerCase() == PipelineConfig.REPO_TYPE_ODS_SAAS_SERVICE) {
                            this.logger.debug("Repo '${repo.id}' is of type ODS SaaS Service Component. Nothing to do in phase '${name}' for target environment'${targetEnvToken}'.")
                        } else if (repo.type?.toLowerCase() == PipelineConfig.REPO_TYPE_ODS_SERVICE) {
                            if (this.project.isAssembleMode && name == PipelinePhases.BUILD) {
                                executeODSComponent(repo, baseDir, false)
                            } else if (this.project.isPromotionMode && name == PipelinePhases.DEPLOY) {
                                new DeployOdsComponent(project, steps, git, logger).run(repo, baseDir)
                            } else if (this.project.isAssembleMode && name == PipelinePhases.FINALIZE) {
                                new FinalizeOdsComponent(project, steps, git, logger).run(repo, baseDir)
                            } else {
                                this.logger.debug("Repo '${repo.id}' is of type ODS Service Component. Nothing to do in phase '${name}' for target environment '${targetEnvToken}'.")
                            }
                        } else if (repo.type?.toLowerCase() == PipelineConfig.REPO_TYPE_ODS_TEST) {
                            if (this.project.isAssembleMode && name == PipelinePhases.INIT) {
                                this.logger.debug("Repo '${repo.id}' is of type ODS Test Component, init phase - configured hook: '${repo.pipelineConfig?.initJenkinsFile}'")
                                if (repo.pipelineConfig?.initJenkinsFile) {
                                    executeODSComponent(repo, baseDir, true, repo.pipelineConfig?.initJenkinsFile)
                                    // hacky - but the only way possible - we know it's only one.
                                    Closure checkout = prepareCheckoutRepoNamedJob(repo, true).get(1)
                                    checkout()
                                    this.logger.debug("Got new git data for ${repo.id}: ${repo.data.git}")
                                }
                            } else if (name == PipelinePhases.TEST) {
                                executeODSComponent(repo, baseDir)
                            } else if (this.project.isAssembleMode && name == PipelinePhases.FINALIZE) {
                                new FinalizeNonOdsComponent(project, steps, git, logger).run(repo, baseDir)
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
                    } else {
                        repo.doInstall = false
                        this.logger.debug("Repo '${repo.id}' is of type '${repo.type}'. Include flag is set to false so nothing to do in phase '${name}' for target environment '${targetEnvToken}'.")
                    }

                    if (postExecute) {
                        postExecute(this.steps, repo)
                    }
                }
            }
        ]
    }

    List<Map<String, Closure>> prepareExecutePhaseForReposNamedJob(String name, List<Set<Map>> repos, Closure preExecute = null, Closure postExecute = null) {
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
