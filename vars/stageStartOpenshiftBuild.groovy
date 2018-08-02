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
  sh """oc patch bc ${context.componentId} --patch '
   spec:
     output:
       to:
         kind: ImageStreamTag
         name: ${context.componentId}:${context.tagversion}
     runPolicy: Serial
     source:
       type: Binary
     strategy:
       type: Docker
       dockerstrategy: {}
    ' -n ${context.targetProject}"""
}

return this
