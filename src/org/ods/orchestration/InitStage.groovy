package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.services.BitbucketService
import org.ods.services.GitService
import org.ods.services.OpenShiftService
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.service.*
import org.ods.orchestration.usecase.*
import org.ods.orchestration.util.Project
import org.ods.orchestration.util.PDFUtil
import org.ods.orchestration.util.GitTag
import org.ods.orchestration.util.MROPipelineUtil

import org.ods.util.GitCredentialStore
import org.ods.util.PipelineSteps
import org.ods.util.Logger
import org.ods.util.ILogger

@SuppressWarnings('AbcMetric')
class InitStage extends Stage {

    public final String STAGE_NAME = 'Init'

    InitStage(def script, Project project, List<Set<Map>> repos, String startAgentStageName) {
        super(script, project, repos, startAgentStageName)
    }

    @SuppressWarnings(['CyclomaticComplexity', 'NestedBlockDepth', 'GStringAsMapKey', 'LineLength'])
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

        logger.startClocked("git-releasemanager-${STAGE_NAME}")
        // git checkout
        def gitReleaseBranch = GitService.getReleaseBranch(buildParams.version)
        if (!Project.isWorkInProgress(buildParams.version)) {
            if (Project.isPromotionMode(buildParams.targetEnvironmentToken)) {
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
                checkoutGitRef(
                    "refs/tags/${baseTag}",
                    [[$class: 'LocalBranch', localBranch: gitReleaseBranch]]
                )
            } else {
                if (git.remoteBranchExists(gitReleaseBranch)) {
                    logger.info("Checkout release manager repository @ ${gitReleaseBranch}")
                    checkoutGitRef(
                        "*/${gitReleaseBranch}",
                        [[$class: 'LocalBranch', localBranch: gitReleaseBranch]]
                    )
                } else {
                    git.checkoutNewLocalBranch(gitReleaseBranch)
                }
            }
        }
        logger.debugClocked("git-releasemanager-${STAGE_NAME}")

        logger.debug 'Load build params and metadata file information'
        project.init()

        logger.debug'Register global services'
        def registry = ServiceRegistry.instance
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

        registry.add(NexusService, NexusService.newFromEnv(script.env))

        registry.add(OpenShiftService,
            new OpenShiftService(
                registry.get(PipelineSteps),
                logger
            )
        )

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
                registry.get(SonarQubeUseCase)
            )
        )

        registry.add(LeVADocumentScheduler,
            new LeVADocumentScheduler(
                registry.get(Project),
                registry.get(PipelineSteps),
                registry.get(MROPipelineUtil),
                registry.get(LeVADocumentUseCase),
                logger
            )
        )

        def bitbucket = BitbucketService.newFromEnv(
            steps.unwrap(),
            steps.env,
            project.key,
            project.services.bitbucket.credentials.id,
            logger
        )
        registry.add(BitbucketService, bitbucket)

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

        def phase = MROPipelineUtil.PipelinePhases.INIT

        logger.debug 'Checkout repositories into the workspace'
        project.initGitDataAndJiraUsecase(
            registry.get(GitService), registry.get(JiraUseCase))

        def repos = project.repositories
        @SuppressWarnings('Indentation')
        Closure checkoutClosure =
        {
            script.parallel (
                repos.collectEntries { repo ->
                    logger.info("Repository: ${repo}")
                    if (envState?.repositories) {
                        repo.data.envStateCommit = envState.repositories[repo.id] ?: ''
                    }
                    util.prepareCheckoutRepoNamedJob(repo)
                }
            )
        }

        Closure loadClosure = {
            logger.debugClocked('Project#load')
            project.load(registry.get(GitService), registry.get(JiraUseCase))
            logger.debugClocked('Project#load')

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
                if (project.buildParams.targetEnvironmentToken == 'Q' && project.buildParams.rePromote) {
                    logger.warn("Deploying tag '${project.targetTag}' to Q again!")
                } else if (project.buildParams.targetEnvironmentToken == 'Q') {
                    throw new RuntimeException(
                        "Git Tag '${project.targetTag}' already exists. " +
                        "It can only be deployed again to 'Q' if build param 'rePromote' is set to 'true'."
                    )
                } else {
                    throw new RuntimeException(
                        "Git Tag '${project.targetTag}' already exists. " +
                        "It cannot be deployed again to 'P'."
                    )
                }
            }
            if (!project.isWorkInProgress) {
                bitbucket.setBuildStatus (steps.env.BUILD_URL, project.gitData.commit,
                    'INPROGRESS', "Release Manager for commit: ${project.gitData.commit}")
            }
            def jobMode = project.isPromotionMode ? '(promote)' : '(assemble)'

            logger.debug 'Configure current build description'
            script.currentBuild.description = "Build ${jobMode} #${script.BUILD_NUMBER} - " +
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

        executeInParallel(checkoutClosure, loadClosure)

        // In promotion mode, we need to check if the checked out repos are on commits
        // which "contain" the commits defined in the env state.
        if (project.isPromotionMode && !project.buildParams.rePromote) {
            repos.each { repo ->
                steps.dir("${steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/${repo.id}") {
                    if (repo.data.envStateCommit) {
                        if (git.isAncestor(repo.data.envStateCommit, repo.data.git.commit)) {
                            logger.info(
                                "Verified that ${repo.id}@${repo.data.git.commit} is a descendant of ${repo.data.envStateCommit}."
                            )
                        } else if (project.buildParams.targetEnvironmentToken == 'Q') {
                            util.warnBuild(
                                "${repo.id}@${repo.data.git.commit} is NOT a descendant of ${repo.data.envStateCommit}, " +
                                "which has previously been promoted to 'Q'. If ${repo.data.envStateCommit} has been " +
                                "promoted to 'P' as well, promotion to 'P' will fail. Proceed with caution."
                            )
                        } else {
                            throw new RuntimeException(
                                "${repo.id}@${repo.data.git.commit} is NOT a descendant of ${repo.data.envStateCommit}, " +
                                "which has previously been promoted to 'P'. Ensure to merge everything that has been " +
                                "promoted to 'P' into ${project.gitReleaseBranch}."
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

        // find best place for agent start
        def stageToStartAgent
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
        def os = registry.get(OpenShiftService)

        // Compute target project. For now, the existance of DEV on the same cluster is verified.
        def concreteEnv = Project.getConcreteEnvironment(
            project.buildParams.targetEnvironment,
            project.buildParams.version.toString(),
            project.versionedDevEnvsEnabled
        )
        def targetProject = "${project.key}-${concreteEnv}"
        if (project.buildParams.targetEnvironment == 'dev' && !os.envExists(targetProject)) {
            throw new RuntimeException(
                "Target project ${targetProject} does not exist " +
                "(versionedDevEnvsEnabled=${project.versionedDevEnvsEnabled})."
            )
        }
        project.setTargetProject(targetProject)

        logger.debug 'Compute groups of repository configs for convenient parallelization'
        repos = util.computeRepoGroups(repos)

        registry.get(LeVADocumentScheduler).run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)

        return [project: project, repos: repos, startAgent: stageToStartAgent]
    }

    private checkoutGitRef(String gitRef, def extensions) {
        script.checkout([
            $class: 'GitSCM',
            branches: [[name: gitRef]],
            doGenerateSubmoduleConfigurations: false,
            extensions: extensions,
            userRemoteConfigs: script.scm.userRemoteConfigs,
        ])
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
