def call(def context) {
  stage('Deploy to Openshift') {
    if (!context.environment) {
      println("Skipping for empty environment ...")
      return
    }

    openshiftTag(
      srcStream: context.componentId,
      srcTag: context.tagversion,
      destStream: context.componentId,
      destTag: "latest",
      namespace: context.targetProject
    )
  }
}

return this
