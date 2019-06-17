// Checkout repositories into the current workspace
def call(repos) {
  repos.each { repo ->
    checkout([
      $class: 'GitSCM',
      branches: [
        [ name: repo.branch ]
      ],
      doGenerateSubmoduleConfigurations: false,
      extensions: [
        [ $class: 'RelativeTargetDirectory', relativeTargetDir: new File(".tmp/${repo.name}").path ]
      ],
      submoduleCfg: [],
      userRemoteConfigs: [
        [ credentialsId: 'cd-user-with-password', url: repo.url ]
      ]
    ])
  }
}