package org.ods.component

import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.util.ILogger

@SuppressWarnings('ParameterCount')
class BuildOpenShiftImageStage extends Stage {

    public final String STAGE_NAME = 'Build OpenShift Image'
    private final OpenShiftService openShift
    private final JenkinsService jenkins
    private final ILogger logger

    BuildOpenShiftImageStage(
        def script,
        IContext context,
        Map config,
        OpenShiftService openShift,
        JenkinsService jenkins,
        ILogger logger) {
        super(script, context, config, logger)
        if (!config.resourceName) {
            config.resourceName = context.componentId
        }
        if (!config.imageTag) {
            config.imageTag = context.shortGitCommit
        }
        if (!config.imageLabels) {
            config.imageLabels = [:]
        }
        if (context.extensionImageLabels) {
            config.imageLabels << context.extensionImageLabels
        }
        if (!config.buildArgs) {
            config.buildArgs = [:]
        }
        if (!config.buildTimeoutMinutes) {
            config.buildTimeoutMinutes = context.openshiftBuildTimeout
        }
        if (!config.dockerDir) {
            config.dockerDir = context.dockerDir
        }
        if (!config.openshiftDir) {
            config.openshiftDir = 'openshift'
        }
        if (!config.tailorPrivateKeyCredentialsId) {
            config.tailorPrivateKeyCredentialsId = "${context.projectId}-cd-tailor-private-key"
        }
        if (!config.tailorSelector) {
            config.tailorSelector = "app=${context.projectId}-${context.componentId}"
        }
        if (!config.containsKey('tailorVerify')) {
            config.tailorVerify = false
        }
        if (!config.containsKey('tailorInclude')) {
            config.tailorInclude = 'bc,is'
        }
        if (!config.containsKey('tailorParamFile')) {
            config.tailorParamFile = '' // none apart the automatic param file
        }
        if (!config.containsKey('tailorPreserve')) {
            config.tailorPreserve = ['bc:/spec/output/imageLabels', 'bc:/spec/output/to/name']
        }
        if (!config.containsKey('tailorParams')) {
            config.tailorParams = []
        }
        this.openShift = openShift
        this.jenkins = jenkins
        this.logger = logger
    }

    protected run() {
        if (!context.environment) {
            logger.warn 'Skipping because of empty (target) environment ...'
            return [:]
        }

        if (script.fileExists(config.openshiftDir)) {
            script.dir(config.openshiftDir) {
                jenkins.maybeWithPrivateKeyCredentials(config.tailorPrivateKeyCredentialsId) { pkeyFile ->
                    openShift.tailorApply(
                        [selector: config.tailorSelector, include: config.tailorInclude],
                        config.tailorParamFile,
                        config.tailorParams,
                        config.tailorPreserve,
                        pkeyFile,
                        config.tailorVerify
                    )
                }
            }
        }

        def imageLabels = assembleImageLabels()
        writeReleaseFile(imageLabels, config)

        if (!buildConfigExists()) {
            script.error "BuildConfig '${config.resourceName}' does not exist. " +
                'Verify that you have setup the OpenShift resource correctly ' +
                'and/or set the "resourceName" option of the pipeline stage appropriately.'
        }
        patchBuildConfig(imageLabels)

        // Start and follow build of container image.
        int buildVersion = 0
        script.timeout(time: config.buildTimeoutMinutes) {
            logger.startClocked("${config.resourceName}-build")
            buildVersion = startBuild()
            logger.info followBuild(buildVersion)
            logger.debugClocked("${config.resourceName}-build")
        }

        // Retrieve build status.
        def lastVersion = buildVersion
        def buildId = "${config.resourceName}-${lastVersion}"
        def buildStatus = getBuildStatus(buildId)
        if (buildStatus != 'complete') {
            script.error "OpenShift Build #${lastVersion} was not successful - status is '${buildStatus}'."
        }
        def imageReference = getImageReference()
        logger.info "Build #${lastVersion} of '${config.resourceName}' has produced image: ${imageReference}."

        context.addBuildToArtifactURIs(
            config.resourceName,
            [
                buildId: buildId,
                image: imageReference,
            ]
        )

        return [
            buildId: buildId,
            image: imageReference,
        ]
    }

    protected String stageLabel() {
        if (config.resourceName != context.componentId) {
            return "${STAGE_NAME} (${config.resourceName})"
        }
        STAGE_NAME
    }

    private boolean buildConfigExists() {
        openShift.resourceExists('BuildConfig', config.resourceName)
    }

    private String getImageReference() {
        openShift.getImageReference(config.resourceName, config.imageTag)
    }

    private String startAndFollowBuild() {
        openShift.startAndFollowBuild(config.resourceName, config.dockerDir)
    }

    private int startBuild() {
        openShift.startBuild(config.resourceName, config.dockerDir)
    }

    private String followBuild(int version) {
        openShift.followBuild(config.resourceName, version)
    }

    private int getLastVersion() {
        openShift.getLastBuildVersion(config.resourceName)
    }

    private String getBuildStatus(String build) {
        openShift.getBuildStatus(build)
    }

    private String patchBuildConfig(Map imageLabels) {
        openShift.patchBuildConfig(
            config.resourceName,
            config.imageTag,
            config.buildArgs,
            imageLabels
        )
    }

    private Map assembleImageLabels() {
        def imageLabels = [
            'ods.build.source.repo.url': context.gitUrl,
            'ods.build.source.repo.commit.sha': context.gitCommit,
            'ods.build.source.repo.commit.msg': context.gitCommitMessage,
            'ods.build.source.repo.commit.author': context.gitCommitAuthor,
            'ods.build.source.repo.commit.timestamp': context.gitCommitTime,
            'ods.build.source.repo.branch': context.gitBranch,
            'ods.build.jenkins.job.url': context.buildUrl,
            'ods.build.timestamp': context.buildTime,
            'ods.build.lib.version': context.odsSharedLibVersion,
        ]
        // Add custom image labels (prefixed with ext).
        for (def key : config.imageLabels.keySet()) {
            imageLabels["ext.${key}"] = config.imageLabels[key]
        }
        // Sanitize image labels.
        def sanitizedImageLabels = [:]
        for (def key : imageLabels.keySet()) {
            def val = imageLabels[key].toString()
            def sanitizedVal = val.replaceAll('[\r\n]+', ' ').trim().replaceAll('["\']+', '')
            sanitizedImageLabels[key] = sanitizedVal
        }
        sanitizedImageLabels
    }

    private writeReleaseFile(Map imageLabels, Map config) {
        def jsonImageLabels = []
        for (def key : imageLabels.keySet()) {
            jsonImageLabels << """{"name": "${key}", "value": "${imageLabels[key]}"}"""
        }

        // Write docker/release.json file to be reachable from Dockerfile.
        script.writeFile(
            file: "${config.dockerDir}/release.json",
            text: "[\n${jsonImageLabels.join(',\n')}\n]"
        )
    }

}
