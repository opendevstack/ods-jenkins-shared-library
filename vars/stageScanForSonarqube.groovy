def call(def context, def requireQualityGatePass = false) {

  withStage('SonarQube Analysis', context) {

    if (context.sonarQubeBranch != '*' && context.sonarQubeBranch != context.gitBranch) {
      echo "Stage is skipped since this build is for a branch that was not specified in the sonarQubeBranch property."
    } else {

      withSonarQubeEnv('SonarServerConfig') {

        // Debug mode
        def debugMode = ""
        if (context.debug) {
          debugMode = "-X"
        }

        // Project version - this could be overwritten later by e.g. the MRO forced version
        def projectVersionParam = ""
        def propertiesContent = readFile('sonar-project.properties')
        if (!propertiesContent.contains('sonar.projectVersion=')) {
          projectVersionParam = "-Dsonar.projectVersion=${context.gitCommit.take(8)}"
        }

        // Scan
        def scannerBinary = "sonar-scanner"
        def status = sh(returnStatus: true, script: "which ${scannerBinary}", label: "finding scanner binary")
        if (status != 0) {
          def scannerHome = tool 'SonarScanner'
          scannerBinary = "${scannerHome}/bin/sonar-scanner"
        }
        sh(
            label: "SQ scanning",
            script: "${scannerBinary} ${debugMode} ${projectVersionParam}"
        )

        // Generate and archive cnes report.
        // We need to get the SQ project name as it might have been modified.
        def sqProps = readProperties file: 'sonar-project.properties'
        def sonarProjectKey = sqProps['sonar.projectKey']
        def targetSQreport = "SCRR-" + sonarProjectKey + ".docx"
        def targetSQreportMd = "SCRR-" + sonarProjectKey + ".md"
        withEnv (["SQ_PROJECT=${sonarProjectKey}", "TARGET_SQ_REPORT=${targetSQreport}", "TARGET_SQ_REPORT_MD=${targetSQreportMd}"]) {
          sh(
              label: "Generate CNES Report",
              script: "java -jar /usr/local/cnes/cnesreport.jar -s $SONAR_HOST_URL -t $SONAR_AUTH_TOKEN -p $SQ_PROJECT"
          )
          sh(
              label: "Create artifacts dir",
              script: "mkdir -p artifacts"
          )
          sh(
              label: "Move report to artifacts dir",
              script: "mv *-analysis-report.docx* artifacts/; mv *-analysis-report.md* artifacts/"
          )
          sh(
              label: "Rename report to SCRR",
              script: "mv artifacts/*-analysis-report.docx* artifacts/$TARGET_SQ_REPORT; mv artifacts/*-analysis-report.md* artifacts/$TARGET_SQ_REPORT_MD"
          )
          archiveArtifacts "artifacts/SCRR*"
          stash(
              name: "scrr-report-${context.componentId}-${context.buildNumber}",
              includes: 'artifacts/SCRR*',
              allowEmpty: true
          )
          context.addArtifactURI("SCRR", targetSQreport)
          context.addArtifactURI("SCRR-MD", targetSQreportMd)
        }

        // Check quality gate status
        if (requireQualityGatePass) {
          def qualityGateJson = sh(
              label: "Get status of quality gate",
              script: "curl -s -u $SONAR_AUTH_TOKEN: $SONAR_HOST_URL/api/qualitygates/project_status?projectKey=$sonarProjectKey",
              returnStdout: true
          )
          try {
            def qualityGateResult = readJSON text: qualityGateJson
            if (qualityGateResult["projectStatus"]["projectStatus"] == "ERROR") {
              error "Quality gate failed!"
            } else {
              echo "Quality gate passed."
            }
          } catch (Exception ex) {
            error "Quality gate status could not be retrieved. Status was: '${qualityGateJson}'. Error was: ${ex}"
          }
        }
      }
    }
  }
}

return this
