package org.ods.services

class OpenShiftService {

  private def script
  private String project

  OpenShiftService(def script, String project) {
    this.script = script
    this.project = project
  }

  String getServer() {
    script.sh(
      script: "oc whoami --show-server",
      label: "Get OpenShift server URL",
      returnStdout: true
    ).trim()
  }

  void startRollout(String name) {
    script.sh(
      script: "oc -n ${project} rollout latest dc/${name}",
      label: "Rollout latest version of dc/${name}"
    )
  }

  void watchRollout(String name) {
    script.sh(
      script: "oc -n ${project} rollout status dc/${name} --watch=true",
      label: "Watch rollout of latest version of dc/${name}"
    )
  }

  String getLatestVersion(String name) {
    script.sh(
      script: "oc -n ${project} get dc/${name} -o jsonpath='{.status.latestVersion}'",
      label: "Get latest version of dc/${name}",
      returnStdout: true
    ).trim()
  }

  String getRolloutStatus(String name) {
    script.sh(
      script: "oc -n ${project} get rc/${name} -o jsonpath='{.metadata.annotations.openshift\\.io/deployment\\.phase}'",
      label: "Get status of ReplicationController ${name}",
      returnStdout: true
    ).trim()toLowerCase()
  }

  void setImageTag(String name, String sourceTag, String destinationTag) {
    script.sh(
      script: "oc -n ${project} tag ${name}:${sourceTag} ${name}:${destinationTag}",
      label: "Set tag ${destinationTag} on is/${name}"
    )
  }

  boolean automaticImageChangeTriggerEnabled(String name) {
    try {
      def automaticValue = script.sh(
        script: """oc -n ${project} get dc/${name} -o template --template '{{range .spec.triggers}}{{if eq .type "ImageChange" }}{{.imageChangeParams.automatic}}{{end}}{{end}}'""",
        returnStdout: true,
        label: "Check ImageChange trigger for dc/${name}"
      ).trim()
      automaticValue == "true"
    } catch (Exception ex) {
      return false
    }
  }

  boolean resourceExists(String kind, String name) {
    script.sh(
      script: "oc -n ${project} get ${kind}/${name} &> /dev/null",
      returnStatus: true,
      label: "Check existance of ${kind} ${name}"
    ) == 0
  }

  String startAndFollowBuild(String name, String dir) {
    script.sh(
      script: "oc -n ${project} start-build ${name} --from-dir ${dir} --follow",
      label: "Start and follow OpenShift build ${name}",
      returnStdout: true
    ).trim()
  }

  String getLastBuildVersion(String name) {
    script.sh(
      returnStdout: true,
      script: "oc -n ${project} get bc ${name} -o jsonpath='{.status.lastVersion}'",
      label: "Get lastVersion of BuildConfig ${name}"
    ).trim()
  }

  String getBuildStatus(String buildId) {
    def buildStatus = 'unknown'
    def retries = 3
    for (def i = 0; i < retries; i++) {
      buildStatus = checkForBuildStatus(buildId)
      if (buildStatus == "complete") {
        return buildStatus
      }
      // Wait 5 seconds before asking again. Sometimes the build finishes but the
      // status is not set to "complete" immediately ...
      script.sleep(5)
    }
    return buildStatus
  }

  void patchBuildConfig(String name, String tag, Map buildArgs, Map imageLabels) {
    def odsImageLabels = []
    for (def key : imageLabels.keySet()) {
      odsImageLabels << """{"name": "${key}", "value": "${imageLabels[key]}"}"""
    }

    // Normally we want to replace the imageLabels, but in case they are not
    // present yet, we need to add them this time.
    def imageLabelsOp = "replace"
    def imageLabelsValue = script.sh(
      script: "oc -n ${project} get bc/${name} -o jsonpath='{.spec.output.imageLabels}'",
      returnStdout: true,
      label: "Test existance of path .spec.output.imageLabels"
    ).trim()
    if (imageLabelsValue.length() == 0) {
      imageLabelsOp = "add"
    }

    def patches = [
        '{"op": "replace", "path": "/spec/source", "value": {"type":"Binary"}}',
        """{"op": "replace", "path": "/spec/output/to/name", "value": "${name}:${tag}"}""",
        """{"op": "${imageLabelsOp}", "path": "/spec/output/imageLabels", "value": [
        ${odsImageLabels.join(",")}
      ]}"""
    ]

    // add build args - TODO: Ensure we are not replacing all buildArgs!!
    def buildArgsItems = []
    for (def key : buildArgs.keySet()) {
      def val = buildArgs[key]
      buildArgsItems << """{"name": "${key}", "value": "${val}"}"""
    }
    if (buildArgsItems.size() > 0) {
      def buildArgsPatch = """{"op": "replace", "path": "/spec/strategy/dockerStrategy", "value": {"buildArgs": [
      ${buildArgsItems.join(",")}
    ]}}"""
      patches << buildArgsPatch
    }

    script.sh(
      script: """oc -n ${project} patch bc ${name} --type=json --patch '[${patches.join(",")}]'""",
      label: "Patch BuildConfig ${name}"
    )
  }

  String getImageReference(String name, String tag) {
    script.sh(
      returnStdout: true,
      script: "oc get -n ${project} istag ${name}:${tag} -o jsonpath='{.image.dockerImageReference}'",
      label: "Get image reference of ${name}:${tag}"
    ).trim()
  }

  boolean tooManyEnvironments(String projectId, Integer limit) {
    script.sh(
      returnStdout: true, script: "oc projects | grep '^\\s*${projectId}-' | wc -l", label: "check ocp environment maximum"
    ).trim().toInteger() >= limit
  }

  private String checkForBuildStatus(String buildId) {
    script.sh(
      returnStdout: true,
      script: "oc -n ${project} get build ${buildId} -o jsonpath='{.status.phase}'",
      label: "Get phase of build ${buildId}"
    ).trim().toLowerCase()
  }
}
