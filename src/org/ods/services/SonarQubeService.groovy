package org.ods.services

class SonarQubeService {

  private def script
  private String sonarQubeEnv

  SonarQubeService(def script, String sonarQubeEnv) {
    this.script = script
    this.sonarQubeEnv = sonarQubeEnv
  }

  def readProperties(String filename = 'sonar-project.properties') {
    script.readProperties(file: filename)
  }

  def scan(Map properties, String gitCommit, boolean debug = false) {
    withSonarServerConfig { hostUrl, authToken ->
      def scannerParams = [
        "-Dsonar.host.url=${hostUrl}",
        "-Dsonar.auth.token=${authToken}"
      ]
      if (!properties.containsKey('sonar.projectVersion')) {
        scannerParams << "-Dsonar.projectVersion=${gitCommit.take(8)}"
      }
      if (debug) {
        scannerParams << '-X'
      }
      script.sh(
        label: "Run SonarQube scan",
        script: "${getScannerBinary()} ${scannerParams.join(' ')}"
      )
    }
  }

  def generateCNESReport(String projectKey, String author) {
    withSonarServerConfig { hostUrl, authToken ->
      script.sh(
        label: "Generate CNES Report",
        script: "java -jar /usr/local/cnes/cnesreport.jar -s ${hostUrl} -t ${authToken} -p ${projectKey} -a ${author}"
      )
    }
  }

  private String getScannerBinary() {
    def scannerBinary = 'sonar-scanner'
    def status = script.sh(
      returnStatus: true,
      script: "which ${scannerBinary}",
      label: "Find scanner binary"
    )
    if (status != 0) {
      def scannerHome = script.tool('SonarScanner')
      scannerBinary = "${scannerHome}/bin/sonar-scanner"
    }
    scannerBinary
  }

  private def getQualityGateJSON(String projectKey) {
    withSonarServerConfig { hostUrl, authToken ->
      script.sh(
        label: "Get status of quality gate",
        script: "curl -s -u ${authToken}: ${hostUrl}/api/qualitygates/project_status?projectKey=${projectKey}",
        returnStdout: true
      )
    }
  }

  private def withSonarServerConfig(Closure block) {
    // SonarServerConfig is set in the Jenkins master via init.groovy.d/sonarqube.groovy.
    script.withSonarQubeEnv(sonarQubeEnv) {
      block(script.SONAR_HOST_URL, script.SONAR_AUTH_TOKEN)
    }
  }
}
