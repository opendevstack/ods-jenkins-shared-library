def call(def context, def snykAuthenticationCode, def buildFile) {
  if (null == snykAuthenticationCode) {
    println("Skipping Snyk Scan due to missing authentication code (to enable pass as parameter a snyk service account authentication code to this stage)")
    return
  }

  String message = "Snyk scan mode: build will " + (context.doFailOnSnykScanVulnerabilities ? "" : "not ") +
  "fail if vulnerabilities are found (doFailOnSnykScanVulnerabilities=${context.doFailOnSnykScanVulnerabilities})!"
  println(message)

  if (buildFile == null) {
    error "Build failed: build file definition for snyk security scan is null!"
  } else {
    stage('Snyk Security Scan') {
      withEnv(["SNYK_AUTHENTICATION_CODE=${snykAuthenticationCode}", "PROJECT_NAME=${context.targetProject}",
               "COMPONENT_NAME=${context.componentId}", "BUILD_FILE=${buildFile}"]) {
        // Verify that snyk is installed
        def status = sh(script: "/usr/local/snyk version", returnStatus: true)
        if (status != 0) {
          error "Build failed: snyk is not installed!"
        }
        // Authorise snyk
        status = sh(script: "/usr/local/snyk auth $SNYK_AUTHENTICATION_CODE", returnStatus: true)
        if (status != 0) {
          error "Build failed: something went wrong by authorising snyk (SNYK_AUTHENTICATION_CODE=$SNYK_AUTHENTICATION_CODE)!"
        }
        // first monitor project (enable this only for master later, no need to monitor other branches)
        status = sh(script: "/usr/local/snyk monitor --file=$BUILD_FILE --project-name=$PROJECT_NAME/$COMPONENT_NAME", returnStatus: true)
        if (status != 0) {
          error "Build failed: something went wrong with snyk monitor command!"
        }
        // second fail if vulnerabilites are found (
        status = sh(script: "/usr/local/snyk test --file=$BUILD_FILE", returnStatus: true)
        if (status != 0 && context.doFailOnSnykScanVulnerabilities) {
          error "Build failed: snyk test found vulnerabilities (see snyk report above for details!)!"
        }
      }
    }
  }
}

return this
