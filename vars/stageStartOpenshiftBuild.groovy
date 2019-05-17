def call(def context, def buildArgs = [:]) {
  stage('Build Openshift Image') {
    if (!context.environment) {
      println("Skipping for empty environment ...")
      return
    }

    timeout(context.openshiftBuildTimeout) {
      patchBuildConfig(context, buildArgs)
      sh "oc start-build ${context.componentId} --from-dir docker --follow -n ${context.targetProject}"
    }
  }
}

private void patchBuildConfig(def context, def buildArgs) {
  def gitCommitMessage = ${context.gitCommitMessage.replaceAll("[\r\n\"']+","").trim()}
  def patches = [
      '{"op": "replace", "path": "/spec/source", "value": {"type":"Binary"}}',
      """{"op": "replace", "path": "/spec/output/to/name", "value": "${context.componentId}:${context.tagversion}"}""",
      """{"op": "replace", "path": "/spec/output/imageLabels", "value": [
        {"name":"ods.build.source.repo.commit.author","value":"${context.gitCommitAuthor}"},
        {"name":"ods.build.source.repo.url","value":"${context.gitUrl}"},
        {"name":"ods.build.source.repo.commit","value":"${context.gitCommit}"},
        {"name":"ods.build.source.repo.commit.msg","value":"${gitCommitMessage}"},
        {"name":"ods.build.source.repo.commit.time","value":"${context.gitCommitTime}"},
        {"name":"ods.build.source.repo.branch","value":"${context.gitBranch}"},
        {"name":"ods.build.jenkins.job.url","value":"${context.buildUrl}"},
        {"name":"ods.build.date","value":"${context.buildTime}"},
        {"name":"ods.build.lib.version","value":"${context.odsSharedLibVersion}"}
      ]}"""
  ]

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
