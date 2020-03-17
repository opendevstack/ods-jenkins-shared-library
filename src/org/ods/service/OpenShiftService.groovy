package org.ods.service

import org.ods.Context
import org.ods.model.OpenshiftDeployment

class OpenShiftService {

  private Context context
  private def script

  OpenShiftService(script, Context context) {
    this.script = script
    this.context = context
  }

  String buildImage(Map buildArgs, Map imageLabels) {
    def startBuildInfo = ''
    script.timeout(context.openshiftBuildTimeout) {
      patchBuildConfig(buildArgs, imageLabels)
      startBuildInfo = script.sh(
          script: "oc -n ${context.targetProject} start-build ${context.componentId} --from-dir ${context.dockerDir} --follow",
          label: "Start OpenShift build",
          returnStdout: true
      ).trim()
    }
    script.echo startBuildInfo
    def buildId = extractBuildId(startBuildInfo)
    if (buildId == null)
      script.error "Could not extract the build ID from the build start result '${startBuildInfo}'"

    def buildStatus = checkForBuildStatus(buildId)
    if (buildStatus != "complete") {
      script.error "OCP Build ${buildId} was not successful - status ${buildStatus}"
    }

    return buildId
  }

  String getCurrentImageSha() {
    def currentImage = script.sh(
        returnStdout: true,
        script: "oc get -n ${context.targetProject} istag ${context.componentId}:${context.tagversion} -o jsonpath='{.image.dockerImageReference}'",
        label: "find new image sha"
    ).trim()
    script.echo(currentImage)
    return currentImage
  }

  String extractBuildId(String startBuildInfo) {
    def startBuildInfoHeader = startBuildInfo.split('\n').first()
    def match = (startBuildInfoHeader =~ /${context.componentId}-[0-9]+/)
    return match.find() ? match[0] : null
  }

  String checkForBuildStatus(String buildId) {
    def buildStatus = 'unknown'
    def retries = 3
    for (def i = 0; i < retries; i++) {
      buildStatus = getBuildStatus(buildId)
      if (buildStatus == "complete") {
        return buildStatus
      }
      // Wait 5 seconds before asking again. Sometimes the build finishes but the
      // status is not set to "complete" immediately ...
      script.sleep(5)
    }
    return buildStatus
  }

  String getBuildStatus(String buildId) {
    return script.sh(
        returnStdout: true,
        script: "oc -n ${context.targetProject} get build ${buildId} -o jsonpath='{.status.phase}'",
        label: "find last build"
    ).trim().toLowerCase()
  }

  void patchBuildConfig(Map buildArgs, Map imageLabels) {
    // sanitize commit message
    def sanitizedGitCommitMessage = context.gitCommitMessage.replaceAll("[\r\n]+", " ").trim().replaceAll("[\"']+", "")
    //remove apostrophe in committer name
    def sanitizedGitCommitAuthor = context.gitCommitAuthor.trim().replaceAll("'", "")

    // create the default ODS driven labels
    def odsImageLabels = []
    odsImageLabels.push("{\"name\":\"ods.build.source.repo.url\",\"value\":\"${context.gitUrl}\"}")
    odsImageLabels.push("{\"name\":\"ods.build.source.repo.commit.sha\",\"value\":\"${context.gitCommit}\"}")
    odsImageLabels.push("{\"name\":\"ods.build.source.repo.commit.msg\",\"value\":\"${sanitizedGitCommitMessage}\"}")
    odsImageLabels.push("{\"name\":\"ods.build.source.repo.commit.author\",\"value\":\"${sanitizedGitCommitAuthor}\"}")
    odsImageLabels.push("{\"name\":\"ods.build.source.repo.commit.timestamp\",\"value\":\"${context.gitCommitTime}\"}")
    odsImageLabels.push("{\"name\":\"ods.build.source.repo.branch\",\"value\":\"${context.gitBranch}\"}")
    odsImageLabels.push("{\"name\":\"ods.build.jenkins.job.url\",\"value\":\"${context.buildUrl}\"}")
    odsImageLabels.push("{\"name\":\"ods.build.timestamp\",\"value\":\"${context.buildTime}\"}")
    odsImageLabels.push("{\"name\":\"ods.build.lib.version\",\"value\":\"${context.odsSharedLibVersion}\"}")

    // add additional ones - prefixed with ext.
    for (def key : imageLabels.keySet()) {
      def val = imageLabels[key]
      odsImageLabels.push("{\"name\": \"ext.${key}\", \"value\": \"${val}\"}")
    }

    // write the file - so people can pick it up in the Dockerfile
    script.writeFile file: 'docker/release.json', text: "[" + odsImageLabels.join(",") + "]"

    // Normally we want to replace the imageLabels, but in case they are not
    // present yet, we need to add them this time.
    def imageLabelsOp = "replace"
    def imageLabelsValue = script.sh(
        script: "oc -n ${context.targetProject} get bc/${context.componentId} -o jsonpath='{.spec.output.imageLabels}'",
        returnStdout: true,
        label: "Test existance of path .spec.output.imageLabels"
    ).trim()
    if (imageLabelsValue.length() == 0) {
      imageLabelsOp = "add"
    }

    def patches = [
        '{"op": "replace", "path": "/spec/source", "value": {"type":"Binary"}}',
        """{"op": "replace", "path": "/spec/output/to/name", "value": "${context.componentId}:${context.tagversion}"}""",
        """{"op": "${imageLabelsOp}", "path": "/spec/output/imageLabels", "value": [
        ${odsImageLabels.join(",")}
      ]}"""
    ]

    // add build args
    def buildArgsItems = []
    for (def key : buildArgs.keySet()) {
      def val = buildArgs[key]
      buildArgsItems.push("{\"name\": \"${key}\", \"value\": \"${val}\"}")
    }
    if (buildArgsItems.size() > 0) {
      def buildArgsPatch = """{"op": "replace", "path": "/spec/strategy/dockerStrategy", "value": {"buildArgs": [
      ${buildArgsItems.join(",")}
    ]}}"""
      patches.push(buildArgsPatch)
    }

    script.sh """oc patch bc ${context.componentId} --type=json --patch '[${patches.join(",")}]' -n ${context.targetProject}"""
  }

  void checkDeploymentConfigExistence() {
    if (!resourceExists("dc")) {
      script.error "No DeploymentConfig ${context.componentId} found in ${context.targetProject}."
    }
  }

  void imageStreamExists() {
    if (!resourceExists("is")) {
      script.error "No ImageStream ${context.componentId} found in ${context.targetProject}."
    }
  }

  boolean resourceExists(String kind) {
    def statusCode = script.sh(
        script: "oc -n ${context.targetProject} get ${kind}/${context.componentId} &> /dev/null",
        returnStatus: true,
        label: "Check existance of ${kind} ${context.componentId}"
    )
    def exists = statusCode == 0
    if (exists) {
      debug("Resource ${kind}/${context.componentId} exists.")
    } else {
      debug("Resource ${kind}/${context.componentId} does not exist.")
    }
    return exists
  }

  boolean automaticConfigChangeTriggerEnabled() {
    try {
      def automaticValue = script.sh(
          script: """oc -n ${context.targetProject} get dc/${context.componentId} -o template --template '{{range .spec.triggers}}{{if eq .type "ConfigChange" }}configTriggerIsEnabled{{end}}{{end}}'""",
          returnStdout: true,
          label: "Check ConfigChange trigger for dc/${context.componentId}"
      ).trim()
      def exists = automaticValue == "configTriggerIsEnabled"
      if (exists) {
        debug("ConfigChange trigger enabled for dc/${context.componentId}.")
      } else {
        debug("No ConfigChange trigger set for dc/${context.componentId}.")
      }
      return exists
    } catch (Exception ex) {
      debug("ConfigChange trigger of dc/${context.componentId} cannot be detected.")
      return false
    }
  }

  boolean automaticImageChangeTriggerEnabled() {
    try {
      def automaticValue = script.sh(
          script: """oc -n ${context.targetProject} get dc/${context.componentId} -o template --template '{{range .spec.triggers}}{{if eq .type "ImageChange" }}{{.imageChangeParams.automatic}}{{end}}{{end}}'""",
          returnStdout: true,
          label: "Check ImageChange trigger for dc/${context.componentId}"
      ).trim()
      def exists = automaticValue == "true"
      if (exists) {
        debug("ImageChange trigger enabled for dc/${context.componentId}.")
      } else {
        debug("No ImageChange trigger set for dc/${context.componentId}.")
      }
      return exists
    } catch (Exception ex) {
      debug("ImageChange trigger of dc/${context.componentId} cannot be detected.")
      return false
    }
  }

  void setLatestImageTag() {
    script.sh(
        script: "oc -n ${context.targetProject} tag ${context.componentId}:${context.tagversion} ${context.componentId}:latest",
        label: "Update latest tag of is/${context.componentId} to ${context.tagversion}"
    )
  }

  void rolloutDeployment(boolean needsStart) {
    if (needsStart) {
      startRollout()
    }
    def deployment = watchRollout()
    if (deployment == null) {
      script.error "Deployment failed, please check the error in the OCP console."
    } else if (deployment.status.toLowerCase() != "complete") {
      script.error "Deployment ${deployment.id} failed with status '${deployment.status}', please check the error in the OCP console."
    } else {
      script.echo "Deployment ${deployment.id} successfully rolled out."
      context.addArtifactURI("OCP Deployment Id", deployment.id)
    }
  }

  private void startRollout() {
    script.sh(
        script: "oc -n ${context.targetProject} rollout latest dc/${context.componentId}",
        label: "Rollout latest deployment of dc/${context.componentId}"
    )
  }

  private OpenshiftDeployment watchRollout() {
    def rolloutResult = ''
    try {
      script.timeout(context.openshiftRolloutTimeout) {
        rolloutResult = script.sh(
            script: "oc -n ${context.targetProject} rollout status dc/${context.componentId} --watch=true",
            label: "Watch rollout of latest deployment of dc/${context.componentId}",
            returnStdout: true
        ).trim()
        debug(rolloutResult)
      }

      /*
      Considering that the output looks like

      -----------
      Deployment config "docker-plain-test" waiting on image update
      Waiting for latest deployment config spec to be observed by the controller loop...
      Waiting for rollout to finish: 0 out of 1 new replicas have been updated...
      Waiting for rollout to finish: 0 out of 1 new replicas have been updated...
      Waiting for rollout to finish: 0 out of 1 new replicas have been updated...
      Waiting for rollout to finish: 0 of 1 updated replicas are available...
      Waiting for latest deployment config spec to be observed by the controller loop...
      replication controller "docker-plain-test-1" successfully rolled out
      -----------

      and the part that needs extraction is docker-plain-test-1
       */

      // rolloutResult is e.g.: replication controller "foo-123" successfully rolled out
      // Unfortunately there does not seem a more structured way to retrieve this information.
      // It is not posssible to work with Regex because all regex supporting classes are not
      // serializable
      def prefix = "replication controller \""
      def index = rolloutResult.indexOf(prefix)
      def line = rolloutResult[index + prefix.size()..-1]
      index = line.indexOf("\" successfully rolled out")
      def rolloutId = line[0..index - 1]
      if (!rolloutId.startsWith("${context.componentId}-") || rolloutId.contains("\"")) {
        script.error "Got '${rolloutResult}' as rollout status, which cannot be parsed properly ..."
      }

      def rolloutStatus = script.sh(
          script: "oc -n ${context.targetProject} get rc/${rolloutId} -o jsonpath='{.metadata.annotations.openshift\\.io/deployment\\.phase}'",
          label: "Get status of latest rollout of dc/${context.componentId}",
          returnStdout: true
      ).trim()
      return new OpenshiftDeployment(rolloutId, rolloutStatus)
    } catch (ex) {
      debug("Rollout exceeded ${context.openshiftRolloutTimeout} minutes, cancelling ...")
      cancelRollout()
      throw ex
    }
  }

  private def cancelRollout() {
    script.timeout(1) {
      script.sh(
          script: "oc -n ${context.targetProject} rollout cancel dc/${context.componentId}",
          label: "Cancel rollout of latest deployment of dc/${context.componentId}"
      )
    }
  }

  private def debug(String msg) {
    if (context.debug) {
      script.echo msg
    }
  }

  boolean tooManyEnvironments(String projectId, Integer limit) {
    script.sh(
        returnStdout: true, script: "oc projects | grep '^\\s*${projectId}-' | wc -l", label: "check ocp environment maximum"
    ).trim().toInteger() >= limit
  }

}