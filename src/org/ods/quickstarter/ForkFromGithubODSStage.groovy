package org.ods.quickstarter

import org.ods.util.GitCredentialStore

class ForkFromGithubODSStage extends Stage {

    protected String STAGE_NAME = 'Fork from ODS Github'

    ForkFromGithubODSStage(def script, IContext context, Map config = [:]) {
        super(script, context, config)
        if (!config.branch) {
            config.branch = 'master'
        }
        if (!config.sourceRepository && config.odsComponent) {
            config.sourceRepository = "https://github.com/opendevstack/${config.odsComponent}.git"
        }
    }

    def run() {
        if (!config.sourceRepository) {
            script.error("Cannot fork without param 'sourceRepository' or 'odsComponent'")
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

        script.echo("Forking ${config.sourceRepository}, branch: ${config.branch}")
        script.dir(context.targetDir) {
            def status = script.sh(
                script: """
                  git init
                  git remote add origin ${context.gitUrlHttp}
                  git remote add source ${config.sourceRepository}
                  git fetch source
                  git checkout --no-track -b ${config.branch} source/${config.branch}
                """,
                label: "Fork from ${config.sourceRepository}",
                returnStatus: true
            )

            if (status != 0) {
                script.error("Could not fork ${config.sourceRepository}, status ${status}")
            }
        }
    }

}
