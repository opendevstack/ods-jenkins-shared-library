package org.ods.quickstarter

class Pipeline implements Serializable {

    private def script
    private Map config

    Pipeline(def script, Map config) {
        this.script = script
        this.config = config
    }

    @SuppressWarnings('AbcMetric')
    def execute(Closure block) {
        // build params
        checkRequiredBuildParams()
        config.odsNamespace = script.env.ODS_NAMESPACE ?: 'ods'
        config.odsImageTag = script.env.ODS_IMAGE_TAG ?: 'latest'
        config.odsGitRef = script.env.ODS_GIT_REF ?: 'production'
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
            config.targetDir = config.componentId
        }
        if (!config.podVolumes) {
            config.podVolumes = []
        }

        // vars from jenkins master
        def gitHost
        script.node {
            gitHost =  script.env.BITBUCKET_HOST.split(":")[0]
            config.jobName = script.env.JOB_NAME
            config.buildNumber = script.env.BUILD_NUMBER
            config.buildUrl = script.env.BUILD_URL
            config.buildTime = new Date()
            config.dockerRegistry = script.env.DOCKER_REGISTRY

            // get nexus params
            config.nexusHost = script.env.NEXUS_HOST
            config.nexusUsername = script.env.NEXUS_USERNAME
            config.nexusPassword = script.env.NEXUS_PASSWORD
        }

        onAgentNode(config) { context ->
            new CheckoutStage(script, context).execute()
            new CreateOutputDirectoryStage(script, context).execute()

            // Execute user-defined stages.
            block(context)

            new PushToRemoteStage(script, context, [gitHost: gitHost]).execute()
        }
    }

    private def checkRequiredBuildParams() {
        def requiredParams = ['PROJECT_ID', 'COMPONENT_ID', 'GIT_URL_HTTP']
        for (def i = 0; i < requiredParams.size(); i++) {
            def param = requiredParams[i]
            if (!script.env[param]) {
                script.error "Build param '${param}' is required but not given!"
            }
        }
    }

    private def onAgentNode(Map config, Closure block) {
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
        }

        def podLabel = "quickstarter-${config.sourceDir}-${config.projectId}-${config.componentId}"

        script.podTemplate(
            label: podLabel,
            cloud: 'openshift',
            containers: config.podContainers,
            volumes: config.podVolumes,
            serviceAccount: config.podServiceAccount
        ) {
            script.node(podLabel) {
                IContext context = new Context(config)
                block(context)
            }
        }
    }
}
