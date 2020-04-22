package org.ods.services

class SnykService {

  private def script
  private String reportFile

  SnykService(def script, String reportFile) {
    this.script = script
    this.reportFile = reportFile
  }

  String getReportFile() {
    reportFile
  }

  boolean version() {
    script.sh(
      script: "snyk version | tee -a ${reportFile}",
      returnStatus: true,
      label: "Get Snyk version"
    ) == 0
  }

  boolean auth(String authCode) {
    script.sh(
      script: "snyk auth ${authCode} | tee -a ${reportFile}",
      returnStatus: true,
      label: "Authenticate with Snyk server"
    ) == 0
  }

  boolean test(String organisation, String buildFile, String severityThreshold) {
    script.sh(
      script: "snyk test --org=${organisation} --file=${buildFile} --all-sub-projects --severity-threshold=${severityThreshold} | tee -a ${reportFile}",
      returnStatus: true,
      label: "Run Snyk test"
    ) == 0
  }

  boolean monitor(String organisation, String buildFile) {
    script.sh(
        script: "snyk monitor --org=${organisation} --file=${buildFile} --all-sub-projects | tee -a ${reportFile}",
        returnStatus: true,
        label: "Start monitoring in snyk.io"
    ) == 0
  }
}
