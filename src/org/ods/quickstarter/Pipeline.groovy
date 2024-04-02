package org.ods.quickstarter

import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.util.IPipelineSteps
import org.ods.util.PipelineSteps

import org.ods.util.Logger
import org.ods.util.ILogger

class Pipeline implements Serializable {

    private final def script
    private final Map config
    private final ILogger logger
    private final IPipelineSteps steps
    private OpenShiftService openShiftService

    Pipeline(def script, Map config) {
        this.script = script
        this.config = config
        this.logger = new Logger(script, false)
        this.steps = new PipelineSteps(script)
    }

    @SuppressWarnings(['AbcMetric', 'UnnecessaryObjectReferences'])
    def execute(Closure block) {
        // build params
        checkRequiredBuildParams()
        config.odsNamespace = script.env.ODS_NAMESPACE ?: 'ods'
        config.odsImageTag = script.env.ODS_IMAGE_TAG ?: 'latest'
        config.odsGitRef = script.env.ODS_GIT_REF ?: 'master'
        config.agentImageTag = script.env.AGENT_IMAGE_TAG ?: config.odsImageTag
        config.sharedLibraryRef = script.env.SHARED_LIBRARY_REF ?: config.odsImageTag
        config.projectId = script.env.PROJECT_ID.toLowerCase()
        config.componentId = script.env.COMPONENT_ID.toLowerCase()
        config.gitUrlHttp = script.env.GIT_URL_HTTP
        config.packageName = script.env.PACKAGE_NAME
        config.group = script.env.GROUP_ID
        config.openShiftProject = "${config.projectId}-cd"

        // config options
        if (!config.sourceDir) {
            // Extract folder name from e.g. "be-golang-plain/Jenkinsfile"
            def jenkinsfilePath = script.currentBuild?.rawBuild?.parent?.definition?.scriptPath.toString()
            def jenkinsfilePathParts = jenkinsfilePath.split('/')
            if (jenkinsfilePathParts.size() >= 2) {
                config.sourceDir = jenkinsfilePathParts[-2]
            } else {
                script.error "Config option 'sourceDir' is required but not given!"
            }
        }
        if (!config.image && !config.imageStreamTag && !config.podContainers) {
            script.error "One of 'image', 'imageStreamTag' or 'podContainers' is required but not given!"
        }
        if (!config.cdUserCredentialsId) {
            config.cdUserCredentialsId = "${config.openShiftProject}-cd-user-with-password"
        }
        if (!config.targetDir) {
            // Use componentId as some build tools (e.g. sbt) need to work in a directory
            // having the same name as the application they target.
            config.targetDir = config.componentId
        }
        if (!config.podVolumes) {
            config.podVolumes = []
        }
        if (!config.annotations) {
            config.annotations = [
                script.podAnnotation(
                    key: 'cluster-autoscaler.kubernetes.io/safe-to-evict', value: 'false'
                    )
                ]
        }

        // vars from jenkins master
        script.node {
            config.jobName = script.env.JOB_NAME
            config.buildNumber = script.env.BUILD_NUMBER
            config.buildUrl = script.env.BUILD_URL
            config.buildTime = new Date()
            config.dockerRegistry = script.env.DOCKER_REGISTRY ?: 'image-registry.openshift-image-registry.svc:5000'
            config << BitbucketService.readConfigFromEnv(script.env)
            config << NexusService.readConfigFromEnv(script.env)
        }

        onAgentNode(config) { context ->
            new CheckoutStage(script, context).execute()
            new CreateOutputDirectoryStage(script, context).execute()

            // Execute user-defined stages.
            block(context)

            new PushToRemoteStage(script, context).execute()
        }
    }

    private checkRequiredBuildParams() {
        def requiredParams = ['PROJECT_ID', 'COMPONENT_ID', 'GIT_URL_HTTP']
        for (def i = 0; i < requiredParams.size(); i++) {
            def param = requiredParams[i]
            if (!script.env[param]) {
                script.error "Build param '${param}' is required but not given!"
            }
        }
    }

    // private appDomain() {
    //     if (config.openShiftProject) {
    //         logger.startClocked("${config.componentId}-get-oc-app-domain")

    //         this.openShiftService = new OpenShiftService(steps, logger)
    //         config.appDomain = openShiftService.getApplicationDomain("${config.projectId}")

    //         logger.debugClocked("${config.componentId}-get-oc-app-domain")
    //     } else {
    //         logger.debug('Could not get application domain, as no openShiftProject is available')
    //     }
    // }

    private onAgentNode(Map config, Closure block) {
        if (!config.podContainers) {
            if (!config.containsKey('alwaysPullImage')) {
                config.alwaysPullImage = true
            }
            if (!config.podServiceAccount) {
                config.podServiceAccount = 'jenkins'
            }
            if (!config.resourceRequestMemory) {
                config.resourceRequestMemory = '512Mi'
            }
            if (!config.resourceLimitMemory) {
                config.resourceLimitMemory = '1Gi'
            }
            if (!config.resourceRequestCpu) {
                config.resourceRequestCpu = '100m'
            }
            if (!config.resourceLimitCpu) {
                config.resourceLimitCpu = '1'
            }
            if (!config.image) {
                config.image = "${config.dockerRegistry}/${config.imageStreamTag}"
            }
            config.podContainers = [
                script.containerTemplate(
                    name: 'jnlp',
                    image: config.image,
                    workingDir: '/tmp',
                    alwaysPullImage: config.alwaysPullImage,
                    resourceRequestMemory: config.resourceRequestMemory,
                    resourceLimitMemory: config.resourceLimitMemory,
                    resourceRequestCpu: config.resourceRequestCpu,
                    resourceLimitCpu: config.resourceLimitCpu,
                    args: ''
                )
            ]
            logger.debug "Setting App Domain......."
            if (!config.appDomain) {
                if (config.openShiftProject) {
                    logger.startClocked("${config.componentId}-get-oc-app-domain")

                    this.openShiftService = new OpenShiftService(steps, logger)
                    config.appDomain = openShiftService.getApplicationDomain("${config.projectId}")

                    logger.debugClocked("${config.componentId}-get-oc-app-domain")
                } else {
                    logger.debug('Could not get application domain, as no openShiftProject is available')
                }
            }else {
                logger.debug("Using application domain '${config.appDomain}'")
            }

        }

        def podLabel = "qs-${UUID.randomUUID()}"

        script.podTemplate(
            label: podLabel,
            cloud: 'openshift',
            containers: config.podContainers,
            volumes: config.podVolumes,
            serviceAccount: config.podServiceAccount,
            annotations: config.annotations,
        ) {
            script.node(podLabel) {
                // IContext context = new Context(config, logger, script)
                IContext context = new Context(config)
                // context.setAppDomain()
                block(context)
            }
        }
    }

}
