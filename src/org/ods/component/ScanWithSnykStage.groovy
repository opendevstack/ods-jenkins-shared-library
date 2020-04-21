package org.ods.component

import org.ods.services.SnykService

class ScanWithSnykStage extends Stage {
  public final String STAGE_NAME = 'Snyk Security Scan'
  private SnykService snyk

  ScanWithSnykStage(def script, IContext context, Map config, SnykService snyk) {
    super(script, context, config)
    if (!config.containsKey('failOnVulnerabilities')) {
      config.failOnVulnerabilities = context.failOnSnykScanVulnerabilities
    }
    if (!config.organisation) {
      config.organisation = context.projectId
    }
    if (!config.projectName) {
      config.projectName = context.componentId
    }
    if (!config.buildFile) {
      config.buildFile = 'build.gradle'
    }
    this.snyk = snyk
  }

  def run() {
    if (!config.snykAuthenticationCode) {
      script.error "Option 'snykAuthenticationCode' is not set!"
    }

    if (!snyk.version()) {
      script.error 'Snyk binary is not in $PATH'
    }

    if (!snyk.auth(config.snykAuthenticationCode)) {
      script.error 'Snyk auth failed'
    }

    script.echo "Scanning for vulnerabilities with " +
        "organisation=${config.organisation}, " +
        "projectName=${config.projectName}, " +
        "buildFile=${config.buildFile}, " +
        "failOnVulnerabilities=${config.failOnVulnerabilities}."

    boolean vulnerabilitiesFound

    def envVariables = [
      "NEXUS_HOST=${context.nexusHost}",
      "NEXUS_USERNAME=${context.nexusUsername}",
      "NEXUS_PASSWORD=${context.nexusPassword}"
    ]
    // nexus credentials are provided here because snyk runs build.gradle who needs them
    script.withEnv(envVariables) {
      vulnerabilitiesFound = snyk.test(config.organisation, config.buildFile)
      if (vulnerabilitiesFound) {
        script.echo 'Snyk test detected vulnerabilities.'
      } else {
        script.echo 'No vulnerabilities detected.'
      }
    }

    if (!snyk.monitor(config.organisation, config.buildFile, config.projectName)) {
      script.error 'Snyk monitor failed'
    }

    generateAndArchiveReport()

    if (vulnerabilitiesFound && config.failOnVulnerabilities) {
      script.error 'Snyk scan stage failed. See snyk report for details.'
    }
  }

  private generateAndArchiveReport() {
    def targetReport = "SCSR-${context.projectId}-${context.componentId}-${snyk.reportFile}"
    script.sh(
      label: 'Create artifacts dir',
      script: 'mkdir -p artifacts/SCSR'
    )
    script.sh(
      label: 'Rename report to SCSR',
      script: "mv ${snyk.reportFile} artifacts/${targetReport}"
    )
    script.archiveArtifacts(artifacts: 'artifacts/SCSR*')
    script.stash(
      name: "scrr-report-${context.componentId}-${context.buildNumber}",
      includes: 'artifacts/SCSR*',
      allowEmpty: true
    )
    context.addArtifactURI('SCSR', targetReport)
  }
}
