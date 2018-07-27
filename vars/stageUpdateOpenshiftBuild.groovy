def call(def context) {
  stage('Update Openshift Build') {
    if (!context.environment) {
      println("Skipping for empty environment ...")
      return
    }

    timeout(context.openshiftBuildTimeout) {
      sh "oc project ${context.targetProject}"
      patchBuildConfig(context)
      sh "oc start-build ${context.componentId} -e projectId=${context.projectId} -e componentId=${context.componentId} -e tagversion=${context.tagversion} -e nexusHost=${context.nexusHost} -e nexusUsername=${context.nexusUsername} -e nexusPassword=${context.nexusPassword} --wait=true -n ${context.targetProject}"
    }
  }
}

private void patchBuildConfig(def context) {
  // contextDir should be "docker" but some quickstarters use "src/docker"
  def contextDir = "docker"
  if (!fileExists("docker")) {
    contextDir = "src/docker"
  }
  sh """oc patch bc ${context.componentId} --patch '
   spec:
     output:
       to:
         kind: ImageStreamTag
         name: ${context.componentId}:${context.tagversion}
     runPolicy: Serial
     source:
       type: Git
       git:
         uri: ${context.gitUrl}
         ref: ${context.gitBranch}
       contextDir: ${contextDir}
       sourceSecret:
         name: cd-user-token
     strategy:
       type: Docker
       dockerstrategy:
         env:
           - name: projectId
             value: ${context.projectId}
           - name: componentId
             value: ${context.componentId}
           - name: tagversion
             value: ${context.tagversion}
    ' -n ${context.targetProject}"""
}

return this
