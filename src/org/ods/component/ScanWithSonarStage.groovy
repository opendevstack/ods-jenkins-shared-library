package org.ods.component

import org.ods.services.SonarQubeService

class ScanWithSonarStage extends Stage {
  protected String STAGE_NAME = 'SonarQube Analysis'
  protected SonarQubeService sonarQube

  ScanWithSonarStage(def script, IContext context, Map config, SonarQubeService sonarQube) {
    super(script, context, config)
    if (!config.requireQualityGatePass) {
      config.requireQualityGatePass = false
    }
    this.sonarQube = sonarQube
  }

  def run() {
    if (context.sonarQubeBranch != '*' && context.sonarQubeBranch != context.gitBranch) {
      script.echo "Skipping as branch '${context.gitBranch}' is not covered by the 'sonarQubeBranch' property."
      return
    }

    def sonarProperties = sonarQube.readProperties()
    def sonarProjectKey = sonarProperties['sonar.projectKey']

    sonarQube.scan(sonarProperties, context.gitCommit, context.debug)

    generateAndArchiveReports(sonarProjectKey, context.buildTag)

    if (config.requireQualityGatePass) {
      def qualityGateResult = getQualityGateResult(sonarProjectKey)
      if (qualityGateResult == "ERROR") {
        script.error "Quality gate failed!"
      } else if (qualityGateResult == "UNKNOWN") {
        script.error "Quality gate unknown!"
      } else {
        script.echo "Quality gate passed."
      }
    }
  }

  private generateAndArchiveReports(String projectKey, String buildTag) {
    def targetReport = "SCRR-${projectKey}.docx"
    def targetReportMd = "SCRR-${projectKey}.md"
    sonarQube.generateCNESReport(projectKey, buildTag)
    script.sh(
      label: 'Create artifacts dir',
      script: 'mkdir -p artifacts'
    )
    script.sh(
      label: 'Move report to artifacts dir',
      script: 'mv *-analysis-report.docx* artifacts/; mv *-analysis-report.md* artifacts/'
    )
    script.sh(
      label: 'Rename report to SCRR',
      script: "mv artifacts/*-analysis-report.docx* artifacts/${targetReport}; mv artifacts/*-analysis-report.md* artifacts/${targetReportMd}"
    )
    script.archiveArtifacts(artifacts: 'artifacts/SCRR*')
    script.stash(
      name: "scrr-report-${context.componentId}-${context.buildNumber}",
      includes: 'artifacts/SCRR*',
      allowEmpty: true
    )
    context.addArtifactURI('SCRR', targetReport)
    context.addArtifactURI('SCRR-MD', targetReportMd)
  }

  private String getQualityGateResult(String sonarProjectKey) {
    def qualityGateJSON = sonarQube.getQualityGateJSON(sonarProjectKey)
    try {
      def qualityGateResult = script.readJSON(text: qualityGateJSON)
      def status = qualityGateResult?.projectStatus?.projectStatus ?: 'UNKNOWN'
      return status.toUpperCase()
    } catch (Exception ex) {
      script.error "Quality gate status could not be retrieved. Status was: '${qualityGateJSON}'. Error was: ${ex}"
    }
  }
}
