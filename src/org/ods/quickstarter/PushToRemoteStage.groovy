package org.ods.quickstarter

class PushToRemoteStage extends Stage {
  protected String STAGE_NAME = 'Push to remote'

  PushToRemoteStage(def script, IContext context, Map config = [:]) {
    super(script, context, config)
    if (!config.branch) {
      config.branch = 'master'
    }
  }

  def run() {
    script.withCredentials([script.usernamePassword(credentialsId: context.cdUserCredentialsId, passwordVariable: 'pass', usernameVariable: 'user')]) {
      script.writeFile file: "/home/jenkins/.netrc", text: "machine ${config.gitHost} login ${script.user} password ${script.pass}"
    }

    script.dir(context.targetDir) {
      if (!script.fileExists (".git")) {
        script.echo("Initializing quickstarter git repo ${context.targetDir} @${context.gitUrlHttp}")
        script.sh(
          script: """
          # Clone first (there is ALWAYS a remote repo!)
          git clone ${context.gitUrlHttp}
          basename=\$(basename ${context.gitUrlHttp})
          clonedGitFolderName=\${basename%.*}
          files=\$(ls \$clonedGitFolderName | grep -v ^l | wc -l)
          if [ \$files == 0 ]; then 
            echo "Init project from scratch \$(pwd)"
            git init .
            git remote add origin ${context.gitUrlHttp}
          else
            echo "Upgrading existing project: ${context.gitUrlHttp}"
            mv "\$clonedGitFolderName"/* .
            mv "\$clonedGitFolderName"/.[!.]* .
          fi
          rm -rf \$clonedGitFolderName
          git config user.email "undefined"
          git config user.name "ODS System User"
          git add --all .
          git commit -m "Initial OpenDevStack commit"
          """,
          label: "Copy and commit quickstarter files"
        )
      }
      script.echo("Pushing quickstarter git repo to ${context.gitUrlHttp}")
      script.sh(
        script: """
          git push -u origin ${config.branch}
          """,
        label: "Push to remote"
      )
    }
  }
}
