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

        boolean failed = false;
        String errorMessage = null;

        // Verify that snyk is installed
        def status = sh(script: "snyk version | tee -a $SNYK_REPORT", returnStatus: true, label : "getting snyk version")
        if (status != 0) {
          failed = true;
          errorMessage = "snyk is not installed!"
        }

        // Authorise snyk
        if (!failed) {
          // TODO do we need to report auth call?!
          status = sh(script: "snyk auth $SNYK_AUTHENTICATION_CODE | tee -a $SNYK_REPORT", returnStatus: true, label : "authenticating to snyk")
          if (status != 0) {
            failed = true;
            errorMessage = "snyh auth failed!"
          }
        }

        // Monitor project
        if (!failed) {
          status = sh(script: "snyk monitor --org=$ORGANISATION --file=$BUILD_FILE --project-name=$COMPONENT_NAME --all-sub-projects | tee -a $SNYK_REPORT", returnStatus: true, label : "start monitoring in snyk")
          if (status != 0) {
            failed = true;
            errorMessage = "snyk monitor command failed!"
          }
        }

        // fail if vulnerabilites are found!
        if (!failed) {
          status = sh(script: "snyk test --org=$ORGANISATION --file=$BUILD_FILE --all-sub-projects | tee -a $SNYK_REPORT", returnStatus: true, label : "run snyk test")
          if (status != 0) {
            failed = true;
            errorMessage = "snyk test command failed!"
          }
        }

        def projectKey = context.componentId
        def targetSQreport = "SCSR-" + context.projectId + "-" + projectKey + "-" + snykReport
        withEnv (["SQ_PROJECT=${projectKey}", "TARGET_SQ_REPORT=${targetSQreport}", "SNYK_REPORT=${snykReport}"]) {
          sh(
                  label : "Create artifacts dir",
                  script: "mkdir -p artifacts/SCSR"
          )
          sh(
                  label : "Rename report to SCSR",
                  script: "mv $SNYK_REPORT artifacts/$TARGET_SQ_REPORT && ls -lart . && ls -lart artifacts/SCSR"
          )
          archiveArtifacts "artifacts/SCSR*"
          stash(
                  name: "scrr-report-${context.componentId}-${context.buildNumber}",
                  includes: 'artifacts/SCSR*',
                  allowEmpty : true
          )
          context.addArtifactURI("SCSR", targetSQreport)
        }

        if (failed && context.failOnSnykScanVulnerabilities) {
          error "snyk scan stage failed (see snyk report for details) due: " + errorMessage
        }
      }
    }
  }
}

return this

