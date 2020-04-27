import hudson.Functions

import java.nio.file.Paths

import kong.unirest.Unirest

import org.ods.orchestration.scheduler.*
import org.ods.orchestration.service.*
import org.ods.orchestration.usecase.*
import org.ods.orchestration.util.*

def call() {
    def steps = new PipelineSteps(this)
    def project = new Project(steps)

    try {
        Unirest.config()
            .socketTimeout(1200000)
            .connectTimeout(120000)

        def git = new GitUtil(steps)
        git.configureUser()

        // load build params
        def buildParams = Project.loadBuildParams(steps)
        steps.echo("Build Parameters: ${buildParams}")


        // git checkout
        def gitReleaseBranch = GitUtil.getReleaseBranch(buildParams.version)
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
                    throw new RuntimeException("Error: unable to find latest tag for version ${buildParams.version}/${buildParams.changeId}.")
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
                        [[$class: 'LocalBranch', localBranch: "**"]]
                    )
                } else {
                    git.checkoutNewLocalBranch(gitReleaseBranch)
                }
            }
        }

        // Load build params and metadata file information.
        project.init()

        // Register global services
        def registry = ServiceRegistry.instance
        registry.add(GitUtil, git)
        registry.add(PDFUtil, new PDFUtil())
        registry.add(PipelineSteps, steps)
        def util = new MROPipelineUtil(project, steps, git)
        registry.add(MROPipelineUtil, util)
        registry.add(Project, project)

        def docGenUrl = env.DOCGEN_URL ?: "http://docgen.${project.key}-cd.svc:8080"
        registry.add(DocGenService,
            new DocGenService(docGenUrl)
        )

        registry.add(LeVADocumentChaptersFileService,
            new LeVADocumentChaptersFileService(steps)
        )

        registry.add(JenkinsService,
            new JenkinsService(
                registry.get(PipelineSteps)
            )
        )

        registry.add(LeVADocumentChaptersFileService,
            new LeVADocumentChaptersFileService(steps)
        )

        registry.add(JenkinsService,
            new JenkinsService(
                registry.get(PipelineSteps)
            )
        )

        if (project.services?.jira) {
            withCredentials([usernamePassword(credentialsId: project.services.jira.credentials.id, usernameVariable: "JIRA_USERNAME", passwordVariable: "JIRA_PASSWORD")]) {
                registry.add(JiraService,
                    new JiraService(
                        env.JIRA_URL,
                        env.JIRA_USERNAME,
                        env.JIRA_PASSWORD
                    )
                )

                if (project.hasCapability("Zephyr")) {
                    registry.add(JiraZephyrService,
                        new JiraZephyrService(
                            env.JIRA_URL,
                            env.JIRA_USERNAME,
                            env.JIRA_PASSWORD
                        )
                    )
                }
            }
        }

        registry.add(NexusService,
            new NexusService(
                env.NEXUS_URL,
                env.NEXUS_USERNAME,
                env.NEXUS_PASSWORD
            )
        )

        def tailorPrivateKeyFile = ''
        def tailorPrivateKeyCredentialsId = "${project.key}-cd-tailor-private-key"
        if (privateKeyExists(tailorPrivateKeyCredentialsId)) {
            withCredentials([
                sshUserPrivateKey(
                    credentialsId: tailorPrivateKeyCredentialsId,
                    keyFileVariable: 'PKEY_FILE'
                )
            ]) {
                tailorPrivateKeyFile = PKEY_FILE
            }
        }

        withCredentials([usernamePassword(credentialsId: project.services.bitbucket.credentials.id, usernameVariable: "BITBUCKET_USER", passwordVariable: "BITBUCKET_PW")]) {
            registry.add(OpenShiftService,
                new OpenShiftService(
                    registry.get(PipelineSteps),
                    env.OPENSHIFT_API_URL,
                    env.BITBUCKET_HOST,
                    env.BITBUCKET_USER,
                    env.BITBUCKET_PW,
                    tailorPrivateKeyFile
                )
            )
        }

        def jiraUseCase = new JiraUseCase(
            registry.get(Project),
            registry.get(PipelineSteps),
            registry.get(MROPipelineUtil),
            registry.get(JiraService)
        )

        jiraUseCase.setSupport(
            project.hasCapability("Zephyr")
                ? new JiraUseCaseZephyrSupport(project, steps, jiraUseCase, registry.get(JiraZephyrService), registry.get(MROPipelineUtil))
                : new JiraUseCaseSupport(project, steps, jiraUseCase)
        )

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
                registry.get(LeVADocumentUseCase)
            )
        )

        def phase = MROPipelineUtil.PipelinePhases.INIT

        project.load(registry.get(GitUtil), registry.get(JiraUseCase))
        def repos = project.repositories

         // Validate that for Q and P we have a valid version
        if (project.isPromotionMode && ['Q', 'P'].contains(project.buildParams.targetEnvironmentToken) && buildParams.version == "WIP") {
            throw new RuntimeException("Error: trying to deploy to Q or P without having defined a correct version. ${buildParams.version} version value is not allowed for those environments. If you are using Jira, please check that all values are set in the release manager issue. Build parameters obtained: ${buildParams}")
        }

       if (project.isPromotionMode && git.localTagExists(project.targetTag)) {
            if (project.buildParams.targetEnvironmentToken == 'Q') {
                steps.echo("WARNING: Deploying tag ${project.targetTag} again!")
            } else {
                throw new RuntimeException("Error: tag ${project.targetTag} already exists - it cannot be deployed again to P.")
            }
        }

        def jobMode = project.isPromotionMode ? "(promote)" : "(assemble)"

        // Configure current build
        currentBuild.description = "Build ${jobMode} #${BUILD_NUMBER} - Change: ${env.RELEASE_PARAM_CHANGE_ID}, Project: ${project.key}, Target Environment: ${project.key}-${env.MULTI_REPO_ENV}, Version: ${env.VERSION}"

        // Clean workspace from previous runs
        [PipelineUtil.ARTIFACTS_BASE_DIR, PipelineUtil.SONARQUBE_BASE_DIR, PipelineUtil.XUNIT_DOCUMENTS_BASE_DIR, MROPipelineUtil.REPOS_BASE_DIR].each { name ->
            steps.echo("Cleaning workspace directory '${name}' from previous runs")
            Paths.get(env.WORKSPACE, name).toFile().deleteDir()
        }

        // Checkout repositories into the workspace
        parallel(util.prepareCheckoutReposNamedJob(repos) { steps_, repo ->
            steps.echo("Repository: ${repo}")
            steps.echo("Environment configuration: ${env.getEnvironment()}")
        })

        // Load configs from each repo's release-manager.yml
        util.loadPipelineConfigs(repos)

        def os = registry.get(OpenShiftService)

        project.setOpenShiftData(os.sessionApiUrl)

        // It is assumed that the pipeline runs in the same cluster as the 'D' env.
        if (project.buildParams.targetEnvironmentToken == 'D' && !os.envExists(project.targetProject)) {

            runOnAgentPod(project, true) {

                def sourceEnv = project.buildParams.targetEnvironment
                os.createVersionedDevelopmentEnvironment(project.key, sourceEnv, project.concreteEnvironment)

                def envParamsFile = project.environmentParamsFile
                def envParams = project.getEnvironmentParams(envParamsFile)

                repos.each { repo ->
                    steps.dir("${steps.env.WORKSPACE}/${MROPipelineUtil.REPOS_BASE_DIR}/${repo.id}") {
                        def openshiftDir = 'openshift-exported'
                        def exportRequired = true
                        if (fileExists('openshift')) {
                            steps.echo("Found 'openshift' folder, current OpenShift state will not be exported into 'openshift-exported'.")
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
                                steps.echo("Exporting current OpenShift state to folder '${openshiftDir}'.")
                                def targetFile = 'template.yml'
                                os.tailorExport(
                                    "${project.key}-${sourceEnv}",
                                    componentSelector,
                                    envParams,
                                    targetFile
                                )
                            }

                            steps.echo("Applying desired OpenShift state defined in ${openshiftDir} to ${project.targetProject}.")
                            os.tailorApply(
                                project.targetProject,
                                componentSelector,
                                '', // do not exlude any resources
                                envParamsFile,
                                false
                            )
                        }
                    }
                }
            }
        }

        // Compute groups of repository configs for convenient parallelization
        repos = util.computeRepoGroups(repos)

        registry.get(LeVADocumentScheduler).run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)

        return [project: project, repos: repos]
    } catch (e) {
        // Check for random null references which occur after a Jenkins restart
        if (ServiceRegistry.instance == null || ServiceRegistry.instance.get(PipelineSteps) == null) {
            e = new IllegalStateException("Error: invalid references have been detected for critical pipeline services. Most likely, your Jenkins instance has been recycled. Please re-run the pipeline!").initCause(e)
        }

        echo(e.message)

        try {
            project.reportPipelineStatus(e.message, true)
        } catch (reportError) {
            echo("Error: unable to report pipeline status because of: ${reportError.message}.")
            reportError.initCause(e)
            throw reportError
        }

        throw e
    }
}

private boolean privateKeyExists(def privateKeyCredentialsId) {
    try {
        withCredentials([sshUserPrivateKey(credentialsId: privateKeyCredentialsId, keyFileVariable: 'PKEY_FILE')]) {
            true
        }
    } catch (_) {
        false
    }
}

private checkoutGitRef(String gitRef, def extensions) {
    checkout([
        $class                           : 'GitSCM',
        branches                         : [[name: gitRef]],
        doGenerateSubmoduleConfigurations: false,
        extensions                       : extensions,
        userRemoteConfigs                : scm.userRemoteConfigs
    ])
}

return this
