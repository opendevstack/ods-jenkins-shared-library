def call(def context, def buildArgs = [:], def imageLabels = [:]) {

  withStage('Build Openshift Image', context) {

    if (!context.environment) {
      echo "Skipping for empty environment ..."
      return
    }

    def buildId = null
    def startBuildInfo = ''
    timeout(context.openshiftBuildTimeout) {
      patchBuildConfig(context, buildArgs, imageLabels)
      startBuildInfo = sh (
        script: "oc -n ${context.targetProject} start-build ${context.componentId} --from-dir docker --follow",
        label: "Start OpenShift build",
        returnStdout: true
      ).trim()
      echo startBuildInfo
      def startBuildInfoHeader = startBuildInfo.split('\n').first()
      def match = (startBuildInfoHeader =~ /${context.componentId}-[0-9]+/)
      if (match.find()) {
        buildId = match[0]
      } else {
        error "Did not find build ID in build start result '${startBuildInfoHeader}'"
      }    
    }

    if (buildId == null) {
      error "Got '${startBuildInfo}' as build start result, which cannot be parsed to get the build ID ..."
    }

    def buildStatus = ""
    def int retries = 10
    while (buildStatus.toLowerCase() != "complete" && retries > 0)
    {
      sleep 5
      buildStatus = sh(
        returnStdout: true,
        script:"oc -n ${context.targetProject} get build ${buildId} -o jsonpath='{.status.phase}'",
        label: "find last build"
      ).trim()
      retries--
    }

    if (buildStatus.toLowerCase() != "complete") {
      error "OCP Build ${buildId} was not successful - status ${buildStatus}"
    }

    def currentImage = sh(
      returnStdout: true,
      script:"oc get -n ${context.targetProject} istag ${context.componentId}:${context.tagversion} -o jsonpath='{.image.dockerImageReference}'",
      label: "find new image sha"
    ).trim()

    context.addArtifactURI("OCP Build Id", buildId)
    context.addArtifactURI("OCP Docker image", currentImage)
  }
}

private void patchBuildConfig(def context, def buildArgs, def imageLabels) {
 // sanitize commit message
  def sanitizedGitCommitMessage = context.gitCommitMessage.replaceAll("[\r\n]+", " ").trim().replaceAll("[\"']+", "")
  //remove apostrophe in committer name
  def sanitizedGitCommitAuthor = context.gitCommitAuthor.trim().replaceAll("'","")

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
  writeFile file: 'docker/release.json', text: "[" + odsImageLabels.join(",") + "]"

  // Normally we want to replace the imageLabels, but in case they are not
  // present yet, we need to add them this time.
  def imageLabelsOp = "replace"
  def imageLabelsValue = sh(
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

  sh """oc patch bc ${context.componentId} --type=json --patch '[${patches.join(",")}]' -n ${context.targetProject}"""
}

return this
