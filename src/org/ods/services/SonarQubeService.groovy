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

    def scan(Map properties, String gitCommit, Map pullRequestInfo = [:], String sonarQubeEdition, String exclusions, Boolean sonarQubeProjectsPrivate, String privateToken) {
        withSonarServerConfig { hostUrl, authToken ->
            def scannerParams = [
                "-Dsonar.host.url=${hostUrl}",
                '-Dsonar.scm.provider=git',
                "-Dsonar.projectKey=${properties['sonar.projectKey']}",
                "-Dsonar.projectName=${properties['sonar.projectName']}",
                "-Dsonar.sources=.",
            ]
            if (exclusions?.trim()) {
                scannerParams << "-Dsonar.exclusions=${exclusions}"
            }
            if (sonarQubeProjectsPrivate) {
                scannerParams << "-Dsonar.auth.token=${privateToken}"
            } else {
                scannerParams << "-Dsonar.auth.token=${authToken}"  
            }
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

    /**
     * Checks if a SonarQube portfolio exists with the name of the projectKey value.
     * If it exists, adds the SonarQube project to the portfolio.
     */
    def addProjectToPortfolioIfExists(String projectKey, String sonarProjectKey) {
        withSonarServerConfig { hostUrl, authToken ->
            // Get all portfolios (views)
            // Authentication is required for this API endpoint.
            def getPortfoliosUrl = "${hostUrl}/api/views/portfolios"
            def portfoliosJson = script.sh(
                label: 'Get SonarQube portfolios',
                script: "curl -s -u ${authToken}: ${getPortfoliosUrl}",
                returnStdout: true
            )
            def portfolios = script.readJSON(text: portfoliosJson)
            def portfolio = portfolios?.views?.find { it.name == "${projectKey}" }
            if (portfolio) {
                // Add project to portfolio
                // Requires 'Administrator' permission on the portfolio and 'Browse' permission for adding project.
                def addProjectUrl = "${hostUrl}/api/views/add_project"
                def curlCmd = "curl -s -u ${authToken}: --data \"key=${projectKey}&project=${sonarProjectKey}\" ${addProjectUrl}"
                script.sh(
                    label: "Add project ${sonarProjectKey} to portfolio ${projectKey}",
                    script: curlCmd
                )
                logger.info("Project ${sonarProjectKey} added to portfolio ${projectKey}.")
                return true
            } else {
                logger.info("Portfolio ${projectKey} does not exist. No action taken.")
                return false
            }
        }
    }

    /**
     * Generates and stores a SonarQube token in OpenShift secret, or retrieves it if it already exists.
     * Returns the token string, or empty string if not available.
     */
    def generateAndStoreSonarQubeToken(String credentialsId, String ocNamespace) {
        withSonarServerConfig { hostUrl, authToken ->
            def ocSecretName = "sonarqube-token"
            def getTokenCmd = "oc get secret ${ocSecretName} -n ${ocNamespace} -o jsonpath='{.data.sonarqube-token}' 2>/dev/null"
            def encodedToken = ""
            try {
                encodedToken = script.sh(
                    script: getTokenCmd,
                    returnStdout: true,
                    label: "Fetch SonarQube token from OpenShift secret ${ocSecretName}"
                )?.trim()
            } catch (Exception e) {
                logger.info("OpenShift secret ${ocSecretName} not found in namespace ${ocNamespace}.")
                encodedToken = ""
            }
            if (encodedToken) {
                def decodeCmd = "echo ${encodedToken} | base64 --decode"
                def token = script.sh(
                    label: "Decode SonarQube token",
                    script: decodeCmd,
                    returnStdout: true
                )?.trim()
                logger.info("OpenShift secret ${ocSecretName} already exists in namespace ${ocNamespace}.")
                return token
            }
            // If not found, create and store the token
            script.withCredentials([script.usernamePassword(credentialsId: credentialsId, usernameVariable: 'username', passwordVariable: 'password')]) {
                // Generate SonarQube token via API
                def createTokenUrl = "${hostUrl}/api/user_tokens/generate"
                def tokenName = "jenkins-${ocNamespace}"
                def curlCmd = "curl -s -u ${username}:${password} --data \"name=${tokenName}\" ${createTokenUrl}"
                def response = script.sh(
                    label: "Generate SonarQube token for user ${username}",
                    script: curlCmd,
                    returnStdout: true
                )
                def json = script.readJSON(text: response)
                def token = json?.token
                if (!token) {
                    logger.info("Failed to generate SonarQube token for user ${username}.")
                    return ""
                }
                // Store token in OpenShift secret
                def ocCmd = "oc -n ${ocNamespace} create secret generic ${ocSecretName} --from-literal=sonarqube-token=${token}"
                script.sh(
                    label: "Store SonarQube token in OpenShift secret ${ocSecretName}",
                    script: ocCmd
                )
                logger.info("SonarQube token stored in OpenShift secret ${ocSecretName}.")
                return token
            }
        }
    }
}
