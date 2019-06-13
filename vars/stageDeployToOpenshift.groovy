def call(def context, def selector) {
  stage('Deploy to Openshift') {
    if (!context.environment) {
      println("Skipping for empty environment ...")
      return
    }
    if (fileExists('openshift')) {
      sh "oc --namespace ${context.targetProject} set triggers dc/${context.componentId} --manual"
      context.tailorUsingOpenshiftFolder()
      sh "oc --namespace ${context.targetProject} rollout latest dc/${context.componentId} && oc --namespace ${context.targetProject} rollout status dc/${context.componentId} --watch=true"
    } else {
      openshiftTag(
        srcStream: context.componentId,
        srcTag: context.tagversion,
        destStream: context.componentId,
        destTag: "latest",
        namespace: context.targetProject
      )
    }
  }
}

return this
