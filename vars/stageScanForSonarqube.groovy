def call(def context, def requireQualityGatePass = false, def longLivedBranches = []) {
  def prAnalysisEnabled = context.analysePullRequestsWithSonarQube
  // If the Jenkinsfile author did not specify explicitly, we'll default to
  // configuration in /etc/sonarqube/config.json.
  if (prAnalysisEnabled == null) {
    prAnalysisEnabled = isPullRequestAnalysisAvailable(context.odsConfig)
  }

  def scanAllBranches = prAnalysisEnabled || context.sonarQubeBranch == '*'

  if (scanAllBranches || context.sonarQubeBranch == context.gitBranch) {
    stage('SonarQube Analysis') {
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
          projectVersionParam = "-Dsonar.scm.provider=git -Dsonar.projectVersion=${context.gitCommit.take(8)}"
        }

        if (prAnalysisEnabled) {
          // When long-lived branches are not given explicitly, we take the keys
          // from the branch mapping, assuming them to be the "stable branches".
          if (longLivedBranches.isEmpty()) {
            longLivedBranches = context.branchToEnvironmentMapping.keySet()
            longLivedBranches.removeAll { it.toLowerCase().endsWith('/') }
            longLivedBranches.removeAll { it == '*' }
          }
          if (!context.bitbucketToken) {
            echo 'WARN: No personal access token for Bitbucket configured. PR analysis cannot be performed.'
          } else if (longLivedBranches.contains(context.gitBranch)) {
            echo "INFO: ${context.gitBranch} is considered a long-lived branch. PR analysis will not be performed."
          } else {
            def prKey = null
            def prBase = null
            def prValues = []
            timeout(2) { // expect answer within 2 minutes
              def repoName = "${context.projectId}-${context.componentId}"
              def res = sh(
                label: 'Get pullrequests via API',
                script: "curl -H 'Authorization: Bearer ${context.bitbucketToken}' ${context.bitbucketUrl}/rest/api/1.0/projects/${context.projectId}/repos/${repoName}/pull-requests",
                returnStdout: true
              ).trim()
              if (context.debug) {
                echo "Pull requests: ${res}"
              }
              try {
                def js = readJSON(text: res)
                prValues = js['values']
                if (prValues == null) {
                  throw new RuntimeException('Field "values" of JSON response must not be empty!')
                }
              } catch (Exception ex) {
                echo "WARN: Could not understand API response. Error was: ${ex}"
              }
            }

            for (i = 0; i < prValues.size(); i++) {
              def prCandidate = prValues[i]
              try {
                def prFromBranch = prCandidate['fromRef']['displayId']
                if (prFromBranch == context.gitBranch) {
                  prKey = prCandidate['id']
                  prBase = prCandidate['toRef']['displayId']
                }
              } catch (Exception ex) {
                echo "WARN: Unexpected API response. Error was: ${ex}"
              }
              if (prKey && prBase) {
                break
              }
            }

            if (prKey && prBase) {
              echo "Scanning PR #${prKey}: ${context.gitBranch} -> ${prBase}."
              projectVersionParam += " -Dsonar.pullrequest.provider='Bitbucket Server'"
              projectVersionParam += " -Dsonar.pullrequest.bitbucketserver.serverUrl=${context.bitbucketUrl}"
              projectVersionParam += " -Dsonar.pullrequest.bitbucketserver.token.secured=${context.bitbucketToken}"
              projectVersionParam += " -Dsonar.pullrequest.key=${prKey}"
              projectVersionParam += " -Dsonar.pullrequest.branch=${context.gitBranch}"
              projectVersionParam += " -Dsonar.pullrequest.base=${prBase}"
            } else {
              def longLivedList = longLivedBranches.join(', ')
              echo "INFO: ${context.gitBranch} is not one of the long-lived branches (${longLivedList}), but no open PR was found for it."
            }
          }
        }

        // Scan
        def scannerBinary = "sonar-scanner"
        def status = sh(returnStatus: true, script: "which ${scannerBinary}", label : "finding scanner binary")
        if (status != 0) {
          def scannerHome = tool 'SonarScanner'
          scannerBinary = "${scannerHome}/bin/sonar-scanner"
        }
        sh (
          label : "SQ scanning",
          script: "${scannerBinary} ${debugMode} ${projectVersionParam}"
        )

        // Generate and archive cnes report.
        // We need to get the SQ project name as it might have been modified.
        def sqProps = readProperties file: 'sonar-project.properties'
        def sonarProjectKey = sqProps['sonar.projectKey']
        def targetSQreport = "SCRR-" + sonarProjectKey + ".docx"
        withEnv (["SQ_PROJECT=${sonarProjectKey}", "TARGET_SQ_REPORT=${targetSQreport}"]) {
          sh(
            label : "Generate CNES Report",
            script: "java -jar /usr/local/cnes/cnesreport.jar -s $SONAR_HOST_URL -t $SONAR_AUTH_TOKEN -p $SQ_PROJECT"
          )
          sh(
            label : "Create artifacts dir",
            script: "mkdir -p artifacts"
          )
          sh(
            label : "Move report to artifacts dir",
            script: "mv *-analysis-report.docx* artifacts/"
          )
          sh(
            label : "Rename report to SCRR",
            script: "mv artifacts/*-analysis-report.docx* artifacts/$TARGET_SQ_REPORT"
          )
          archiveArtifacts "artifacts/SCRR*"
          stash(
            name: "scrr-report-${context.componentId}-${context.buildNumber}",
            includes: 'artifacts/SCRR*',
            allowEmpty : true
          )
          context.addArtifactURI("SCRR", targetSQreport)
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

// PR analysis is present in commercial editions (Developer, Enterprise and Cluster).
boolean isPullRequestAnalysisAvailable(def odsConfig) {
  return odsConfig.sonarqubeEdition != 'community'
}

return this
