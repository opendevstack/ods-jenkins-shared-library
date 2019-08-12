package org.ods.service

class OpenShiftService {

    private def script
    def String openshiftApiHost
    def String bitbucketHost
    def String bitbucketUser
    def String bitbucketPassword
    
    OpenShiftService(def script, String openshiftApiHost, String bitbucketHost, String bitbucketUser, String bitbucketPassword) {
        this.script = script
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
      script.echo "searching for ${environment}"
      def statusCode = script.sh(
        script:"oc project ${environment} &> /dev/null",
        label :"check if OCP environment exists",
        returnStatus: true
      )
      script.echo "searching for ${environment} - result ${statusCode}"
      return (statusCode == 0) 
    }

    String exportProject(String environmentName, String projectName, String changeId) {
      def userPass = "${bitbucketUser}:${bitbucketPassword.replace('@', '%40').replace('$', '\'$\'')}"
      def cloneProjectScriptUrl = "https://${bitbucketHost}/projects/opendevstack/repos/ods-project-quickstarters/raw/ocp-templates/scripts/export_ocp_project_metadata.sh?at=refs%2Fheads%2Fproduction"
      def branchName = "${changeId}-${environmentName}"
      branchName = branchName.replace(' ', '_')
      script.echo "Calculated export branch name: ${branchName}"
      def debugMode = ""
      if (script.env.DEBUG) {
        debugMode = "--verbose=true"
      }
      script.sh(script: "curl --fail -s --user ${userPass} -G '${cloneProjectScriptUrl}' -d raw -o export_ocp_project_metadata.sh", label : "Dowloading export script")
      script.sh(script: "echo https://${userPass}@${this.bitbucketHost} > ~/.git-credentials", label : "resolving credentials")
      script.sh(script: "git config --global credential.helper store", label : "setup credential helper")
      script.sh(script: "sh export_ocp_project_metadata.sh -h ${this.openshiftApiHost} -g https://${this.bitbucketUser}@${this.bitbucketHost} -p ${projectName} -e ${environmentName} -gb ${branchName} ${debugMode}", label : "Started export script")
      
      def exportedArtifactUrl = "https://${bitbucketHost}/projects/${projectName}/repos/${projectName}-occonfig-artifacts/browse?at=refs%2Fheads%2F${branchName}"
      script.echo "export into oc-config-artifacts done - branch name: ${branchName} @ ${exportedArtifactUrl}"
      
      // https://bitbucket-dev.biscrum.com/projects/PLTFMDEV/repos/pltfmdev-occonfig-artifacts/browse?at=refs%2Fheads%2Fchange_15-dev
      return exportedArtifactUrl
    }
    
    String getOcArtifactAsJson (String type, String name, def jsonpath = null) 
    {
      if (type == null || name == null) {
        throw new RuntimeException ("Cannot call ocp get artifact with null type or name")
      }
      
      if (jsonpath == null) {
        return script.sh (script: "oc get ${type} ${name} -o json", returnStdout : true, label: "getting OC artifact ${name}")
      } else {
        return script.sh (script: "oc get ${type} ${name} -o jsonpath=\'${jsonpath}\'", returnStdout : true, label: "getting OC artifact ${name} with jsonpath: ${jsonpath}")
      }
    }
}
