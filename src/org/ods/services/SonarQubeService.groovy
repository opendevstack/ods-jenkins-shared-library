package org.ods.services

import org.ods.util.ILogger

class SonarQubeService {

    private final def script
    private final String sonarQubeEnv
    private final ILogger logger

    SonarQubeService(def script, ILogger logger, String sonarQubeEnv) {
        this.script = script
        this.logger = logger
        this.sonarQubeEnv = sonarQubeEnv
    }

    Map<String, Object> readProperties(String filename = 'sonar-project.properties') {
        script.readProperties(file: filename)
    }

    def scan(Map properties, String gitCommit, Map pullRequestInfo = [:], String sonarQubeEdition) {
        withSonarServerConfig { hostUrl, authToken ->
            def scannerParams = [
                "-Dsonar.host.url=${hostUrl}",
                "-Dsonar.auth.token=${authToken}",
                '-Dsonar.scm.provider=git',
                "-Dsonar.projectKey=${properties['sonar.projectKey']}",
                "-Dsonar.projectName=${properties['sonar.projectName']}",
            ]
            if (!properties.containsKey('sonar.projectVersion')) {
                scannerParams << "-Dsonar.projectVersion=${gitCommit.take(8)}"
            }
            if (logger.debugMode) {
                scannerParams << '-X'
            }
            if (pullRequestInfo) {
                [
                    "-Dsonar.pullrequest.provider='Bitbucket Server'",
                    "-Dsonar.pullrequest.bitbucketserver.serverUrl=${pullRequestInfo.bitbucketUrl}",
                    "-Dsonar.pullrequest.bitbucketserver.token.secured=${pullRequestInfo.bitbucketToken}",
                    "-Dsonar.pullrequest.bitbucketserver.project=${pullRequestInfo.bitbucketProject}",
                    "-Dsonar.pullrequest.bitbucketserver.repository=${pullRequestInfo.bitbucketRepository}",
                    "-Dsonar.pullrequest.key=${pullRequestInfo.bitbucketPullRequestKey}",
                    "-Dsonar.pullrequest.branch=${pullRequestInfo.branch}",
                    "-Dsonar.pullrequest.base=${pullRequestInfo.baseBranch}",
                ].each { scannerParams << it }
            } else if (sonarQubeEdition != 'community') {
                scannerParams << "-Dsonar.branch.name=${properties['sonar.branch.name']}"
            }
            script.sh(
                label: 'Run SonarQube scan',
                script: "${getScannerBinary()} ${scannerParams.join(' ')}"
            )
        }
    }

    def generateCNESReport(String projectKey, String author, String sonarBranch, String sonarQubeEdition) {
        withSonarServerConfig { hostUrl, authToken ->
            def branchParam = sonarQubeEdition == 'community' ? '' : "-b ${sonarBranch}"
            script.sh(
                label: 'Generate CNES Report',
                script: """
                ${logger.shellScriptDebugFlag}
                java -jar /usr/local/cnes/cnesreport.jar \
                    -s ${hostUrl} \
                    -t ${authToken} \
                    -p ${projectKey} \
                    -a ${author} \
                    ${branchParam}
                """
            )
        }
    }

    def getQualityGateJSON(String projectKey) {
        withSonarServerConfig { hostUrl, authToken ->
            script.sh(
                label: 'Get status of quality gate',
                script: "curl -s -u ${authToken}: ${hostUrl}/api/qualitygates/project_status?projectKey=${projectKey}",
                returnStdout: true
            )
        }
    }

    private String getScannerBinary() {
        def scannerBinary = 'sonar-scanner'
        def status = script.sh(
            returnStatus: true,
            script: "which ${scannerBinary}",
            label: 'Find sq scanner binary'
        )
        if (status != 0) {
            def scannerHome = script.tool('SonarScanner')
            scannerBinary = "${scannerHome}/bin/sonar-scanner"
        }
        scannerBinary
    }

    private withSonarServerConfig(Closure block) {
        // SonarServerConfig is set in the Jenkins master via init.groovy.d/sonarqube.groovy.
        script.withSonarQubeEnv(sonarQubeEnv) {
            block(script.SONAR_HOST_URL, script.SONAR_AUTH_TOKEN)
        }
    }

}
