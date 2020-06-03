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

import org.ods.util.PipelineSteps
import org.ods.util.Logger
import org.ods.util.ILogger

@SuppressWarnings('AbcMetric')
class InitStage extends Stage {

    public final String STAGE_NAME = 'Init'

    InitStage(def script, Project project, List<Set<Map>> repos, String startMROStageName) {
        super(script, project, repos, startMROStageName)
    }

    @SuppressWarnings(['CyclomaticComplexity', 'NestedBlockDepth', 'GStringAsMapKey'])
    def run() {
        ILogger logger = ServiceRegistry.instance.get(Logger)
        def steps = new PipelineSteps(script)
        logger.startClocked("boot-git-${STAGE_NAME}")
        def git = new GitService(steps, logger)
        git.configureUser()
        logger.debugClocked("boot-git-${STAGE_NAME}")

        // load build params
        def buildParams = Project.loadBuildParams(steps)
        logger.debug("Release Manager Build Parameters: ${buildParams}")

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
                    []
                )
            } else {
                if (git.remoteBranchExists(gitReleaseBranch)) {
                    logger.info("Checkout release manager repository @ ${gitReleaseBranch}")
                    checkoutGitRef(
                        "*/${gitReleaseBranch}",
                        [[$class: 'LocalBranch', localBranch: '**']]
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



        registry.add(NexusService, NexusService.newFromEnv(script.env, logger))

        registry.add(OpenShiftService,
            new OpenShiftService(
                registry.get(PipelineSteps),
                logger,
                project.targetProject
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

        def phase = MROPipelineUtil.PipelinePhases.INIT

        logger.debug 'Checkout repositories into the workspace'
        project.initGitDataAndJiraUsecase(
            registry.get(GitService), registry.get(JiraUseCase))

        def repos = project.repositories
        @SuppressWarnings('Indentation')
        Closure checkoutClosure = 
        {
            script.parallel (
                util.prepareCheckoutReposNamedJob(repos) { s, repo ->
                    logger.info("Repository: ${repo}")
                }
            )
        }

        Closure loadClosure = {
            logger.debugClocked('Project#load')
            project.load(registry.get(GitService), registry.get(JiraUseCase))
            logger.debugClocked('Project#load')
            bitbucket.setBuildStatus (steps.env.BUILD_URL, project.gitData.commit,
                'INPROGRESS', "Release Manager for commit: ${project.gitData.commit}")

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

            if (project.isPromotionMode && git.localTagExists(project.targetTag)) {
                if (project.buildParams.targetEnvironmentToken == 'Q') {
                    logger.warn("Deploying tag ${project.targetTag} again!")
                } else {
                    throw new RuntimeException(
                        "Error: Git Tag '${project.targetTag}' already exists - " +
                        'it cannot be deployed again to P.'
                    )
                }
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
            logger.debug("MRO Start stage: ${this.startMROSlaveStageName}")
        }

        executeInParallel(checkoutClosure, loadClosure)

        // find best place for mro slave start
        def stageToStartMRO
        repos.each { repo ->
            if (repo.type == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST) {
                stageToStartMRO = MROPipelineUtil.PipelinePhases.TEST
            } else if (repo.type == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE &&
                !repo.data?.odsBuildArtifacts?.resurrected) {
                if (stageToStartMRO != MROPipelineUtil.PipelinePhases.TEST) {
                    stageToStartMRO = MROPipelineUtil.PipelinePhases.BUILD
                }
            }
        }
        if (!stageToStartMRO) {
            logger.info "No applicable stage found - slave bootstrap will run during 'deploy'.\r" +
                "To change this to 'init', change 'startOrchestrationSlaveOnInit' in JenkinsFile to 'true'"
            stageToStartMRO = MROPipelineUtil.PipelinePhases.DEPLOY
        }
        def os = registry.get(OpenShiftService)

        // It is assumed that the pipeline runs in the same cluster as the 'D' env.
        if (project.buildParams.targetEnvironmentToken == 'D' && !os.envExists()) {
            runOnAgentPod(true) {
                def sourceEnv = project.buildParams.targetEnvironment
                os.createVersionedDevelopmentEnvironment(project.key, sourceEnv)

                def envParamsFile = project.environmentParamsFile
                def envParams = project.getEnvironmentParams(envParamsFile)

                repos.each { repo ->
                    steps.dir("${steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/${repo.id}") {
                        def openshiftDir = 'openshift-exported'
                        def exportRequired = true
                        if (script.fileExists('openshift')) {
                            logger.debug(
                                "Found 'openshift' folder, current OpenShift state " +
                                    "will not be exported into 'openshift-exported'."
                            )
                            openshiftDir = 'openshift'
                            exportRequired = false
                        } else {
                            steps.sh(
                                script: "mkdir -p ${openshiftDir}",
                                label: "Ensure ${openshiftDir} exists"
                            )
                        }
                        def componentSelector = "app=${project.key}-${repo.id}"
                        steps.dir(openshiftDir) {
                            if (exportRequired) {
                                logger.debug("Exporting current OpenShift state to folder '${openshiftDir}'.")
                                def targetFile = 'template.yml'
                                (new OpenShiftService(steps, logger, "${project.key}-${sourceEnv}")).tailorExport(
                                    componentSelector,
                                    envParams,
                                    targetFile
                                )
                            }

                            logger.info(
                                "Applying desired OpenShift state defined in ${openshiftDir} " +
                                "to ${project.targetProject}."
                            )
                            def params = []
                            def preserve = []
                            def applyFunc = { pkeyFile ->
                                os.tailorApply(
                                        [selector: componentSelector],
                                        envParamsFile,
                                        params,
                                        preserve,
                                        pkeyFile,
                                        false
                                    )
                            }
                            def jenkins = registry.get(JenkinsService)
                            jenkins.maybeWithPrivateKeyCredentials(project.tailorPrivateKeyCredentialsId) { pkeyFile ->
                                applyFunc(pkeyFile)
                            }
                        }
                    }
                }
            }
        }

        logger.debug 'Compute groups of repository configs for convenient parallelization'
        repos = util.computeRepoGroups(repos)

        registry.get(LeVADocumentScheduler).run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)

        return [project: project, repos: repos, startMROSlave: stageToStartMRO]
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

}
