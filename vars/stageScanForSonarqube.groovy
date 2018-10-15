def call(def context) {
  if (context.sonarQubeBranch == '*' || context.sonarQubeBranch == context.gitBranch) {
    stage('SonarQube Analysis') {
      withSonarQubeEnv('SonarServerConfig') {
        def debugMode = ""
        if (context.debug) {
          debugMode = "-X"
        }
        def scannerBinary = "sonar-scanner"
        def status = sh(returnStatus: true, script: "which ${scannerBinary}")
        if (status != 0) {
          def scannerHome = tool 'SonarScanner'
          scannerBinary = "${scannerHome}/bin/sonar-scanner"
        }
        sh "${scannerBinary} ${debugMode}"
      }
    }
  }
}

return this
