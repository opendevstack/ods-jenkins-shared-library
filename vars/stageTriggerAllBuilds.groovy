def call(def context) {
  stage('Trigger All Openshift Builds') {
    if (!context.environment) {
      println("Skipping for empty environment ...")
      return
    }

    if (!context.environmentCreated) {
      println("Skipping as environment has not been created in this pipeline ...")
      return
    }

    triggerAllBuilds(context)
  }
}

private void triggerAllBuilds(context) {
  def buildConfigs = getBuildConfigs(context)
  buildConfigs.each { bc ->
    echo "Triggering build config '${bc}' ..."
    def tagversion = getTagversion(context, bc)
    echo "Using tagversion '${tagversion}' ..."
    setBcEnv(context, bc, tagversion)
    patchBc(context, bc, tagversion)
    startBuild(context, bc)
    tagBuild(context, bc, tagversion)
  }
}

private String[] getBuildConfigs(def context) {
  sh(
    returnStdout: true,
    script: "oc get bc --no-headers -n ${context.projectId}-test | awk '{print \$1}'"
  ).split()
}

private String getTagversion(def context, String bc) {
  sh(
    returnStdout: true,
    script: "oc export bc ${bc} -n ${context.projectId}-test | grep 'output' -A 3 | tail -n 1 | awk -F':' '{print \$3}'"
  ).trim()
}

private void setBcEnv(def context, String bc, String tagversion) {
  sh "oc set env bc ${bc} projectId=${context.projectId} componentId=${bc} tagversion=${tagversion}"
}

private void patchBc(def context, String bc, String tagversion) {
  def gitUrl = "https://cd_user@${context.bitbucketHost}/scm/${context.projectId}/${context.projectId}-${bc}.git"
  sh """oc patch bc ${bc} -n ${context.targetProject} --patch '
    spec:
      output:
        to:
          kind: ImageStreamTag
          name: ${bc}:${tagversion}
      runPolicy: Serial
      source:
        type: Git
        git:
          uri: ${gitUrl}
          ref: master
        contextDir: docker
        sourceSecret:
          name: cd-user-token
      strategy:
        type: Docker
        dockerstrategy:
          env:
            - name: projectId
              value: ${context.projectId}
            - name: componentId
              value: ${bc}
            - name: tagversion
              value: ${tagversion}
    ' || exit 1"""
}

private void startBuild(def context, String bc) {
  sh "oc start-build ${bc} -n ${context.targetProject} --wait || exit 1"
}

private void tagBuild(def context, String bc, String tagversion) {
  sh "oc tag -n ${context.targetProject} ${bc}:${tagversion} ${bc}:latest"
}

return this
