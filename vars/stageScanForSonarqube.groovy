def call(def context) {
  if (context.productionBranch == context.gitBranch) {
    stage('SonarQube Analysis') {
      withSonarQubeEnv('SonarServerConfig') {
        def verbose = ""
        if (context.verbose) {
          verbose = "-X"
        }
        def scannerBinary = "sonar-scanner"
        def status = sh(returnStatus: true, script: "which ${scannerBinary}")
        if (status != 0) {
          def scannerHome = tool 'SonarScanner'
          scannerBinary = "${scannerHome}/bin/sonar-scanner"
        }
        sh "${scannerBinary} ${verbose}"
      }
    }
  }
}

return this
