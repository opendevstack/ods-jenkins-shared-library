package org.ods.orchestration

import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.service.DocGenService
import org.ods.orchestration.service.JiraService
import org.ods.orchestration.service.JiraZephyrService
import org.ods.orchestration.service.LeVADocumentChaptersFileService
import org.ods.orchestration.usecase.BitbucketTraceabilityUseCase
import org.ods.orchestration.usecase.ComponentMismatchException
import org.ods.orchestration.usecase.JUnitTestReportsUseCase
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.usecase.JiraUseCaseSupport
import org.ods.orchestration.usecase.JiraUseCaseZephyrSupport
import org.ods.orchestration.usecase.LeVADocumentUseCase
import org.ods.orchestration.usecase.OpenIssuesException
import org.ods.orchestration.usecase.SonarQubeUseCase
import org.ods.orchestration.util.GitTag
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PDFUtil
import org.ods.orchestration.util.PipelinePhaseLifecycleStage
import org.ods.orchestration.util.Project
import org.ods.services.BitbucketService
import org.ods.services.GitService
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.GitCredentialStore
import org.ods.util.ILogger
import org.ods.util.Logger
import org.ods.util.PipelineSteps

@SuppressWarnings('AbcMetric')
class InitStage extends Stage {

    public final String STAGE_NAME = 'Init'

    InitStage(def script, Project project, List<Set<Map>> repos, String startAgentStageName) {
        super(script, project, repos, startAgentStageName)
    }

    @SuppressWarnings(['CyclomaticComplexity', 'NestedBlockDepth', 'GStringAsMapKey', 'LineLength', 'Indentation'])
    def run() {
        ILogger logger = ServiceRegistry.instance.get(Logger)
        def steps = new PipelineSteps(script)
        def git = new GitService(steps, logger)

        // load build params
        def buildParams = Project.loadBuildParams(steps)
        logger.debug("Release Manager Build Parameters: ${buildParams}")

        // Read the environment state on main branch. We do not want to read
        // the state file on a release branch, as that might be stale (e.g. due
        // to concurrent releases).
        def envState = loadEnvState(logger, buildParams.targetEnvironment)

        def hasErrorsDuringCheckout = false
        def checkOutException = null
        try {
            checkOutReleaseManagerRepository(buildParams, git, logger)
        } catch (Exception e) {
            checkOutException = new RuntimeException("Error during the checkout of the release manager repository", e)
            hasErrorsDuringCheckout = true
        }

        logger.debug 'Load build params and metadata file information'
        project.init()

        logger.debug'Register global services'
        def registry = ServiceRegistry.instance
        addServicesToRegistry(registry, git, steps, logger)

        if (hasErrorsDuringCheckout) {
            //throw an exception after project init
            throw new RuntimeException(checkOutException.message, checkOutException)
        }

        logger.debug 'Checkout repositories into the workspace'
        BitbucketService bitbucket = registry.get(BitbucketService)
        configureGit(git, steps, bitbucket)
        def phase = MROPipelineUtil.PipelinePhases.INIT
        project.initGitDataAndJiraUsecase(registry.get(GitService), registry.get(JiraUseCase))
        logger.debugClocked('Project#load')
        project.load(registry.get(GitService), registry.get(JiraUseCase))
        logger.debugClocked('Project#load')
        MROPipelineUtil util = registry.get(MROPipelineUtil)

        def check = project.getComponentsFromJira()
        if (check) {
            logger.info("Comparing Jira components against metadata.yml repositories")
            if (check.deployableState != 'DEPLOYABLE') {
                throw new ComponentMismatchException(check.wikiMessage)
            }
            logger.info("Jira components found: $check")

            for (component in check.components) {
                logger.info("Component: $component")
                if (!project.repositories.any { it -> component.name == (it.containsKey('name') ? it.name : it.id) }) {
                    project.addDefaults(component.name)
                    logger.info("Repository added: $component.name")
                }
            }
        }
        def repos = project.repositories
        logger.debug("Printing repositories")
        logger.debug("$repos")

        Closure checkoutClosure = buildCheckOutClousure(repos, logger, envState, util)
        Closure<String> loadClosure = buildLoadClousure(logger, registry, buildParams, git, steps)
        try {
            executeInParallel(checkoutClosure, loadClosure)
            script.parallel (
                repos.collectEntries { repo ->
                    logger.debug("-< Initing Repository: ${repo.id}")
                    // we allow init hooks for special component types
                    util.prepareExecutePhaseForRepoNamedJob(phase, repo)
                }
            )
        } catch (OpenIssuesException openDocumentsException) {
            util.warnBuild(openDocumentsException.message)
            throw openDocumentsException
        }

        if (project.isPromotionMode && !project.buildParams.rePromote) {
            checkReposContainEnvCommits(repos, steps, git, logger, util)
        }

        String stageToStartAgent = findBestPlaceToStartAgent(repos, logger)

        // Compute target project. For now, the existance of DEV on the same cluster is verified.
        def concreteEnv = Project.getConcreteEnvironment(
            project.buildParams.targetEnvironment,
            project.buildParams.version.toString(),
            project.versionedDevEnvsEnabled
        )
        def targetProject = "${project.key}-${concreteEnv}"
        def os = registry.get(OpenShiftService)
        if (project.buildParams.targetEnvironment == 'dev' && !os.envExists(targetProject)) {
            throw new RuntimeException(
                "Target project ${targetProject} does not exist " +
                    "(versionedDevEnvsEnabled=${project.versionedDevEnvsEnabled})."
            )
        }
        project.setTargetProject(targetProject)

        logger.debug 'Compute groups of repository configs for convenient parallelization'
        repos = util.computeRepoGroups(repos)
        registry.get(LeVADocumentScheduler).run(phase, PipelinePhaseLifecycleStage.PRE_END)

        return [project: project, repos: repos, startAgent: stageToStartAgent]
    }

    private String findBestPlaceToStartAgent(List<Map> repos, ILogger logger) {
        String stageToStartAgent
        repos.each { repo ->
            if (repo.type == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST) {
                stageToStartAgent = MROPipelineUtil.PipelinePhases.TEST
            } else if (repo.type == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE &&
                !repo.data.openshift.resurrectedBuild) {
                if (stageToStartAgent != MROPipelineUtil.PipelinePhases.TEST) {
                    stageToStartAgent = MROPipelineUtil.PipelinePhases.BUILD
                }
            }
        }
        if (!stageToStartAgent) {
            logger.info "No applicable stage found - agent bootstrap will run during 'deploy'.\r" +
                "To change this to 'init', change 'startOrchestrationAgentOnInit' in JenkinsFile to 'true'"
            stageToStartAgent = MROPipelineUtil.PipelinePhases.DEPLOY
        }
        return stageToStartAgent
    }

    private Closure buildCheckOutClousure(repos, logger, envState, MROPipelineUtil util) {
        @SuppressWarnings('Indentation')
        Closure checkoutClosure =
            {
                script.parallel(
                    repos.collectEntries { repo ->
                        logger.info("Loading Repository: ${repo}")
                        if (envState?.repositories) {
                            repo.data.envStateCommit = envState.repositories[repo.id] ?: ''
                        }
                        util.prepareCheckoutRepoNamedJob(repo)
                    }
                )
            }
        return checkoutClosure
    }

    // In promotion mode, we need to check if the checked out repos are on commits
    // which "contain" the commits defined in the env state.
    private void checkReposContainEnvCommits(List<Map> repos,
                                             steps,
                                             git,
                                             logger,
                                             util) {
        repos.each { repo ->
            if (repo.include) {
                steps.dir("${steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/${repo.id}") {
                    if (repo.data.envStateCommit) {
                        if (git.isAncestor(repo.data.envStateCommit, repo.data.git.commit)) {
                            logger.info(
                                "Verified that ${repo.id}@${repo.data.git.commit} is " +
                                    "a descendant of ${repo.data.envStateCommit}."
                            )
                        } else if (project.buildParams.targetEnvironmentToken == 'Q') {
                            util.warnBuild(
                                "${repo.id}@${repo.data.git.commit} is NOT a descendant of " +
                                    "${repo.data.envStateCommit}, which has previously been promoted to 'Q'. " +
                                    "If ${repo.data.envStateCommit} has been promoted to 'P' as well, " +
                                    "promotion to 'P' will fail. Proceed with caution."
                            )
                        } else {
                            throw new RuntimeException(
                                "${repo.id}@${repo.data.git.commit} is NOT a descendant of " +
                                    "${repo.data.envStateCommit}, which has previously been promoted to 'P'. " +
                                    "Ensure to merge everything that has been promoted to 'P' " +
                                    "into ${project.gitReleaseBranch}."
                            )
                        }
                    } else {
                        logger.info(
                            "Repo ${repo.id} is not recorded in env state, skipping commit ancestor verification."
                        )
                    }
                }
            }
        }
    }

    private void addServicesToRegistry(ServiceRegistry registry, GitService git, PipelineSteps steps, Logger logger) {
        registry.add(GitService, git)
        registry.add(PDFUtil, new PDFUtil())
        registry.add(PipelineSteps, steps)
        def util = new MROPipelineUtil(project, steps, git, logger)
        registry.add(MROPipelineUtil, util)
        registry.add(Project, project)

        def docGenUrl = script.env.DOCGEN_URL ?: "http://docgen.${project.key}-cd.svc:8080"
        registry.add(DocGenService,
            new DocGenService(docGenUrl)
        )
        registry.add(LeVADocumentChaptersFileService,
            new LeVADocumentChaptersFileService(steps)
        )
        registry.add(JenkinsService,
            new JenkinsService(
                steps,
                logger
            )
        )
        registry.add(LeVADocumentChaptersFileService,
            new LeVADocumentChaptersFileService(steps)
        )

        if (project.services?.jira) {
            addJiraToRegistry(registry)
        }

        registry.add(NexusService, NexusService.newFromEnv(script.env, steps,
            project.services.bitbucket.credentials.id))
        registry.add(OpenShiftService,
            new OpenShiftService(
                registry.get(PipelineSteps),
                logger
            )
        )
        addJiraUseCaseToRegistry(registry, logger, steps)
        registry.add(JUnitTestReportsUseCase,
            new JUnitTestReportsUseCase(
                registry.get(Project),
                registry.get(PipelineSteps)
            )
        )
        registry.add(SonarQubeUseCase,
            new SonarQubeUseCase(
                registry.get(Project),
                registry.get(PipelineSteps),
                registry.get(NexusService)
            )
        )
        addBitBucketToRegistry(steps, logger, registry)
        addLeVADocumentUseCaseToRegistry(registry, logger)
        registry.add(LeVADocumentScheduler,
            new LeVADocumentScheduler(
                registry.get(Project),
                registry.get(PipelineSteps),
                registry.get(MROPipelineUtil),
                registry.get(LeVADocumentUseCase),
                logger
            )
        )
    }

    private Closure<String> buildLoadClousure(Logger logger,
                                              ServiceRegistry registry,
                                              Map<String, Object> buildParams,
                                              GitService git,
                                              PipelineSteps steps) {
        BitbucketService bitbucket = registry.get(BitbucketService)
        Closure loadClosure = {
            logger.debug 'Validate that for Q and P we have a valid version'
            if (project.isPromotionMode && ['Q', 'P'].contains(project.buildParams.targetEnvironmentToken)
                && buildParams.version == 'WIP') {
                throw new RuntimeException(
                    'Error: trying to deploy to Q or P without having defined a correct version. ' +
                        "${buildParams.version} version value is not allowed for those environments. " +
                        'If you are using Jira, please check that all values are set in the release manager issue. ' +
                        "Build parameters obtained: ${buildParams}"
                )
            }

            if (project.isPromotionMode && git.remoteTagExists(project.targetTag)) {
                ['Q', 'P'].each {
                    if (project.buildParams.targetEnvironmentToken == it && project.buildParams.rePromote) {
                        logger.warn("Deploying tag '${project.targetTag}' to ${it} again!")
                    } else if (project.buildParams.targetEnvironmentToken == it) {
                        throw new RuntimeException(
                            "Git Tag '${project.targetTag}' already exists. " +
                                "It can only be deployed again to '${it}' if build param 'rePromote' is set to 'true'."
                        )
                    }
                }
            }
            if (!project.isWorkInProgress) {
                bitbucket.setBuildStatus(steps.env.BUILD_URL, project.gitData.commit,
                    'INPROGRESS', "Release Manager for commit: ${project.gitData.commit}")
            }
            def jobMode = project.isPromotionMode ? '(promote)' : '(assemble)'

            logger.debug 'Configure current build description'
            script.currentBuild.description = "Build ${jobMode} #${script.env.BUILD_NUMBER} - " +
                "Change: ${script.env.RELEASE_PARAM_CHANGE_ID}, " +
                "Project: ${project.key}, " +
                "Target Environment: ${project.key}-${script.env.MULTI_REPO_ENV}, " +
                "Version: ${script.env.VERSION}"

            def projectNexusKey = "${project.getKey()}-${project.buildParams.version}"
            def nexusRepoExists = registry.get(NexusService).groupExists(
                project.services.nexus.repository.name, projectNexusKey)
            project.addConfigSetting(NexusService.NEXUS_REPO_EXISTS_KEY, nexusRepoExists)
            logger.debug("Nexus repository for project/version '${projectNexusKey}'" +
                " exists? ${nexusRepoExists}")

            def os = registry.get(OpenShiftService)
            project.setOpenShiftData(os.apiUrl)
            logger.debug("Agent start stage: ${this.startAgentStageName}")
        }
        return loadClosure
    }

    private void configureGit(GitService git, PipelineSteps steps, BitbucketService bitbucket) {
        git.configureUser()
        steps.withCredentials(
            [steps.usernamePassword(
                credentialsId: bitbucket.passwordCredentialsId,
                usernameVariable: 'BITBUCKET_USER',
                passwordVariable: 'BITBUCKET_PW'
            )]
        ) {
            GitCredentialStore.configureAndStore(
                steps,
                bitbucket.url as String,
                steps.env.BITBUCKET_USER as String,
                steps.env.BITBUCKET_PW as String)
        }
    }

    private addLeVADocumentUseCaseToRegistry(ServiceRegistry registry, Logger logger) {
        registry.add(LeVADocumentUseCase,
            new LeVADocumentUseCase(
                registry.get(Project),
                registry.get(PipelineSteps),
                registry.get(MROPipelineUtil),
                registry.get(DocGenService),
                registry.get(JenkinsService),
                registry.get(JiraUseCase),
                registry.get(JUnitTestReportsUseCase),
                registry.get(LeVADocumentChaptersFileService),
                registry.get(NexusService),
                registry.get(OpenShiftService),
                registry.get(PDFUtil),
                registry.get(SonarQubeUseCase),
                registry.get(BitbucketTraceabilityUseCase),
                logger
            )
        )
    }

    private BitbucketService addBitBucketToRegistry(PipelineSteps steps, Logger logger, ServiceRegistry registry) {
        def bitbucket = BitbucketService.newFromEnv(
            steps.unwrap(),
            steps.env,
            project.key,
            project.services.bitbucket.credentials.id,
            logger
        )
        registry.add(BitbucketService, bitbucket)

        registry.add(BitbucketTraceabilityUseCase,
            new BitbucketTraceabilityUseCase(
                registry.get(BitbucketService),
                registry.get(PipelineSteps),
                registry.get(Project)
            )
        )
        bitbucket
    }

    private void addJiraUseCaseToRegistry(ServiceRegistry registry, Logger logger, PipelineSteps steps) {
        def jiraUseCase = new JiraUseCase(
            registry.get(Project),
            registry.get(PipelineSteps),
            registry.get(MROPipelineUtil),
            registry.get(JiraService),
            logger
        )

        if (project.hasCapability('Zephyr')) {
            jiraUseCase.setSupport(
                new JiraUseCaseZephyrSupport(
                    project,
                    steps,
                    jiraUseCase,
                    registry.get(JiraZephyrService),
                    registry.get(MROPipelineUtil)
                )
            )
        } else {
            jiraUseCase.setSupport(
                new JiraUseCaseSupport(project, steps, jiraUseCase)
            )
        }

        registry.add(JiraUseCase, jiraUseCase)
    }

    private void addJiraToRegistry(registry) {
        script.withCredentials(
            [script.usernamePassword(
                credentialsId: project.services.jira.credentials.id,
                usernameVariable: 'JIRA_USERNAME',
                passwordVariable: 'JIRA_PASSWORD'
            )]
        ) {
            registry.add(JiraService,
                new JiraService(
                    script.env.JIRA_URL,
                    script.env.JIRA_USERNAME,
                    script.env.JIRA_PASSWORD
                )
            )

            if (project.hasCapability('Zephyr')) {
                registry.add(JiraZephyrService,
                    new JiraZephyrService(
                        script.env.JIRA_URL,
                        script.env.JIRA_USERNAME,
                        script.env.JIRA_PASSWORD
                    )
                )
            }
        }
    }

    private void checkOutReleaseManagerRepository(def buildParams, def git,
                                                  ILogger logger) {
        logger.startClocked("git-releasemanager-${STAGE_NAME}")
        if (!Project.isWorkInProgress(buildParams.version)) {
            def gitReleaseBranch = GitService.getReleaseBranch(buildParams.version)
            logger.debug("Release Manager branch to checkout: ${gitReleaseBranch}")
            if (Project.isPromotionMode(buildParams.targetEnvironmentToken)) {
                checkOutRepoInPromotionMode(git, buildParams, gitReleaseBranch, logger)
            } else {
                checkOutRepoInNotPromotionMode(git, gitReleaseBranch, false, logger)
            }
        } else {
            // Here is the difference with respect to deploy-to-D:
            // The branch to be used is obtained from buildParams.changeId, not from buildParams.version ( = WIP ).
            def gitReleaseBranch = GitService.getReleaseBranch(buildParams.changeId)
            logger.info("Release branch that should be used if available: ${gitReleaseBranch}")
            checkOutRepoInNotPromotionMode(git, gitReleaseBranch, true, logger)
        }
        logger.debugClocked("git-releasemanager-${STAGE_NAME}")
    }

    private void checkOutRepoInNotPromotionMode(GitService git,
                                                String gitReleaseBranch,
                                                boolean isWorkInProgress,
                                                Logger logger) {
        if (git.remoteBranchExists(gitReleaseBranch)) {
            logger.info("Checkout release manager repository branch ${gitReleaseBranch}")
            git.checkout(
                "*/${gitReleaseBranch}",
                [[$class: 'LocalBranch', localBranch: gitReleaseBranch]],
                script.scm.userRemoteConfigs
            )
            project.setGitReleaseBranch(gitReleaseBranch)
        } else {
            // If we are still in WIP and there is no branch for current release,
            // do not create it. We use if only if it exists. We use master if it does not exist.
            if (! isWorkInProgress) {
                logger.info("Creating release manager repository branch: ${gitReleaseBranch}")
                git.checkoutNewLocalBranch(gitReleaseBranch)
                project.setGitReleaseBranch(gitReleaseBranch)
            } else {
                logger.info("Since no deploy was done to D (branch ${gitReleaseBranch} does not exist), " +
                    "using master branch for developer preview.")
                project.setGitReleaseBranch("master")
            }
        }
    }

    private Exception checkOutRepoInPromotionMode(GitService git,
                                             Map buildParams,
                                             String gitReleaseBranch,
                                             Logger logger) {
        def tagList = git.readBaseTagList(
            buildParams.version,
            buildParams.changeId,
            buildParams.targetEnvironmentToken
        )
        def baseTag = GitTag.readLatestBaseTag(
            tagList,
            buildParams.version,
            buildParams.changeId,
            buildParams.targetEnvironmentToken
        )?.toString()
        if (!baseTag) {
            throw new RuntimeException(
                "Error: unable to find latest tag for version ${buildParams.version}/${buildParams.changeId}."
            )
        }
        logger.info("Checkout release manager repository @ ${baseTag}")
        git.checkout(
            "refs/tags/${baseTag}",
            [[$class: 'LocalBranch', localBranch: gitReleaseBranch]],
            script.scm.userRemoteConfigs
        )
    }

    private Map loadEnvState(ILogger logger, String targetEnvironment) {
        def envStateFile = "${Project.envStateFileName(targetEnvironment)}"
        logger.info "Load env state from ${envStateFile}"
        script.sh("mkdir -p ${MROPipelineUtil.ODS_STATE_DIR}")
        if (!script.fileExists(envStateFile)) {
            logger.debug "No env state ${envStateFile} found, initializing ..."
            return [:]
        }
        logger.debug "Env state ${envStateFile} found"
        script.readJSON(file: envStateFile)
    }

}
