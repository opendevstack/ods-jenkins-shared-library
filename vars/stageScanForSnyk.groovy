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

  if (buildFile == null) {
    error "build file definition for snyk security scan is null!"
  } else {
    stage('Snyk Security Scan') {
      withEnv(["SNYK_AUTHENTICATION_CODE=${snykAuthenticationCode}", "PROJECT_NAME=${context.targetProject}",
               "COMPONENT_NAME=${context.componentId}", "BUILD_FILE=${buildFile}", "ORGANISATION=${organisation}"]) {
        // Verify that snyk is installed
        def status = sh(script: "snyk version", returnStatus: true)
        if (status != 0) {
          error "snyk is not installed!"
        }
        // Authorise snyk
        status = sh(script: "snyk auth $SNYK_AUTHENTICATION_CODE", returnStatus: true)
        if (status != 0) {
          error "something went wrong by authorising snyk (SNYK_AUTHENTICATION_CODE=$SNYK_AUTHENTICATION_CODE)!"
        }
        // first monitor project
        status = sh(script: "snyk monitor --org=$ORGANISATION --file=$BUILD_FILE --project-name=$COMPONENT_NAME", returnStatus: true)
        if (status != 0) {
          error "something went wrong with snyk monitor command!"
        }
        // fail if vulnerabilites are found
        status = sh(script: "snyk test --org=$ORGANISATION --file=$BUILD_FILE", returnStatus: true)
        if (status != 0 && context.failOnSnykScanVulnerabilities) {
          error "snyk test found vulnerabilities (see snyk report above for details!)!"
        }
      }
    }
  }
}

return this
