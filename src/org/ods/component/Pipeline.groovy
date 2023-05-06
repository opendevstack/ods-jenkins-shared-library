package org.ods.component

import groovy.json.JsonOutput
import org.ods.services.BitbucketService
import org.ods.services.GitService
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.GitCredentialStore
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.PipelineSteps

class Pipeline implements Serializable {

    private GitService gitService
    private OpenShiftService openShiftService
    private JenkinsService jenkinsService
    private BitbucketService bitbucketService

    private ILogger logger
    private def script
    private IPipelineSteps steps
    private IContext context
    private boolean notifyNotGreen = true
    private boolean ciSkipEnabled = true
    private boolean displayNameUpdateEnabled = true
    private boolean localCheckoutEnabled = true
    private boolean bitbucketNotificationEnabled = true

    Pipeline(def script, ILogger logger) {
        this.script = script
        this.steps = new PipelineSteps(script)
        this.logger = logger
    }

    // Main entry point.
    @SuppressWarnings(['NestedBlockDepth', 'AbcMetric', 'CyclomaticComplexity',
        'MethodSize', 'GStringExpressionWithinString'])
    def execute(Map config, Closure stages) {
        logger.debug "-> ODS Component pipeline setup, debug mode? ${logger.debugMode}"
        if (!!script.env.MULTI_REPO_BUILD) {
            setupForMultiRepoBuild(config)
        }
        if (config.containsKey('notifyNotGreen')) {
            this.notifyNotGreen = config.notifyNotGreen
        }
        if (config.containsKey('ciSkipEnabled')) {
            this.ciSkipEnabled = config.ciSkipEnabled
        }
        if (config.containsKey('displayNameUpdateEnabled')) {
            this.displayNameUpdateEnabled = config.displayNameUpdateEnabled
        }
        if (config.containsKey('localCheckoutEnabled')) {
            this.localCheckoutEnabled = config.localCheckoutEnabled
        }
        if (config.containsKey('bitbucketNotificationEnabled')) {
            this.bitbucketNotificationEnabled = config.bitbucketNotificationEnabled
        }
        if (!config.projectId || !config.componentId) {
            amendProjectAndComponentFromOrigin(config)
        }
        if (!config.projectId) {
            script.error "Param 'projectId' is required"
        }
        if (!config.componentId) {
            script.error "Param 'componentId' is required"
        }
        // amendProjectAndComponent.. will set the repoName from the origin url
        // in case componentId or projectId was set hard, we use those to set it
        // allow to overwrite in case NOT ods std (e.g. from a migration)
        if (!config.repoName) {
            config.repoName = "${config.projectId}-${config.componentId}"
        }

        prepareAgentPodConfig(config)
        logger.infoClocked("${config.componentId}", "***** Starting ODS Component Pipeline *****")
        context = new Context(script, config, logger, this.localCheckoutEnabled)

        boolean skipCi = false
        def bootstrap = {
            try {
                script.wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
                    if (this.localCheckoutEnabled) {
                        script.checkout script.scm
                    }
                    script.stage('odsPipeline start') {
                        def defaultDockerRegistry = 'image-registry.openshift-image-registry.svc:5000'
                        // we leave the check here for the registry
                        // to bring this close to the real bootstrap of the agent.
                        if (!config.containsKey('podContainers') && !config.image) {
                            def dockerRegistry = script.env.DOCKER_REGISTRY ?: defaultDockerRegistry
                            config.image = "${dockerRegistry}/${config.imageStreamTag}"
                        }
                        // in VERY rare (> 7 parallel agents, sometimes the env.X returns null)
                        def wtfEnvBug = 'null/'
                        if (config.image?.startsWith(wtfEnvBug)) {
                            config.image = config.image.
                                replace(wtfEnvBug, "${defaultDockerRegistry}/")
                            logger.warn("Patched image via master env to: ${config.image}")
                        }

                        context.assemble()

                        // register services after context was assembled
                        logger.debug('-> Registering & loading global services')
                        def registry = ServiceRegistry.instance

                        // In non-MRO case, reset the ServiceRegistry.
                        // This allows users to have two pipelines in one
                        // Jenkinsfile with e.g. differing OpenShift target projects.
                        if (this.localCheckoutEnabled) {
                            registry.clear()
                        }

                        // if we run in another context there is a good chance
                        // services have been already registered
                        if (!registry.get(GitService)) {
                            logger.debug 'Registering GitService'
                            registry.add(GitService, new GitService(script, logger))
                        }
                        this.gitService = registry.get(GitService)

                        if (!registry.get(BitbucketService)) {
                            logger.debug 'Registering BitbucketService'
                            registry.add(BitbucketService, new BitbucketService(
                                script,
                                context.bitbucketUrl,
                                context.projectId,
                                context.credentialsId,
                                logger
                            ))
                        }
                        this.bitbucketService = registry.get(BitbucketService)

                        if (!registry.get(OpenShiftService)) {
                            logger.debug 'Registering OpenShiftService'
                            registry.add(OpenShiftService, new OpenShiftService(
                                steps, logger
                            ))
                        }
                        this.openShiftService = registry.get(OpenShiftService)

                        if (!registry.get(JenkinsService)) {
                            logger.debug 'Registering JenkinsService'
                            registry.add(JenkinsService, new JenkinsService(script, logger))
                        }
                        this.jenkinsService = registry.get(JenkinsService)

                        if (!registry.get(NexusService)) {
                            logger.debug 'Registering NexusService'
                            registry.add(NexusService, new NexusService(
                                context.nexusUrl, context.nexusUsername, context.nexusPassword))
                        }
                    }

                    // check if there is a skipped previous run - if so - delete (to save memory)
                    if (!script.env.MULTI_REPO_BUILD) {
                        jenkinsService.deleteNotBuiltBuilds(
                            script.currentBuild.getPreviousBuild())
                    }

                    skipCi = isCiSkip()
                    if (skipCi) {
                        script.stage('odsPipeline (ci skip) finished') {
                            logger.info 'Skipping build due to [ci skip], [skip ci] or ***NO_CI***' +
                                ' in the commit message ...'
                            updateBuildStatus('NOT_BUILT')
                            setBitbucketBuildStatus('SUCCESSFUL')
                            return this
                        }
                    }
                }
            } catch (err) {
                logger.warn("Error during ODS component pipeline setup: ${err}")
                updateBuildStatus('FAILURE')
                setBitbucketBuildStatus('FAILED')
                if (notifyNotGreen) {
                    doNotifyNotGreen(context.emailextRecipients)
                }
                throw err
            }
        }
        if (this.localCheckoutEnabled) {
            logger.info "***** Continuing on node 'master' *****"
            script.node('master', bootstrap)
        } else {
            bootstrap()
        }

        if (!skipCi) {
            if (!config.containsKey('podContainers')) {
                config.podContainers = [
                    script.containerTemplate(
                        name: 'jnlp',
                        image: config.image,
                        workingDir: '/tmp',
                        resourceRequestMemory: config.resourceRequestMemory,
                        resourceLimitMemory: config.resourceLimitMemory,
                        resourceRequestCpu: config.resourceRequestCpu,
                        resourceLimitCpu: config.resourceLimitCpu,
                        alwaysPullImage: config.alwaysPullImage,
                        args: '${computer.jnlpmac} ${computer.name}'
                    )
                ]
            }
            def msgBasedOn = ''
            if (config.image) {
                msgBasedOn = " based on image '${config.image}'"
            }
            logger.info "***** Continuing on node '${config.podLabel}'${msgBasedOn} *****"
            logger.startClocked("${config.podLabel}")
            script.podTemplate(
                label: config.podLabel,
                cloud: 'openshift',
                containers: config.podContainers,
                volumes: config.podVolumes,
                serviceAccount: config.podServiceAccount,
                annotations: config.annotations,
                slaveConnectTimeout: 240, // in seconds
            ) {
                script.node(config.podLabel) {
                    try {
                        logger.debugClocked("${config.podLabel}")
                        setBitbucketBuildStatus('INPROGRESS')
                        script.wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
                            gitService.checkout(
                                context.gitCommit,
                                [],
                                [[credentialsId: context.credentialsId, url: context.gitUrl]]
                            )
                            if (this.displayNameUpdateEnabled) {
                                script.currentBuild.displayName = "#${context.tagversion}"
                            }
                            // hook method for (Agent) specific callouts
                            context.amendWithAgentInformation()
                            if (context.commitGitWorkingTree) {
                                gitService.configureUser()
                                script.withCredentials(
                                    [script.usernamePassword(
                                        credentialsId: context.credentialsId,
                                        usernameVariable: 'BITBUCKET_USER',
                                        passwordVariable: 'BITBUCKET_PW'
                                    )]
                                ) {
                                    GitCredentialStore.configureAndStore(
                                        script, context.bitbucketUrl as String,
                                        script.env.BITBUCKET_USER as String,
                                        script.env.BITBUCKET_PW as String)
                                }
                                gitService.switchToRemoteBranch(context.gitBranch)
                            }
                            stages(context)
                            if (context.commitGitWorkingTree) {
                                gitService.commit([], "system-commit ods, [ci skip]", true)
                                gitService.pushRef(context.gitBranch)
                            }
                        }
                        script.stage('odsPipeline finished') {
                            updateBuildStatus('SUCCESS')
                            setBitbucketBuildStatus('SUCCESSFUL')
                            logger.infoClocked("${context.componentId}", '***** Finished ODS Pipeline *****')
                        }
                        return this
                    } catch (err) {
                        script.stage('odsPipeline error') {
                            logger.warnClocked("${context.componentId}",
                                "***** Finished ODS Pipeline for ${context.componentId} (with error) *****")
                            logger.warn "Error: ${err}"
                            updateBuildStatus('FAILURE')
                            setBitbucketBuildStatus('FAILED')
                            if (notifyNotGreen) {
                                doNotifyNotGreen(context.emailextRecipients)
                            }
                            if (!!script.env.MULTI_REPO_BUILD) {
                                context.addArtifactURI('failedStage', script.env.STAGE_NAME)
                                // this is the case on a parallel node to be interrupted
                                if (err instanceof org.jenkinsci.plugins.workflow.steps.FlowInterruptedException) {
                                    throw err
                                }
                                return this
                            }
                            throw err
                        }
                    } finally {
                        jenkinsService.stashTestResults(
                            context.testResults,
                            "${context.componentId}-${context.buildNumber}"
                        ).each { resultKey, resultValue ->
                            context.addArtifactURI(resultKey, resultValue)
                        }
                        logger.debugClocked("${config.componentId}",
                            "ODS Component Pipeline '${context.componentId}-${context.buildNumber}'\r" +
                                "ODS Build Artifacts '${context.componentId}': " +
                                "\r${JsonOutput.prettyPrint(JsonOutput.toJson(context.getBuildArtifactURIs()))}"
                        )
                    }
                }
            }
        }
    }

    def setupForMultiRepoBuild(def config) {
        logger.info '-> Detected multirepo orchestration pipeline build'
        config.localCheckoutEnabled = false
        config.displayNameUpdateEnabled = false
        config.ciSkipEnabled = false
        config.notifyNotGreen = false
        config.sonarQubeBranch = '*'
        def buildEnv = script.env.MULTI_REPO_ENV
        if (buildEnv) {
            config.environment = buildEnv
            logger.debug("Setting target environment: '${config.environment}'")
        } else {
            logger.warn 'Variable MULTI_REPO_ENV (target environment!) must not be null!'
            // Using exception because error step would skip post steps
            throw new RuntimeException("Variable 'MULTI_REPO_ENV' (target environment!)" +
                ' must not be null!')
        }
        config.bitbucketNotificationEnabled = !!script.env.NOTIFY_BB_BUILD
    }

    @SuppressWarnings('Instanceof')
    def updateBuildStatus(String status) {
        if (this.displayNameUpdateEnabled) {
            // @ FIXME ? groovy.lang.MissingPropertyException: No such property: result for class: java.lang.String
            if (script.currentBuild instanceof String) {
                script.currentBuild = status
            } else {
                script.currentBuild.result = status
            }
        }
    }

    Map<String, Object> getBuildArtifactURIs() {
        return context.getBuildArtifactURIs()
    }

    private void setBitbucketBuildStatus(String state) {
        if (!this.bitbucketNotificationEnabled) {
            return
        }
        // in some nasty nullpointer cases - jenkins suddenly nullifies this bitbucket service?
        // which in case a previous error, will push the nullp up the stack, and hide the "real" error
        if (!bitbucketService) {
            logger.warn "Cannot set Bitbucket build status to '${state}' as bitbucket service is null!"
            return
        }
        if (!context.buildUrl || !context.gitCommit) {
            logger.info "Cannot set Bitbucket build status to '${state}' because required data is missing!"
            return
        }

        def buildName = "${context.gitCommit.take(8)}"
        bitbucketService.setBuildStatus(context.buildUrl, context.gitCommit, state, buildName)
    }

    private void doNotifyNotGreen(List<String> emailextRecipients) {
        String subject = "Build $context.componentId on project $context.projectId  failed!"
        String body = "<p>$subject</p> <p>URL : <a href=\"$context.buildUrl\">$context.buildUrl</a></p> "
        String recipients = emailextRecipients ? emailextRecipients.join(", ") : ''

        script.emailext(
            body: body, mimeType: 'text/html',
            replyTo: '$script.DEFAULT_REPLYTO', subject: subject,
            to: recipients
        )
    }

    // Whether the build should be skipped, based on the Git commit message.
    private boolean isCiSkip() {
        return this.ciSkipEnabled && gitService.ciSkipInCommitMessage
    }

    private void prepareAgentPodConfig(Map config) {
        if (!config.image && !config.imageStreamTag && !config.podContainers) {
            script.error "One of 'image', 'imageStreamTag' or 'podContainers' is required"
        }
        if (!config.podVolumes) {
            config.podVolumes = []
        }
        if (!config.containsKey('podServiceAccount')) {
            config.podServiceAccount = 'jenkins'
        }
        if (!config.containsKey('alwaysPullImage')) {
            config.alwaysPullImage = true
        }
        if (!config.containsKey('resourceRequestMemory')) {
            config.resourceRequestMemory = '1Gi'
        }
        if (!config.containsKey('resourceLimitMemory')) {
            // 2Gi is required for e.g. jenkins-agent-maven, which selects the Java
            // version based on available memory.
            // Also, e.g. Angular is known to use a lot of memory during production
            // builds.
            // Quickstarters should set a lower value if possible.
            config.resourceLimitMemory = '2Gi'
        }
        if (!config.containsKey('resourceRequestCpu')) {
            config.resourceRequestCpu = '100m'
        }
        if (!config.containsKey('resourceLimitCpu')) {
            // 1 core is a lot but this directly influences build time.
            // Quickstarters should set a lower value if possible.
            config.resourceLimitCpu = '1'
        }
        if (!config.containsKey('podLabel')) {
            config.podLabel = "pod-${UUID.randomUUID().toString()}"
        }
        if (!config.annotations) {
            config.annotations = [
                script.podAnnotation(
                    key: 'cluster-autoscaler.kubernetes.io/safe-to-evict', value: 'false'
                )
            ]
        }
    }

    private void amendProjectAndComponentFromOrigin(Map config) {
        logger.debug("Amending project / component name based on Git origin URL")
        def block = {
            def origin
            try {
                origin = new GitService(script, this.logger).getOriginUrl()
                logger.debug("Retrieved Git origin URL from filesystem: ${origin}")
            } catch (err) {
                logger.debug("Could not retrieve git origin from filesystem: ${err}")
                def jobSplitList = script.env.JOB_NAME.split('/')
                def projectName = jobSplitList[0]
                def bcName = jobSplitList[1].replace("${projectName}-", '')
                origin = (new OpenShiftService(steps, logger))
                    .getOriginUrlFromBuildConfig(projectName, bcName)
                logger.debug("Retrieved Git origin URL from build config: ${origin}")
            }

            def splittedOrigin = origin.split('/')
            def project = splittedOrigin[splittedOrigin.size() - 2]
            if (!config.projectId) {
                config.projectId = project
            }
            // get the repo name from the git url
            config.repoName = splittedOrigin.last().replace('.git', '')
            if (!config.componentId) {
                config.componentId = config.repoName - ~/^${project}-/
            }
            logger.debug(
                "Project- / component-name config: ${config.projectId} / ${config.componentId}"
            )
        }
        if (this.localCheckoutEnabled) {
            script.node('master', block)
        } else {
            block()
        }
    }

}
