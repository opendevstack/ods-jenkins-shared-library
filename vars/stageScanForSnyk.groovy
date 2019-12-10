def call(def context, def snykAuthenticationCode, def buildFile, def organisation) {
  if (!snykAuthenticationCode) {
    error "missing snyk authentication code (parameter snykAuthenticationCode is null or false)"
  }
  if (!organisation) {
    error "missing organisation (parameter organisation is null or false)"
  }

  String message = "Snyk scan mode: build will " + (context.failOnSnykScanVulnerabilities ? "" : "not ") +
  "fail if vulnerabilities are found (failOnSnykScanVulnerabilities=${context.failOnSnykScanVulnerabilities})!"
  println(message)

  if (!buildFile) {
    error "build file definition for snyk security scan is null!"
  } else {
    stage('Snyk Security Scan') {
      String snykReport = "snyk-report.txt";
      withEnv(["SNYK_AUTHENTICATION_CODE=${snykAuthenticationCode}", "PROJECT_NAME=${context.targetProject}", "SNYK_REPORT=${snykReport}",
               "COMPONENT_NAME=${context.componentId}", "BUILD_FILE=${buildFile}", "ORGANISATION=${organisation}",
               "NEXUS_HOST=${context.nexusHost}", "NEXUS_USERNAME=${context.nexusUsername}", "NEXUS_PASSWORD=${context.nexusPassword}"]) {
        // Verify that snyk is installed
        def status = sh(script: "snyk version | tee $SNYK_REPORT", returnStatus: true, label : "getting snyk version")
        if (status != 0) {
          error "snyk is not installed!"
        }
        // Authorise snyk
        status = sh(script: "snyk auth $SNYK_AUTHENTICATION_CODE | tee $SNYK_REPORT", returnStatus: true, label : "authenticating to snyk")
        if (status != 0) {
          error "something went wrong by authorising snyk (SNYK_AUTHENTICATION_CODE=$SNYK_AUTHENTICATION_CODE)!"
        }
        // first monitor project
        status = sh(script: "snyk monitor --org=$ORGANISATION --file=$BUILD_FILE --project-name=$COMPONENT_NAME --all-sub-projects | tee $SNYK_REPORT", returnStatus: true, label : "start monitoring in snyk")
        if (status != 0) {
          error "something went wrong with snyk monitor command!"
        }
        // fail if vulnerabilites are found
        status = sh(script: "snyk test --org=$ORGANISATION --file=$BUILD_FILE --all-sub-projects | tee $SNYK_REPORT", returnStatus: true, label : "run snyk test")

        // archive report

        def projectKey = context.componentId
        def targetSQreport = "SCRR-" + projectKey + "-" + snykReport
        withEnv (["SQ_PROJECT=${projectKey}", "TARGET_SQ_REPORT=${targetSQreport}", "SNYK_REPORT=${snykReport}"]) {
          sh(
                  label : "Create artifacts dir",
                  script: "mkdir -p artifacts/SCRR"
          )
          sh(
                  label : "Rename report to SCRR",
                  script: "mv $SNYK_REPORT artifacts/SCRR/$TARGET_SQ_REPORT && ls -lart . && ls -lart artifacts/SCRR"
          )
          archiveArtifacts "artifacts/SCRR*"
          stash(
                  name: "scrr-report-${context.componentId}-${context.buildNumber}",
                  includes: 'artifacts/SCRR*',
                  allowEmpty : true
          )
          context.addArtifactURI("SCRR", targetSQreport)
        }


        if (status != 0 && context.failOnSnykScanVulnerabilities) {
          error "snyk test found vulnerabilities (see snyk report above for details!)!"
        }
      }
    }
  }
}

return this

