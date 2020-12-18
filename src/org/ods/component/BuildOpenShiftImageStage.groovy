package org.ods.component

import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.util.ILogger

@SuppressWarnings('ParameterCount')
class BuildOpenShiftImageStage extends Stage {

    public final String STAGE_NAME = 'Build OpenShift Image'
    private final OpenShiftService openShift
    private final JenkinsService jenkins

    BuildOpenShiftImageStage(
        def script,
        IContext context,
        Map config,
        ILogger logger) {
        super(script, context, config, logger)
        // If user did not explicitly define which branches to build images for,
        // build images for all branches which are mapped (deployed) to an environment.
        if (!config.containsKey('branches') && !config.containsKey('branch')) {
            config.branches = context.branchToEnvironmentMapping.keySet().toList()
        }
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
        this.openShift = context.getOpenShiftService()
        this.jenkins = context.getJenkinsService()
    }

    protected run() {
        findOrCreateBuildConfig()
        findOrCreateImageStream()

        def imageLabels = assembleImageLabels()
        writeReleaseFile(imageLabels, config)

        patchBuildConfig(imageLabels)

        def buildVersion = startAndFollowBuild()
        def buildId = "${config.resourceName}-${buildVersion}"

        // Retrieve build status.
        def buildStatus = getBuildStatus(buildId)
        if (buildStatus != 'complete') {
            script.error "OpenShift Build #${buildVersion} was not successful - status is '${buildStatus}'."
        }

        def imageReference = getImageReference()
        logger.info "Build #${buildVersion} of '${config.resourceName}' has produced image: ${imageReference}."

        def info = [buildId: buildId, image: imageReference]
        context.addBuildToArtifactURIs(config.resourceName, info)

        return info
    }

    protected String stageLabel() {
        if (config.resourceName != context.componentId) {
            return "${STAGE_NAME} (${config.resourceName})"
        }
        STAGE_NAME
    }

    private int startAndFollowBuild() {
        int buildVersion = 0
        script.timeout(time: config.buildTimeoutMinutes) {
            logger.startClocked("${config.resourceName}-build")
            buildVersion = openShift.startBuild(
                context.cdProject, config.resourceName, config.dockerDir
            )
            logger.info openShift.followBuild(
                context.cdProject, config.resourceName, buildVersion
            )
            logger.debugClocked("${config.resourceName}-build")
        }
        buildVersion
    }

    private void findOrCreateBuildConfig() {
        try {
            openShift.findOrCreateBuildConfig(context.cdProject, config.resourceName)
        } catch (Exception ex) {
            script.error "Could not find/create BuildConfig ${config.resourceName}. Error was: ${ex}"
        }
    }

    private void findOrCreateImageStream() {
        try {
            openShift.findOrCreateImageStream(context.cdProject, config.resourceName)
        } catch (Exception ex) {
            script.error "Could not find/create ImageStream ${config.resourceName}. Error was: ${ex}"
        }
    }

    private String getImageReference() {
        openShift.getImageReference(context.cdProject, config.resourceName, config.imageTag)
    }

    private String getBuildStatus(String build) {
        openShift.getBuildStatus(context.cdProject, build)
    }

    private String patchBuildConfig(Map imageLabels) {
        openShift.patchBuildConfig(
            context.cdProject,
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
