package org.ods.component

import org.ods.services.OpenShiftService

class BuildOpenShiftImageStage extends Stage {
  protected String STAGE_NAME = 'Build Openshift Image'
  protected OpenShiftService openShift

  BuildOpenShiftImageStage(def script, IContext context, Map config, OpenShiftService openShift) {
    super(script, context, config)
    if (!config.imageLabels) {
      config.imageLabels = [:]
    }
    script.echo ("Extension labels: ${context.getExtensionImageLabels()}")
    if (context.getExtensionImageLabels()?.size > 0) {
      config.imageLabels.putAll(context.getExtensionImageLabels())
    }
    
    if (!config.buildArgs) {
      config.buildArgs = [:]
    }
    if (!config.buildTimeoutMinutes) {
      config.buildTimeoutMinutes = context.openshiftBuildTimeout
    }
    this.openShift = openShift
  }

  def run() {
    if (!context.environment) {
      script.echo "Skipping for empty environment ..."
      return
    }

    def imageLabels = buildImageLabels()
    writeReleaseFile(imageLabels)
    patchBuildConfig(imageLabels)

    // Start and follow build of container image.
    script.timeout(time: config.buildTimeoutMinutes) {
      script.echo startAndFollowBuild()
    }

    // Retrieve build status.
    def lastVersion = getLastVersion()
    if (!lastVersion) {
      script.error "Could not get last version of BuildConfig '${context.componentId}'."
    }
    def buildId = "${context.componentId}-${lastVersion}"
    def buildStatus = getBuildStatus(buildId)
    if (buildStatus != 'complete') {
      script.error "OpenShift Build #${lastVersion} was not successful - status is '${buildStatus}'."
    }
    context.addArtifactURI("OCP Build Id", buildId)

    def imageReference = getImageReference()
    script.echo "Build #${lastVersion} has produced image: ${imageReference}."
    context.addArtifactURI("OCP Docker image", imageReference)
  }

    private String getImageReference() {
      openShift.getImageReference(context.componentId, context.tagversion)
    }

    private String startAndFollowBuild() {
      openShift.startAndFollowBuild(context.componentId, context.dockerDir)
    }

    private String getLastVersion() {
      openShift.getLastBuildVersion(context.componentId)
    }

    private String getBuildStatus(String build) {
      openShift.getBuildStatus(build)
    }

    private String patchBuildConfig(Map imageLabels) {
      openShift.patchBuildConfig(
        context.componentId,
        context.tagversion,
        config.buildArgs,
        imageLabels
      )
    }

    private Map buildImageLabels() {
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
        def sanitizedVal = val.replaceAll("[\r\n]+", " ").trim().replaceAll("[\"']+", "")
        sanitizedImageLabels[key] = sanitizedVal
      }
      sanitizedImageLabels
  }

    private writeReleaseFile(Map imageLabels) {
      def jsonImageLabels = []
      for (def key : imageLabels.keySet()) {
        jsonImageLabels << """{"name": "${key}", "value": "${imageLabels[key]}"}"""
      }

      // Write docker/release.json file to be reachable from Dockerfile.
      script.writeFile(file: 'docker/release.json', text: "[\n" + jsonImageLabels.join(",\n") + "\n]")
    }
}
