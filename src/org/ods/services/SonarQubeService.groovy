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

    /*
    Example of the file located in .scannerwork/report-task.txt:
        projectKey=XXXX-python
        serverUrl=https://sonarqube-ods.XXXX.com
        serverVersion=8.2.0.32929
        branch=dummy
        dashboardUrl=https://sonarqube-ods.XXXX.com/dashboard?id=XXXX-python&branch=dummy
        ceTaskId=AXxaAoUSsjAMlIY9kNmn
        ceTaskUrl=https://sonarqube-ods.XXXX.com/api/ce/task?id=AXxaAoUSsjAMlIY9kNmn
    */
    Map<String, Object> readTask(String filename = '.scannerwork/report-task.txt') {
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
              	"-Dsonar.sources=.",
              	"-Dsonar.exclusions=",
            ]
            if (!properties.containsKey('sonar.projectVersion')) {
                scannerParams << "-Dsonar.projectVersion=${gitCommit.take(8)}"
            }
            if (logger.debugMode) {
                scannerParams << '-X'
            }
            if (pullRequestInfo && (sonarQubeEdition != 'community')) {
                [
                    "-Dsonar.pullrequest.key=${pullRequestInfo.bitbucketPullRequestKey}",
                    "-Dsonar.pullrequest.branch=${pullRequestInfo.branch}",
                    "-Dsonar.pullrequest.base=${pullRequestInfo.baseBranch}",
                ].each { scannerParams << it }
            } else if (sonarQubeEdition != 'community') {
                scannerParams << "-Dsonar.branch.name=${properties['sonar.branch.name']}"
            }
            script.sh(
                label: 'Set Java 17 for SonarQube scan',
                script: "source use-j17.sh"
            )
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

    def getQualityGateJSON(
        String projectKey,
        String sonarQubeEdition,
        String gitBranch,
        String bitbucketPullRequestKey) {
        withSonarServerConfig { hostUrl, authToken ->
            def getStatusUrl = "${hostUrl}/api/qualitygates/project_status"
            def urlEncodingFlags = "--data-urlencode projectKey=${projectKey}"
            if (bitbucketPullRequestKey && (sonarQubeEdition != 'community')) {
                urlEncodingFlags += " --data-urlencode pullRequest=${bitbucketPullRequestKey}"
            } else if (sonarQubeEdition != 'community') {
                urlEncodingFlags += " --data-urlencode branch=${gitBranch}"
            }
            script.sh(
                label: 'Get status of quality gate',
                script: "curl -s -u ${authToken}: --get --url ${getStatusUrl} ${urlEncodingFlags}",
                returnStdout: true
            )
        }
    }

    /*
        Example of the data returned in the api call api/ce/task:

            "task": {
                "organization": "my-org-1",
                "id": "AVAn5RKqYwETbXvgas-I",
                "type": "REPORT",
                "componentId": "AVAn5RJmYwETbXvgas-H",
                "componentKey": "project_1",
                "componentName": "Project One",
                "componentQualifier": "TRK",
                "analysisId": "123456",
                "status": "FAILED",
                "submittedAt": "2015-10-02T11:32:15+0200",
                "startedAt": "2015-10-02T11:32:16+0200",
                "executedAt": "2015-10-02T11:32:22+0200",
                "executionTimeMs": 5286,
                "errorMessage": "Fail to extract report AVaXuGAi_te3Ldc_YItm from database",
                "logs": false,
                "hasErrorStacktrace": true,
                "errorStacktrace": "java.lang.IllegalStateException: Fail to extract report from database",
                "scannerContext": "SonarQube plugins:\n\t- Git 1.0 (scmgit)\n\t- Java 3.13.1 (java)",
                "hasScannerContext": true
            }
    */
    def getComputeEngineTaskJSON(String taskid) {
        withSonarServerConfig { hostUrl, authToken ->
            script.sh(
                label: 'Get status of compute engine task',
                script: "curl -s -u ${authToken}: ${hostUrl}/api/ce/task?id=${taskid}",
                returnStdout: true
            )
        }
    }

    String getComputeEngineTaskResult(String taskid) {
        def computeEngineTaskJSON = getComputeEngineTaskJSON(taskid)
        def computeEngineTaskResult = script.readJSON(text: computeEngineTaskJSON)
        def status = computeEngineTaskResult?.task?.status ?: 'UNKNOWN'
        return status.toUpperCase()
    }

    String getSonarQubeHostUrl() {
        withSonarServerConfig { hostUrl, authToken ->
            return hostUrl
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
