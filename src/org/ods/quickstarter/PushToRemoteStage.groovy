package org.ods.quickstarter

import org.ods.util.GitCredentialStore

class PushToRemoteStage extends Stage {

    protected String STAGE_NAME = 'Push to remote'

    PushToRemoteStage(def script, IContext context, Map config = [:]) {
        super(script, context, config)
    }

    def run() {
        script.withCredentials(
            [script.usernamePassword(
                credentialsId: context.cdUserCredentialsId,
                passwordVariable: 'pass',
                usernameVariable: 'user'
            )]
        ) {
            GitCredentialStore.configureAndStore(
                script,
                context.bitbucketUrl as String,
                script.env.user as String,
                script.env.pass as String
            )
        }

        script.dir(context.targetDir) {
            if (!script.fileExists ('.git')) {
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
                      mv -n "\$clonedGitFolderName"/* .
                      mv "\$clonedGitFolderName"/.[!.]* .
                    fi
                    rm -rf \$clonedGitFolderName
                    git config user.email "undefined"
                    git config user.name "ODS System User"
                    """,
                    label: 'Copy quickstarter files'
                )
                config?.gitSubModules?.each { submodule ->
                    script.sh(
                        script: """
                        echo "Adding ${submodule.name} git submodule"
                        git submodule add -b ${submodule.branch} ${submodule.url} ${submodule.folder}
                        """,
                        label: 'Add submodule to quickstarter files'
                    )
                }
                script.sh(
                    script: """
                    git add --all .
                    git commit -m "Initial OpenDevStack commit"
                    """,
                    label: 'Commit quickstarter files'
                )
            }
            script.echo("Pushing quickstarter git repo to ${context.gitUrlHttp}")
            script.sh(
                script: 'git push -u origin $(git rev-parse --abbrev-ref HEAD)',
                label: 'Push to remote'
            )
        }
    }

}
