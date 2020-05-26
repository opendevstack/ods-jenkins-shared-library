package org.ods.quickstarter

import org.ods.util.GitCredentialStore

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
            script.error("Cannot fork from github with null component name! please provide valid param 'odsComponent'")
        }

        script.withCredentials(
            [script.usernamePassword(
                credentialsId: context.cdUserCredentialsId,
                passwordVariable: 'pass',
                usernameVariable: 'user'
            )]
        ) {
            GitCredentialStore.configureAndStore(script,
                context.bitbucketUrl as String,
                script.env.user as String,
                script.env.pass as String)
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
                  git checkout --no-track -b ${config.branch} github/${config.branch}
                """,
                label: "Fork '${config.odsComponent}' from github @${githubRepoUrl}",
                returnStatus: true
            )

            if (status != 0) {
                script.error ("Could not fork ${githubRepoUrl}, status ${status}")
            }
        }
    }

}
