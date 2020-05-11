package org.ods.component

import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService

class BuildOpenShiftImageStage extends Stage {

    public final String STAGE_NAME = 'Build Openshift Image'
    private final OpenShiftService openShift
    private final JenkinsService jenkins

    BuildOpenShiftImageStage(
        def script,
        IContext context,
        Map config,
        OpenShiftService openShift,
        JenkinsService jenkins) {
        super(script, context, config)
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
            config.tailorSelector = "app=${context.projectId}-${componentId}"
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
            config.tailorParams = ["TAGVERSION=${context.tagversion}"]
        }
        this.openShift = openShift
        this.jenkins = jenkins
    }

    def run() {
        if (!context.environment) {
            script.echo 'Skipping for empty environment ...'
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
        patchBuildConfig(imageLabels)

        // Start and follow build of container image.
        script.timeout(time: config.buildTimeoutMinutes) {
            script.echo startAndFollowBuild()
        }

        // Retrieve build status.
        def lastVersion = getLastVersion()
        if (!lastVersion) {
            script.error "Could not get last version of BuildConfig '${componentId}'."
        }
        def buildId = "${componentId}-${lastVersion}"
        def buildStatus = getBuildStatus(buildId)
        if (buildStatus != 'complete') {
            script.error "OpenShift Build #${lastVersion} was not successful - status is '${buildStatus}'."
        }
        def imageReference = getImageReference()
        script.echo "Build #${lastVersion} has produced image: ${imageReference}."

        context.addBuildToArtifactURIs(
            componentId,
            [buildId: buildId, image: imageReference,]
        )

        return [buildId: buildId, image: imageReference,]
    }

    private String getImageReference() {
        openShift.getImageReference(componentId, context.tagversion)
    }

    private String startAndFollowBuild() {
        openShift.startAndFollowBuild(componentId, config.dockerDir)
    }

    private String getLastVersion() {
        openShift.getLastBuildVersion(componentId)
    }

    private String getBuildStatus(String build) {
        openShift.getBuildStatus(build)
    }

    private String patchBuildConfig(Map imageLabels) {
        openShift.patchBuildConfig(
            componentId,
            context.tagversion,
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
            def sanitizedVal = val.replaceAll("[\r\n]+", ' ').trim().replaceAll("[\"']+", '')
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
        script.writeFile(file: "${config.dockerDir}/release.json", text: "[\n" + jsonImageLabels.join(",\n") + "\n]")
    }

}
