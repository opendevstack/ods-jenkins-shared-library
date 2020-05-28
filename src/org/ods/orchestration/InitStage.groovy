package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.services.BitbucketService
import org.ods.services.GitService
import org.ods.services.OpenShiftService

import org.ods.orchestration.scheduler.*
import org.ods.orchestration.service.*
import org.ods.orchestration.usecase.*
import org.ods.orchestration.util.*
import org.ods.util.*

@SuppressWarnings('AbcMetric')
class InitStage extends Stage {

    public final String STAGE_NAME = 'Init'

    InitStage(def script, Context context, List<Set<Map>> repos) {
        super(script, context, repos)
    }

    @SuppressWarnings(['CyclomaticComplexity', 'NestedBlockDepth'])
    def run() {
        def steps = new PipelineSteps(script)
        def context = new Context(steps)

        def git = new GitService(steps)
        git.configureUser()

        // load build params
        def buildParams = Context.loadBuildParams(steps)
        steps.echo("Release Manager Build Parameters: ${buildParams}")

        // git checkout
        def gitReleaseBranch = GitService.getReleaseBranch(buildParams.version)
        if (!Context.isWorkInProgress(buildParams.version)) {
            if (Context.isPromotionMode(buildParams.targetEnvironmentToken)) {
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
                steps.echo("Checkout release manager repository @ ${baseTag}")
                checkoutGitRef(
                    "refs/tags/${baseTag}",
                    []
                )
            } else {
                if (git.remoteBranchExists(gitReleaseBranch)) {
                    steps.echo("Checkout release manager repository @ ${gitReleaseBranch}")
                    checkoutGitRef(
                        "*/${gitReleaseBranch}",
                        [[$class: 'LocalBranch', localBranch: '**']]
                    )
                } else {
                    git.checkoutNewLocalBranch(gitReleaseBranch)
                }
            }
        }

        steps.echo 'Load build params and metadata file information'
        context.init()

        steps.echo 'Register global services'
        def logger = new Logger(steps, steps.env.DEBUG)
        def registry = ServiceRegistry.instance
        registry.add(GitService, git)
        registry.add(PDFUtil, new PDFUtil())
        registry.add(PipelineSteps, steps)
        def util = new MROPipelineUtil(context, steps, git)
        registry.add(MROPipelineUtil, util)
        registry.add(Context, context)

        def docGenUrl = script.env.DOCGEN_URL ?: "http://docgen.${context.key}-cd.svc:8080"
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

        if (context.services?.jira) {
            script.withCredentials(
                [script.usernamePassword(
                    credentialsId: context.services.jira.credentials.id,
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

                if (context.hasCapability("Zephyr")) {
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

        registry.add(NexusService,
            new NexusService(
                script.env.NEXUS_URL,
                script.env.NEXUS_USERNAME,
                script.env.NEXUS_PASSWORD
            )
        )

        registry.add(OpenShiftService,
            new OpenShiftService(
                registry.get(PipelineSteps),
                logger,
                context.targetProject
            )
        )

        def jiraUseCase = new JiraUseCase(
            registry.get(Context),
            registry.get(PipelineSteps),
            registry.get(MROPipelineUtil),
            registry.get(JiraService)
        )

        if (context.hasCapability('Zephyr')) {
            jiraUseCase.setSupport(
                new JiraUseCaseZephyrSupport(
                    context,
                    steps,
                    jiraUseCase,
                    registry.get(JiraZephyrService),
                    registry.get(MROPipelineUtil)
                )
            )
        } else {
            jiraUseCase.setSupport(
                new JiraUseCaseSupport(context, steps, jiraUseCase)
            )
        }

        registry.add(JiraUseCase, jiraUseCase)

        registry.add(JUnitTestReportsUseCase,
            new JUnitTestReportsUseCase(
                registry.get(Context),
                registry.get(PipelineSteps)
            )
        )

        registry.add(SonarQubeUseCase,
            new SonarQubeUseCase(
                registry.get(Context),
                registry.get(PipelineSteps),
                registry.get(NexusService)
            )
        )

        registry.add(LeVADocumentUseCase,
            new LeVADocumentUseCase(
                registry.get(Context),
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
                registry.get(Context),
                registry.get(PipelineSteps),
                registry.get(MROPipelineUtil),
                registry.get(LeVADocumentUseCase)
            )
        )

        registry.add(BitbucketService, new BitbucketService(
            registry.get(PipelineSteps).unwrap(),
            context.releaseManagerBitbucketHostUrl,
            context.key,
            context.services.bitbucket.credentials.id
        ))

        BitbucketService bitbucket = registry.get(BitbucketService)

        def phase = MROPipelineUtil.PipelinePhases.INIT

        steps.echo 'Run Context#load'
        context.load(registry.get(GitService), registry.get(JiraUseCase))
        def repos = context.repositories

        bitbucket.setBuildStatus (steps.env.BUILD_URL, context.gitData.commit,
            'INPROGRESS', "Release Manager for commit: ${context.gitData.commit}")

        steps.echo 'Validate that for Q and P we have a valid version'
        if (context.isPromotionMode && ['Q', 'P'].contains(context.buildParams.targetEnvironmentToken)
            && buildParams.version == 'WIP') {
            throw new RuntimeException(
                'Error: trying to deploy to Q or P without having defined a correct version. ' +
                "${buildParams.version} version value is not allowed for those environments. " +
                'If you are using Jira, please check that all values are set in the release manager issue. ' +
                "Build parameters obtained: ${buildParams}"
            )
        }

        if (context.isPromotionMode && git.localTagExists(context.targetTag)) {
            if (context.buildParams.targetEnvironmentToken == 'Q') {
                steps.echo("WARNING: Deploying tag ${context.targetTag} again!")
            } else {
                throw new RuntimeException(
                    "Error: tag ${context.targetTag} already exists - it cannot be deployed again to P."
                )
            }
        }

        def jobMode = context.isPromotionMode ? '(promote)' : '(assemble)'

        steps.echo 'Configure current build description'
        script.currentBuild.description = "Build ${jobMode} #${script.BUILD_NUMBER} - " +
            "Change: ${script.env.RELEASE_PARAM_CHANGE_ID}, " +
            "Project: ${context.key}, " +
            "Target Environment: ${context.key}-${script.env.MULTI_REPO_ENV}, " +
            "Version: ${script.env.VERSION}"

        steps.echo 'Checkout repositories into the workspace'
        script.parallel(util.prepareCheckoutReposNamedJob(repos) { s, repo ->
            steps.echo("Repository: ${repo}")
            steps.echo("Environment configuration: ${script.env.getEnvironment()}")
        })

        steps.echo "Load configs from each repo's release-manager.yml"
        util.loadPipelineConfigs(repos)

        def os = registry.get(OpenShiftService)

        context.setOpenShiftData(os.apiUrl)

        // It is assumed that the pipeline runs in the same cluster as the 'D' env.
        if (context.buildParams.targetEnvironmentToken == 'D' && !os.envExists()) {

            runOnAgentPod(context, true) {

                def sourceEnv = context.buildParams.targetEnvironment
                os.createVersionedDevelopmentEnvironment(context.key, sourceEnv)

                def envParamsFile = context.environmentParamsFile
                def envParams = context.getEnvironmentParams(envParamsFile)

                repos.each { repo ->
                    steps.dir("${steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/${repo.id}") {
                        def openshiftDir = 'openshift-exported'
                        def exportRequired = true
                        if (script.fileExists('openshift')) {
                            steps.echo(
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
                        def componentSelector = "app=${context.key}-${repo.id}"
                        steps.dir(openshiftDir) {
                            if (exportRequired) {
                                steps.echo("Exporting current OpenShift state to folder '${openshiftDir}'.")
                                def targetFile = 'template.yml'
                                (new OpenShiftService(steps, logger, "${context.key}-${sourceEnv}")).tailorExport(
                                    componentSelector,
                                    envParams,
                                    targetFile
                                )
                            }

                            steps.echo(
                                "Applying desired OpenShift state defined in ${openshiftDir} " +
                                "to ${context.targetProject}."
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
                            jenkins.maybeWithPrivateKeyCredentials(context.tailorPrivateKeyCredentialsId) { pkeyFile ->
                                applyFunc(pkeyFile)
                            }
                        }
                    }
                }
            }
        }

        steps.echo 'Compute groups of repository configs for convenient parallelization'
        repos = util.computeRepoGroups(repos)

        registry.get(LeVADocumentScheduler).run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)

        return [context: context, repos: repos]
    }

    private checkoutGitRef(String gitRef, def extensions) {
        script.checkout([
            $class: 'GitSCM',
            branches: [[name: gitRef]],
            doGenerateSubmoduleConfigurations: false,
            extensions: extensions,
            userRemoteConfigs: script.scm.userRemoteConfigs
        ])
    }

}
