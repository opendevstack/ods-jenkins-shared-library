import java.nio.file.Paths

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
                        [ $class: 'RelativeTargetDirectory', relativeTargetDir: Paths.get(".tmp", "repositories", repo.name).toString() ]
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
