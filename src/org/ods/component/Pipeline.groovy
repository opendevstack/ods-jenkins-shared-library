package org.ods.component

import org.ods.services.GitService
import org.ods.services.BitbucketService
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.ILogger
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import groovy.json.JsonOutput

class Pipeline implements Serializable {

    private GitService gitService
    private OpenShiftService openShiftService
    private JenkinsService jenkinsService
    private BitbucketService bitbucketService

    private final ILogger logger
    private final def script
    private IContext context
    private boolean notifyNotGreen = true
    private boolean ciSkipEnabled  = true
    private boolean displayNameUpdateEnabled = true
    private boolean localCheckoutEnabled = true
    private boolean bitbucketNotificationEnabled  = true

    Pipeline(def script, ILogger logger) {
        this.script = script
        this.logger = logger
    }

    // Main entry point.
    @SuppressWarnings(['NestedBlockDepth', 'AbcMetric', 'CyclomaticComplexity', 'MethodSize'])
    def execute(Map config, Closure stages) {
        logger.info "-> ODS global pipeline setup, debug mode? ${logger.debugMode} / ${config.debug}"
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

        prepareAgentPodConfig(config)
        logger.info "***** Starting ODS Pipeline (${config.componentId})*****"
        context = new Context(script, config, logger, this.localCheckoutEnabled)

        boolean skipCi = false
        def bootstrap = {
            try {
                script.wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
                    if (this.localCheckoutEnabled) {
                        script.checkout script.scm
                    }
                    script.stage('odsPipeline start') {
                        if (!config.containsKey('podContainers') && !config.image) {
                            config.image = "${script.env.DOCKER_REGISTRY}/${config.imageStreamTag}"
                        }
                        context.assemble()
                        // register services after context was assembled
                        logger.debug('Registering & loading global services')
                        def registry = ServiceRegistry.instance

                        // if we run in another context there is a good chance
                        // services have been already registered
                        if (!registry.get(GitService)) {
                            logger.debug 'Registering GitService'
                            registry.add(GitService, new GitService(script))
                        }
                        this.gitService = registry.get(GitService)

                        if (!registry.get(BitbucketService)) {
                            logger.debug 'Registering BitbucketService'
                            registry.add(BitbucketService, new BitbucketService(
                                script,
                                context.bitbucketUrl,
                                context.projectId,
                                context.credentialsId
                            ))
                        }
                        this.bitbucketService = registry.get(BitbucketService)

                        if (!registry.get(OpenShiftService)) {
                            logger.debug 'Registering OpenShiftService'
                            registry.add(OpenShiftService, new OpenShiftService(
                                script,
                                context.targetProject
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
                                context.nexusHost, context.nexusUsername, context.nexusPassword))
                        }
                    }

                    skipCi = isCiSkip()
                    if (skipCi) {
                        logger.info 'Skipping build due to [ci skip] in the commit message ...'
                        updateBuildStatus('NOT_BUILT')
                        setBitbucketBuildStatus('SUCCESSFUL')
                        return
                    }

                    if (context.environment) {
                        context.setOpenshiftApplicationDomain(
                            openShiftService.getOpenshiftApplicationDomain()
                        )

                        def autoCloneEnabled = !!context.cloneSourceEnv
                        if (autoCloneEnabled) {
                            createOpenShiftEnvironment(context)
                        }
                    }
                }
            } catch (err) {
                updateBuildStatus('FAILURE')
                setBitbucketBuildStatus('FAILED')
                if (notifyNotGreen) {
                    doNotifyNotGreen()
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
            script.podTemplate(
                label: config.podLabel,
                cloud: 'openshift',
                containers: config.podContainers,
                volumes: config.podVolumes,
                serviceAccount: config.podServiceAccount,
            ) {
                script.node(config.podLabel) {
                    try {
                        setBitbucketBuildStatus('INPROGRESS')
                        script.wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
                            gitService.checkout(
                                context.gitCommit,
                                [[credentialsId: context.credentialsId, url: context.gitUrl]]
                            )
                            if (this.displayNameUpdateEnabled) {
                                script.currentBuild.displayName = "#${context.tagversion}"
                            }

                            stages(context)
                        }
                        script.stage('odsPipeline finished') {
                            updateBuildStatus('SUCCESS')
                            setBitbucketBuildStatus('SUCCESSFUL')
                            logger.info "***** Finished ODS Pipeline for ${context.componentId} *****"
                        }
                        return this
                    } catch (err) {
                        script.stage('odsPipeline error') {
                            logger.info "***** Finished ODS Pipeline for ${context.componentId} (with error) *****"
                            script.echo("Error: ${err}")
                            updateBuildStatus('FAILURE')
                            setBitbucketBuildStatus('FAILED')
                            if (notifyNotGreen) {
                                doNotifyNotGreen()
                            }
                            if (!!script.env.MULTI_REPO_BUILD) {
                                context.addArtifactURI('failedStage', script.env.STAGE_NAME)
                                // this is the case on a parallel node to be interrupted
                                if (err instanceof org.jenkinsci.plugins.workflow.steps.FlowInterruptedException) {
                                    throw err
                                }
                                return this
                            } else {
                                throw err
                            }
                        }
                    } finally {
                        jenkinsService.stashTestResults(
                            context.testResults,
                            "${context.componentId}-${context.buildNumber}"
                        ).each { resultKey, resultValue ->
                            context.addArtifactURI(resultKey, resultValue)
                        }
                        logger.debug(
                            "ODS Build Artifacts '${context.componentId}': " +
                            "\r${JsonOutput.prettyPrint(JsonOutput.toJson(context.getBuildArtifactURIs()))}"
                        )
                    }
                }
            }
        }
    }

    def setupForMultiRepoBuild(def config) {
        logger.info '-> Detected multirepo MRO build'
        config.localCheckoutEnabled = false
        config.displayNameUpdateEnabled  = false
        config.ciSkipEnabled = false
        config.notifyNotGreen = false
        config.sonarQubeBranch = '*'
        def buildEnv = script.env.MULTI_REPO_ENV
        if (buildEnv) {
            config.environment = buildEnv
            logger.debug("Setting target env ${config.environment}")
        } else {
            logger.echo 'Variable MULTI_REPO_ENV (target environment!) must not be null!'
            // Using exception because error step would skip post steps
            throw new RuntimeException('Variable MULTI_REPO_ENV (target environment!) must not be null!')
        }
    }

    private void setBitbucketBuildStatus(String state) {
        if (!this.bitbucketNotificationEnabled) {
            return
        }
        if (!context.buildUrl || !context.gitCommit) {
            logger.info "Cannot set Bitbucket build status to '${state}' because required data is missing!"
            return
        }

        def buildName = "${context.gitCommit.take(8)}"
        bitbucketService.setBuildStatus(context.buildUrl, context.gitCommit, state, buildName)
    }

    private void doNotifyNotGreen() {
        String subject = "Build $context.componentId on project $context.projectId  failed!"
        String body = "<p>$subject</p> <p>URL : <a href=\"$context.buildUrl\">$context.buildUrl</a></p> "

        script.emailext(
            body: body, mimeType: 'text/html',
            replyTo: '$script.DEFAULT_REPLYTO', subject: subject,
            to: script.emailextrecipients([
                [$class: 'CulpritsRecipientProvider'],
                [$class: 'RequesterRecipientProvider'],
                [$class: 'UpstreamComitterRecipientProvider']
            ])
        )
    }

    def createOpenShiftEnvironment(def context) {
        script.stage('Create Openshift Environment') {
            if (!context.environment) {
                logger.info 'Skipping for empty environment ...'
                return
            }

            if (!!script.env.MULTI_REPO_BUILD) {
                logger.info 'MRO Build - skipping env mapping'
            } else {
                def assumedEnvironments = context.branchToEnvironmentMapping.values()
                def envExists = context.environmentExists(context.targetProject)
                logger.debug(
                    "context.environment: ${context.environment}, " +
                    "context.cloneSourceEnv: ${context.cloneSourceEnv}, " +
                    "context.targetProject: ${context.targetProject}, " +
                    "envExists: ${envExists}"
                )
                if (assumedEnvironments.contains(context.environment) && (envExists)) {
                    logger.info "Skipping for ${context.environment} environment based on ${assumedEnvironments} ..."
                    return
                }
            }

            if (context.environmentExists(context.targetProject)) {
                logger.info "Target environment $context.targetProject exists already ..."
                return
            }

            if (!context.environmentExists("${context.projectId.toLowerCase()}-${context.cloneSourceEnv}")) {
                logger.info "Source Environment ${context.cloneSourceEnv} DOES NOT EXIST, skipping ..."
                return
            }

            if (openShiftService.tooManyEnvironments(context.projectId, context.environmentLimit)) {
                script.error 'Cannot create OC project ' +
                    "as there are already ${context.environmentLimit} OC projects! " +
                    'Please clean up and run the pipeline again.'
            }

            logger.info 'Environment does not exist yet. Creating now ...'
            script.withCredentials(
                [script.usernameColonPassword(credentialsId: context.credentialsId, variable: 'USERPASS')]
            ) {
                def userPass = script.USERPASS.replace('$', '\'$\'')
                def branchName = "${script.env.JOB_NAME}-${script.env.BUILD_NUMBER}-${context.cloneSourceEnv}"
                logger.info "Calculated branch name: ${branchName}"
                def scriptToUrls = context.getCloneProjectScriptUrls()
                // NOTE: a for loop did not work here due to https://issues.jenkins-ci.org/browse/JENKINS-49732
                scriptToUrls.each { scriptName, url ->
                    script.sh(script: "curl --fail -s --user ${userPass} -G '${url}' -d raw -o '${scriptName}'")
                }
                def debugMode = ''
                if (context.getDebug()) {
                    debugMode = '--debug'
                }
                userPass = userPass.replace('@', '\\@')
                script.sh(
                    script: """sh clone-project.sh \
                        -o ${context.openshiftHost} \
                        -b ${context.bitbucketHost} \
                        -c ${userPass} \
                        -p ${context.projectId} \
                        -s ${context.cloneSourceEnv} \
                        -gb ${branchName} \
                        -t ${context.environment} ${debugMode}
                    """
                )
                logger.info 'Environment created!'
            }
        }
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

    public Map<String, Object> getBuildArtifactURIs() {
        return context.getBuildArtifactURIs()
    }

    // Whether the build should be skipped, based on the Git commit message.
    private boolean isCiSkip() {
        return this.ciSkipEnabled && gitService.ciSkipInCommitMessage
    }

    private def prepareAgentPodConfig(Map config) {
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
            // 2Gi is required for e.g. jenkins-slave-maven, which selects the Java
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
    }

    private void amendProjectAndComponentFromOrigin(Map config) {
        def block = {
            def origin
            try {
                origin = new GitService(script).getOriginUrl()
            } catch (err) {
                def jobSplitList = script.env.JOB_NAME.split('/')
                def projectName = jobSplitList[0]
                def bcName = jobSplitList[1].replace("${projectName}-", '')
                origin = new OpenShiftService(script, null).
                    getOriginUrlFromBuildConfig (projectName, bcName)
            }

            def splittedOrigin = origin.split('/')
            def project = splittedOrigin[splittedOrigin.size() - 2]
            if (!config.projectId) {
                config.projectId = project.trim()
            }
            if (!config.componentId) {
                config.componentId = splittedOrigin.last()
                    .replace('.git', '')
                    .replace("${project}-", '')
                    .trim()
            }
            logger.debug(
                "Project / component config from Git origin url: ${config.projectId} / ${config.componentId}"
            )
        }
        if (this.localCheckoutEnabled) {
            script.node('master', block)
        } else {
            block()
        }
    }

}
