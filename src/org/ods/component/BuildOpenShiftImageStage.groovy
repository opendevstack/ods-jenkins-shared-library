package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.util.ILogger

@SuppressWarnings('ParameterCount')
@TypeChecked
class BuildOpenShiftImageStage extends Stage {

    public final String STAGE_NAME = 'Build OpenShift Image'
    private final OpenShiftService openShift
    private final JenkinsService jenkins
    private final ILogger logger
    private final BuildOpenShiftImageOptions options

    @TypeChecked(TypeCheckingMode.SKIP)
    BuildOpenShiftImageStage(
        def script,
        IContext context,
        Map<String, Object> config,
        OpenShiftService openShift,
        JenkinsService jenkins,
        ILogger logger) {
        super(script, context, logger)
        // If user did not explicitly define which branches to build images for,
        // build images for all branches which are mapped (deployed) to an environment.
        // In orchestration pipelines, always build the image.
        if (!config.containsKey('branches') && !config.containsKey('branch')) {
            if (context.triggeredByOrchestrationPipeline) {
                config.branch = context.gitBranch
            } else {
                config.branches = context.branchToEnvironmentMapping.keySet().toList()
            }
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

        this.options = new BuildOpenShiftImageOptions(config)
        this.openShift = openShift
        this.jenkins = jenkins
        this.logger = logger
    }

    protected run() {
        findOrCreateBuildConfig()
        findOrCreateImageStream()

        def imageLabels = assembleImageLabels()
        writeReleaseFile(imageLabels, options.dockerDir)

        patchBuildConfig(imageLabels)

        def buildVersion = startAndFollowBuild()
        def buildId = "${options.resourceName}-${buildVersion}"

        // Retrieve build status.
        def buildStatus = getBuildStatus(buildId)
        if (buildStatus != 'complete') {
            steps.error "OpenShift Build #${buildVersion} was not successful - status is '${buildStatus}'."
        }

        def imageReference = getImageReference()
        logger.info "Build #${buildVersion} of '${options.resourceName}' has produced image: ${imageReference}."

        def info = [buildId: buildId.toString(), image: imageReference.toString()]
        context.addBuildToArtifactURIs(options.resourceName, info)

        return info
    }

    protected String stageLabel() {
        if (options.resourceName != context.componentId) {
            return "${STAGE_NAME} (${options.resourceName})"
        }
        STAGE_NAME
    }

    private int startAndFollowBuild() {
        int buildVersion = 0
        steps.timeout(time: options.buildTimeoutMinutes) {
            logger.startClocked("${options.resourceName}-build")
            buildVersion = openShift.startBuild(
                context.cdProject, options.resourceName, options.dockerDir
            )
            logger.info openShift.followBuild(
                context.cdProject, options.resourceName, buildVersion
            )
            logger.debugClocked("${options.resourceName}-build", (null as String))
        }
        buildVersion
    }

    private void findOrCreateBuildConfig() {
        try {
            openShift.findOrCreateBuildConfig(context.cdProject, options.resourceName)
        } catch (Exception ex) {
            steps.error "Could not find/create BuildConfig ${options.resourceName}. Error was: ${ex}"
        }
    }

    private void findOrCreateImageStream() {
        try {
            openShift.findOrCreateImageStream(context.cdProject, options.resourceName)
        } catch (Exception ex) {
            steps.error "Could not find/create ImageStream ${options.resourceName}. Error was: ${ex}"
        }
    }

    private String getImageReference() {
        openShift.getImageReference(context.cdProject, options.resourceName, options.imageTag)
    }

    private String getBuildStatus(String build) {
        openShift.getBuildStatus(context.cdProject, build)
    }

    private String patchBuildConfig(Map imageLabels) {
        openShift.patchBuildConfig(
            context.cdProject,
            options.resourceName,
            options.imageTag,
            options.buildArgs,
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
        for (def key : options.imageLabels.keySet()) {
            imageLabels["ext.${key}".toString()] = options.imageLabels[key]
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

    private writeReleaseFile(Map imageLabels, String dockerDir) {
        def jsonImageLabels = []
        for (def key : imageLabels.keySet()) {
            jsonImageLabels << """{"name": "${key}", "value": "${imageLabels[key]}"}"""
        }

        // Write docker/release.json file to be reachable from Dockerfile.
        steps.writeFile(
            file: "${dockerDir}/release.json",
            text: "[\n${jsonImageLabels.join(',\n')}\n]"
        )
    }

}
