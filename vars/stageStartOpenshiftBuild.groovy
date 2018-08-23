def call(def context) {
  stage('Build Openshift Image') {
    if (!context.environment) {
      println("Skipping for empty environment ...")
      return
    }

    timeout(context.openshiftBuildTimeout) {
      patchBuildConfig(context)
      sh "oc start-build ${context.componentId} --from-dir docker --follow -n ${context.targetProject}"
    }
  }
}

private void patchBuildConfig(def context) {
  sh """oc patch bc ${context.componentId} --type=json --patch '[
    {"op": "replace", "path": "/spec/source", "value":{"type":"Binary"}},
    {"op": "replace", "path": "/spec/output/to/name", "value":"${context.componentId}:${context.tagversion}"}
    ]' -n ${context.targetProject}"""
}

return this
