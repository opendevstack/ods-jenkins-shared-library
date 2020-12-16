package org.ods.component

import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.util.ILogger
import org.ods.util.PodData
import org.ods.util.RegistryAccessInfo
import org.ods.util.TargetProjectConfig
import org.yaml.snakeyaml.Yaml

@SuppressWarnings('ParameterCount')
class RolloutOpenShiftDeploymentStage extends Stage {

    public final String STAGE_NAME = 'Deploy to OpenShift'
    private final List<String> DEPLOYMENT_KINDS = [
        OpenShiftService.DEPLOYMENT_KIND, OpenShiftService.DEPLOYMENTCONFIG_KIND,
    ]
    private final OpenShiftService openShift
    private final JenkinsService jenkins

    @SuppressWarnings('CyclomaticComplexity')
    RolloutOpenShiftDeploymentStage(
        def script,
        IContext context,
        Map config,
        OpenShiftService openShift,
        JenkinsService jenkins,
        ILogger logger) {
        super(script, context, config, logger)
        if (!config.selector) {
            config.selector = context.selector
        }
        if (!config.imageTag) {
            config.imageTag = context.shortGitCommit
        }
        if (!config.deployTimeoutMinutes) {
            config.deployTimeoutMinutes = context.openshiftRolloutTimeout ?: 5
        }
        if (!config.deployTimeoutRetries) {
            config.deployTimeoutRetries = context.openshiftRolloutTimeoutRetries ?: 5
        }
        // Helm options
        if (!config.chartDir) {
            config.chartDir = 'chart'
        }
        if (!config.containsKey('helmReleaseName')) {
            config.helmReleaseName = context.componentId
        }
        if (!config.containsKey('helmValues')) {
            config.helmValues = [:]
        }
        if (!config.containsKey('helmValuesFiles')) {
            config.helmValuesFiles = []
        }
        if (!config.containsKey('helmDefaultFlags')) {
            config.helmDefaultFlags = ['--install', '--atomic']
        }
        if (!config.containsKey('helmAdditionalFlags')) {
            config.helmAdditionalFlags = []
        }
        if (!config.containsKey('helmDiff')) {
            config.helmDiff = true
        }
        if (!config.helmPrivateKeyCredentialsId) {
            config.helmPrivateKeyCredentialsId = "${context.cdProject}-helm-private-key"
        }
        // Tailor options
        if (!config.openshiftDir) {
            config.openshiftDir = 'openshift'
        }
        if (!config.tailorPrivateKeyCredentialsId) {
            config.tailorPrivateKeyCredentialsId = "${context.cdProject}-tailor-private-key"
        }
        if (!config.tailorSelector) {
            config.tailorSelector = config.selector
        }
        if (!config.containsKey('tailorVerify')) {
            config.tailorVerify = false
        }
        if (!config.containsKey('tailorExclude')) {
            config.tailorExclude = 'bc,is'
        }
        if (!config.containsKey('tailorParamFile')) {
            config.tailorParamFile = '' // none apart the automatic param file
        }
        if (!config.containsKey('tailorPreserve')) {
            config.tailorPreserve = [] // do not preserve any paths in live config
        }
        if (!config.containsKey('tailorParams')) {
            config.tailorParams = []
        }
        this.openShift = openShift
        this.jenkins = jenkins
    }

    protected run() {
        if (!context.environment) {
            logger.warn 'Skipping because of empty (target) environment ...'
            return
        }
        if (context.triggeredByOrchestrationPipeline && config.environmentsConfigMap) {
            script.error(
                "Environment configuration via 'environmentsConfigMap' is not supported " +
                "in the orchestration pipeline yet."
            )
            return
        }

        // Transport images built in this pipeline run only
        def images = context.buildArtifactURIs.builds.keySet()

        Map<String, Map<String, List<PodData>>> rolloutDataByTarget = [:]
        def deployTargets = getDeployTargets()
        RegistryAccessInfo srcRegistry
        if (deployTargets.any { k, v -> v.isExternalCluster() }) {
            srcRegistry = getSourceRegistrySecret()
        }
        deployTargets.each { k, v ->
            rolloutDataByTarget[k] = deployToTarget(k, v, images, srcRegistry)
        }
        rolloutDataByTarget
    }

    protected String stageLabel() {
        if (config.selector != context.selector) {
            return "${STAGE_NAME} (${config.selector})"
        }
        STAGE_NAME
    }

    private RegistryAccessInfo getSourceRegistrySecret() {
        def srcRegistrySecret = config.sourceRegistrySecret
        if (!srcRegistrySecret) {
            srcRegistrySecret = openShift.getRegistrySecretOfServiceAccount(context.cdProject, 'jenkins')
        }
        openShift.getRegistryAccessInfo(context.cdProject, srcRegistrySecret)
    }

    private Map<String, List<PodData>> deployToTarget(
        String targetName,
        TargetProjectConfig targetConfig,
        Set<String> images,
        RegistryAccessInfo srcRegistry) {
        Map<String, List<PodData>> rolloutData
        def targetProject = targetConfig.namespace
        logger.info "Deploying to '${targetName}' (project '${targetProject}') ..."
        withOpenShiftCluster(targetConfig) {
            def deploymentResources = openShift.getResourcesForComponent(
                targetProject, DEPLOYMENT_KINDS, config.selector
            )
            if (context.triggeredByOrchestrationPipeline
                && deploymentResources.containsKey(OpenShiftService.DEPLOYMENT_KIND)) {
                script.error "Deployment resources cannot be used in the orchestration pipeline yet."
                return
            }
            def originalDeploymentVersions = fetchOriginalVersions(targetProject, deploymentResources)

            if (targetConfig.isLocalCluster()) {
                retagImages(targetProject, images)
            } else {
                pushImages(targetProject, targetConfig, srcRegistry, images)
            }

            def refreshResources = false
            if (script.fileExists("${config.chartDir}/Chart.yaml")) {
                if (context.triggeredByOrchestrationPipeline) {
                    script.error "Helm cannot be used in the orchestration pipeline yet."
                    return
                }
                script.withEnv(["TARGET=${targetName}"]) {
                    helmUpgrade(context.targetProject)
                }
                refreshResources = true
            } else if (script.fileExists(config.openshiftDir)) {
                script.withEnv(["TARGET=${targetName}"]) {
                    tailorApply(context.targetProject)
                }
                refreshResources = true
            }

            if (refreshResources) {
                deploymentResources = openShift.getResourcesForComponent(
                    context.targetProject, DEPLOYMENT_KINDS, config.selector
                )
            }

            rolloutData = rollout(targetProject, deploymentResources, originalDeploymentVersions)
        }

        rolloutData
    }

    private void withOpenShiftCluster(TargetProjectConfig targetConfig, Closure block) {
        if (targetConfig.apiUrl) {
            script.withOpenShiftCluster(
                targetConfig.apiUrl,
                "${context.cdProject}-${targetConfig.apiCredentialsSecret}".toString(),
                block
            )
        } else {
            block()
        }
    }

    private void tailorApply(String targetProject) {
        script.dir(config.openshiftDir) {
            jenkins.maybeWithPrivateKeyCredentials(config.tailorPrivateKeyCredentialsId) { pkeyFile ->
                openShift.tailorApply(
                    targetProject,
                    [selector: config.tailorSelector, exclude: config.tailorExclude],
                    config.tailorParamFile,
                    config.tailorParams,
                    config.tailorPreserve,
                    pkeyFile,
                    config.tailorVerify
                )
            }
        }
    }

    private void helmUpgrade(String targetProject) {
        script.dir(config.chartDir) {
            jenkins.maybeWithPrivateKeyCredentials(config.helmPrivateKeyCredentialsId) { pkeyFile ->
                if (pkeyFile) {
                    script.sh(script: "gpg --import ${pkeyFile}", label: 'Import private key into keyring')
                }
                config.helmValues.imageTag = config.imageTag
                openShift.helmUpgrade(
                    targetProject,
                    config.helmReleaseName,
                    config.helmValuesFiles,
                    config.helmValues,
                    config.helmDefaultFlags,
                    config.helmAdditionalFlags,
                    config.helmDiff
                )
            }
        }
    }

    private Map<String, TargetProjectConfig> getDeployTargets() {
        def environmentConfig = [:]
        if (config.environmentsConfigMap) {
            environmentConfig = getEnvironmentConfig(context.environment)
            if (!environmentConfig) {
                logger.info("No target project configured for '${context.environment}'.")
                return environmentConfig
            }
        } else {
            logger.info("environmentsConfigMap not configured, using defaults ...")
            environmentConfig.local = new TargetProjectConfig(namespace: context.targetProject)
        }
        environmentConfig
    }

    private Map<String, TargetProjectConfig> getEnvironmentConfig(String targetEnvironment) {
        if (!openShift.resourceExists(context.cdProject, "cm", config.environmentsConfigMap)) {
            script.error("environmentsConfigMap '${config.environmentsConfigMap}' does not exist")
        }
        def configMapContent = openShift.getConfigMapDataKey(
            context.cdProject, config.environmentsConfigMap, targetEnvironment
        )
        if (!configMapContent) {
            return [:]
        }
        def envConfig = [:]
        def envConfigMap = new Yaml().load(configMapContent)
        envConfigMap.each { targetName, targetProjectConfigMap ->
            def targetProjectConfig = new TargetProjectConfig(targetProjectConfigMap)
            logger.info("Gathered configuration for target '${targetName}': ${targetProjectConfig}")
            def validationError = targetProjectConfig.validate()
            if (validationError) {
                script.error(
                    "environmentsConfigMap '${config.environmentsConfigMap}' is invalid: " +
                    "target '${targetEnvironment}/${targetName}' has error: ${validationError}."
                )
                return
            }
            envConfig[targetName] = targetProjectConfig
        }
        envConfig
    }

    private void retagImages(String targetProject, Set<String> images) {
        images.each { image ->
            findOrCreateImageStream(targetProject, image)
            openShift.importImageTagFromProject(
                targetProject, image, context.cdProject, config.imageTag, config.imageTag
            )
        }
    }

    private void pushImages(
        String targetProject,
        TargetProjectConfig targetConfig,
        RegistryAccessInfo srcRegistry,
        Set<String> images) {
        def destRegistrySecret = targetConfig.registrySecret
        if (!destRegistrySecret) {
            destRegistrySecret = openShift.getRegistrySecretOfServiceAccount(targetProject, 'builder')
        }
        def destRegistry = openShift.getRegistryAccessInfo(targetProject, destRegistrySecret)
        images.each { image ->
            findOrCreateImageStream(targetProject, image)
            def srcStream = "${srcRegistry.host}/${context.cdProject}"
            def destStream = "${targetConfig.registryHost}/${targetProject}"
            logger.info("Pushing image from ${srcStream} to ${destStream} via skopeo ...")
            openShift.pushImage(
                "docker://${srcStream}/${image}:${config.imageTag}",
                "docker://${destStream}/${image}:${config.imageTag}",
                srcRegistry.credentials,
                destRegistry.credentials,
                targetConfig.skopeoAdditionalFlags
            )
        }
    }

    private findOrCreateImageStream(String targetProject, String image) {
        try {
            openShift.findOrCreateImageStream(targetProject, image)
        } catch (Exception ex) {
            script.error "Could not find/create ImageStream ${image} in ${targetProject}. Error was: ${ex}"
        }
    }

    // rollout returns a map like this:
    // [
    //    'DeploymentConfig/foo': [[podName: 'foo-a', ...], [podName: 'foo-b', ...]],
    //    'Deployment/bar': [[podName: 'bar-a', ...]]
    // ]
    private Map<String, List<PodData>> rollout(
        String targetProject,
        Map<String, List<String>> deploymentResources,
        Map<String, Map<String, Integer>> originalVersions) {
        def rolloutData = [:]
        deploymentResources.each { resourceKind, resourceNames ->
            resourceNames.each { resourceName ->
                def originalVersion = 0
                if (originalVersions.containsKey(resourceKind)) {
                    originalVersion = originalVersions[resourceKind][resourceName] ?: 0
                }

                def podData = rolloutDeployment(targetProject, resourceKind, resourceName, originalVersion)
                rolloutData["${resourceKind}/${resourceName}"] = podData
                // TODO: Once the orchestration pipeline can deal with multiple replicas,
                // update this to store multiple pod artifacts.
                // TODO: Potential conflict if resourceName is duplicated between
                // Deployment and DeploymentConfig resource.
                context.addDeploymentToArtifactURIs(resourceName, podData[0]?.toMap())
            }
        }
        rolloutData
    }

    private List<PodData> rolloutDeployment(
        String targetProject,
        String resourceKind,
        String resourceName,
        int originalVersion) {
        def ownedImageStreams = openShift
            .getImagesOfDeployment(targetProject, resourceKind, resourceName)
            .findAll { targetProject == it.repository }
        def missingStreams = missingImageStreams(targetProject, ownedImageStreams)
        if (missingStreams) {
            script.error "The following ImageStream resources  for ${resourceKind} '${resourceName}' " +
                """do not exist: '${missingStreams.collect { "${it.repository}/${it.name}" }}'. """ +
                'Verify that you have setup the OpenShift resources correctly.'
        }

        setImageTagLatest(targetProject, ownedImageStreams)

        String podManager
        try {
            podManager = openShift.rollout(
                targetProject,
                resourceKind,
                resourceName,
                originalVersion,
                config.deployTimeoutMinutes
            )
        } catch (ex) {
            script.error ex.message
            return []
        }

        return openShift.getPodDataForDeployment(
            targetProject,
            resourceKind,
            podManager,
            config.deployTimeoutRetries
        )
    }

    private Map<String, Map<String, Integer>> fetchOriginalVersions(
        String targetProject,
        Map<String, List<String>> deploymentResources) {
        def originalVersions = [:]
        deploymentResources.each { resourceKind, resourceNames ->
            if (!originalVersions.containsKey(resourceKind)) {
                originalVersions[resourceKind] = [:]
            }
            resourceNames.each { resourceName ->
                originalVersions[resourceKind][resourceName] = openShift.getRevision(
                    targetProject, resourceKind, resourceName
                )
            }
        }
        originalVersions
    }

    private List<Map<String, String>> missingImageStreams(
        String targetProject,
        List<Map<String, String>> imageStreams) {
        imageStreams.findAll { !openShift.resourceExists(targetProject, 'ImageStream', it.name) }
    }

    private void setImageTagLatest(String targetProject, List<Map<String, String>> imageStreams) {
        imageStreams.each { openShift.setImageTag(targetProject, it.name, config.imageTag, 'latest') }
    }

}
