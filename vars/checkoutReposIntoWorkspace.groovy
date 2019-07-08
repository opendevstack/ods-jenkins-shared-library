// Checkout repositories into the current workspace
def call(List<Map> repos) {
    def steps = repos.collectEntries { repo ->
        [
            repo.name,
            {
                checkout([
                    $class: 'GitSCM',
                    branches: [
                        [ name: repo.branch ]
                    ],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [
                        [ $class: 'RelativeTargetDirectory', relativeTargetDir: ".tmp/repositories/${repo.name}" ]
                    ],
                    submoduleCfg: [],
                    userRemoteConfigs: [
                        [ credentialsId: 'bitbucket', url: repo.url ]
                    ]
                ])
            }
        ]
    }

    parallel steps
}

return this
