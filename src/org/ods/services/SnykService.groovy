package org.ods.services

class SnykService {

    private final def script
    private final String reportFile

    SnykService(def script, String reportFile) {
        this.script = script
        this.reportFile = reportFile
    }

    String getReportFile() {
        reportFile
    }

    boolean version() {
        script.sh(
            script: """
              set -e
              set -o pipefail
              snyk version | tee -a ${reportFile}
            """,
            returnStatus: true,
            label: 'Get Snyk version'
        ) == 0
    }

    boolean auth(String authCode) {
        // write snyk auth command into file to avoid displaying the auth code in the output log
        script.writeFile file: 'snykauth.sh', text: "snyk auth ${authCode} | tee -a ${reportFile}"
        script.sh(
            script: '''
              set -e
              set -o pipefail
              chmod +x snykauth.sh
              ./snykauth.sh
            ''',
            returnStatus: true,
            label: 'Authenticate with Snyk server'
        ) == 0
    }

    boolean test(String organisation, String buildFile, String severityThreshold, List<String> additionalFlags) {
        def flags = "--org=${organisation} " +
            "--file=${buildFile} " +
            "--severity-threshold=${severityThreshold}"
        additionalFlags.each { flag ->
            flags += " " + flag
        }
        script.sh(
            script: """
              set -e
              set -o pipefail
              snyk test ${flags} | tee -a ${reportFile}
            """,
            returnStatus: true,
            label: 'Run Snyk test'
        ) == 0
    }

    boolean monitor(String organisation, String buildFile, List<String> additionalFlags) {
        def flags = "--org=${organisation} --file=${buildFile}"
        additionalFlags.each { flag ->
            flags += " " + flag
        }
        script.sh(
            script: """
              set -e
              set -o pipefail
              snyk monitor ${flags} | tee -a ${reportFile}
            """,
            returnStatus: true,
            label: 'Start monitoring in snyk.io'
        ) == 0
    }

}
