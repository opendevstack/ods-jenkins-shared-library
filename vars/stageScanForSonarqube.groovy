def call(def context) {
  if (context.sonarQubeBranch == '*' || context.sonarQubeBranch == context.gitBranch) {
    stage('SonarQube Analysis') {
      withSonarQubeEnv('SonarServerConfig') {
        // debug mode
        def debugMode = ""
        if (context.debug) {
          debugMode = "-X"
        }
        // project version
        def projectVersionParam = ""
        def propertiesContent = readFile('sonar-project.properties')
        if (!propertiesContent.contains('sonar.projectVersion=')) {
          projectVersionParam = "-Dsonar.projectVersion=${context.gitCommit.take(8)}"
        }
        // scan
        def scannerBinary = "sonar-scanner"
        def status = sh(returnStatus: true, script: "which ${scannerBinary}", label : "finding scanner binary")
        if (status != 0) {
          def scannerHome = tool 'SonarScanner'
          scannerBinary = "${scannerHome}/bin/sonar-scanner"
        }
        sh (script: "${scannerBinary} ${debugMode} ${projectVersionParam}", label : "SQ scanning")
		
		    // generate and archive cnes report
        // we need to get the SQ project name as people could modify it
      	sqProps = readProperties file: 'sonar-project.properties'
    		sonarProjectKey = sqProps['sonar.projectKey']
    		withEnv (["SQ_PROJECT=${sonarProjectKey}"]) {
    		  sh (script: "java -jar /usr/local/cnes/cnesreport.jar -s $SONAR_HOST_URL -t $SONAR_AUTH_TOKEN -p $SQ_PROJECT", label : "generate SCR")
              sh (script: "mkdir -p artifacts", label : "create artifacts folder")
              sh (script: "mv *-analysis-report.docx* artifacts/SCRR-$SQ_PROJECT.docx", label : "move SCCR to artifacts dir")
      		  archiveArtifacts "artifacts/SCRR-$SQ_PROJECT.docx"
    		}	
      }
    }
  }
}

return this
