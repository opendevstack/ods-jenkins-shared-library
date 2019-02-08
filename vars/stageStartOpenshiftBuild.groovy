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
  def patches = [
    '{"op": "replace", "path": "/spec/source", "value": {"type":"Binary"}}',
    """{"op": "replace", "path": "/spec/output/to/name", "value": "${context.componentId}:${context.tagversion}"}"""
  ]

  def buildArgsItems = []
  for (def key : buildArgs.keySet()) {
    def val = buildArgs[key]
    buildArgsItems.push("{\"name\": \"${key}\", \"value\": \"${val}\"}")
  }
  if (buildArgsItems.size() > 0) {
    def buildArgsPatch = """{"op": "replace", "path": "/spec/strategy/dockerStrategy", "value": {"buildArgs": [${buildArgsItems.join(",")}]}}"""
    patches.push(buildArgsPatch)
  }

  sh """oc patch bc ${context.componentId} --type=json --patch '[${patches.join(",")}]' -n ${context.targetProject}"""
}

return this
