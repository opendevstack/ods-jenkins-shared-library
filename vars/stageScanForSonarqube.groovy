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
		// execute the scan
        sh "${scannerBinary} ${debugMode}"
		
		// we need to get the sq project name - people could modify it
		sq_props = readProperties file: 'sonar-project.properties'
		sonarProjectKey = sq_props['sonar.projectKey']
		withEnv (["SQ_PROJECT=${sonarProjectKey}"])
		{
		  sh "java -jar /usr/local/cnes/cnesreport.jar -s $SONAR_HOST_URL -t $SONAR_AUTH_TOKEN -p $SQ_PROJECT"
		  // archive generated cnes report doc
		  archiveArtifacts '*-analysis-report.docx*'
		}	
      }
    }
  }
}

return this
