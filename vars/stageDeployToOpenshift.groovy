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

    def ocpLatestImage = sh(returnStdout: true, script:"oc get istag ${context.componentId}:latest -n ${context.targetProject} --no-headers", label : "find last image").trim().split(/\s+/)
    def ocpDockerLatestImageRef = ocpLatestImage[1]
      
    def ocpDeployment
    while(true) {
      ocpDeployment = sh(returnStdout: true, script:"oc describe dc ${context.componentId} -n ${context.targetProject} | grep -e ${ocpDockerLatestImageRef} -e Status -e 'Latest Version'", label : "find new deployment").trim().split(/\s+/)
      
      echo ("Found last deployment id: ${ocpDeployment[2]} - status ${ocpDeployment[6]}")
      if (ocpDeployment[6] != "Pending" && ocpDeployment[6] != "Running") {
          echo "OCP Deployment done - reporting status"
          break
      }
    }
    
    if (ocpDeployment[6] != "Complete") {
      error "Deployment ${context.componentId}${ocpDeployment[2]} failed (status: ${ocpDeployment[6]}), please check the error in the OCP console"
    }
    
    deploymentId = "${context.componentId}-${ocpDeployment[2]}"
    context.addArtifactURI("OCP Deployment Id", deploymentId)
  }
}

return this
