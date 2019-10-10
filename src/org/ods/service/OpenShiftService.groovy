package org.ods.service

import groovy.json.JsonSlurperClassic

import org.ods.util.IPipelineSteps

class OpenShiftService {

    private IPipelineSteps steps

    def String openshiftApiHost
    def String bitbucketHost
    def String bitbucketUser
    def String bitbucketPassword
    
    OpenShiftService(IPipelineSteps steps, String openshiftApiHost, String bitbucketHost, String bitbucketUser, String bitbucketPassword) {
        this.steps = steps
        if (!openshiftApiHost) {
            throw new IllegalArgumentException("Error: unable to connect ocp api host - openshiftApiHost NOT defined")
        }
        this.openshiftApiHost = openshiftApiHost
        if (!bitbucketHost) {
            throw new IllegalArgumentException("Error: unable to connect bitbucket host - bitbucketHost NOT defined")
        }
        this.bitbucketHost = bitbucketHost
        this.bitbucketUser = bitbucketUser
        this.bitbucketPassword = bitbucketPassword
    }

    boolean envExists(String name) {
      def environment = name
      steps.echo "searching for ${environment}"
      def statusCode = steps.sh(
        script:"oc project ${environment} &> /dev/null",
        label :"check if OCP environment exists",
        returnStatus: true
      )
      steps.echo "searching for ${environment} - result ${statusCode}"
      return (statusCode == 0) 
    }

    String exportProject(String environmentName, String projectName, String changeId) {
      def userPass = "${bitbucketUser}:${bitbucketPassword.replace('@', '%40').replace('$', '\'$\'')}"
      def cloneProjectScriptUrl = "https://${bitbucketHost}/projects/opendevstack/repos/ods-project-quickstarters/raw/ocp-templates/scripts/export_ocp_project_metadata.sh?at=refs%2Fheads%2Fproduction"
      def branchName = "${changeId}-${environmentName}"
      branchName = branchName.replace(' ', '_')
      steps.echo "Calculated export branch name: ${branchName}"
      def debugMode = ""
      if (steps.env.DEBUG) {
        debugMode = "--verbose=true"
      }
      steps.sh(script: "curl --fail -s --user ${userPass} -G '${cloneProjectScriptUrl}' -d raw -o export_ocp_project_metadata.sh", label : "Dowloading export steps")
      steps.sh(script: "echo https://${userPass}@${this.bitbucketHost} > ~/.git-credentials", label : "resolving credentials")
      steps.sh(script: "git config --global credential.helper store", label : "setup credential helper")
      steps.sh(script: "sh export_ocp_project_metadata.sh -h ${this.openshiftApiHost} -g https://${this.bitbucketUser}@${this.bitbucketHost} -p ${projectName} -e ${environmentName} -gb ${branchName} ${debugMode}", label : "Started export steps")
      
      def exportedArtifactUrl = "https://${bitbucketHost}/projects/${projectName}/repos/${projectName}-occonfig-artifacts/browse?at=refs%2Fheads%2F${branchName}"
      steps.echo "export into oc-config-artifacts done - branch name: ${branchName} @ ${exportedArtifactUrl}"
      
      // https://bitbucket-dev.biscrum.com/projects/PLTFMDEV/repos/pltfmdev-occonfig-artifacts/browse?at=refs%2Fheads%2Fchange_15-dev
      return exportedArtifactUrl
    }
    
    String getOcArtifactAsJson (String type, String name, def jsonpath = null) 
    {
      if (type == null || name == null) {
        throw new RuntimeException ("Cannot call ocp get artifact with null type or name")
      }
      
      if (jsonpath == null) {
        return steps.sh (script: "oc get ${type} ${name} -o json", returnStdout : true, label: "getting OC artifact ${name}")
      } else {
        return steps.sh (script: "oc get ${type} ${name} -o jsonpath=\'${jsonpath}\'", returnStdout : true, label: "getting OC artifact ${name} with jsonpath: ${jsonpath}")
      }
    }

    Map getPodDataForComponent(String name) {
        String stdout = this.steps.sh(
          script: "oc get pod -l component=${name} -o json --show-all=false",
          returnStdout: true,
          label: "Getting OpenShift Pod data for ${name}"
        ).trim()

        return new JsonSlurperClassic().parseText(stdout)
    }
}
