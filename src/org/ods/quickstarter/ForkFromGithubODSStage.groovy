package org.ods.quickstarter

class ForkFromGithubODSStage extends Stage {
  protected String STAGE_NAME = 'Fork from ODS Github'

  ForkFromGithubODSStage(def script, IContext context, Map config = [:]) {
    super(script, context, config)
    if (!config.branch) {
      config.branch = 'master'
    }
  }

  def run() {
    if (!config.odsComponent) {
      script.error ("Cannot fork from github with null component name! please provide valid param 'odsComponent'")
    }
    
    script.withCredentials([script.usernamePassword(credentialsId: context.cdUserCredentialsId, passwordVariable: 'pass', usernameVariable: 'user')]) {
      script.writeFile file: "/home/jenkins/.netrc", text: "machine ${config.gitHost} login ${script.user} password ${script.pass}"
    }

    def githubRepoUrl = "https://github.com/opendevstack/${config.odsComponent}.git"
    script.echo ("Forking ${githubRepoUrl}, branch: ${config.branch}")
    script.dir(context.targetDir) {
      def status = script.sh(
        script: """
          git init
          git remote add origin ${context.gitUrlHttp}
          git remote add github ${githubRepoUrl}
          git fetch github
          git checkout github/${config.branch}
          git checkout -b ${config.branch}
        """,
        label: "Fork '${config.odsComponent}' from github @${githubRepoUrl}",
        returnStatus: true
      )
      
      if (status != 0) {
        error ("Could not fork ${githubRepoUrl}, status ${status}")
      }
    }
  }  
}
