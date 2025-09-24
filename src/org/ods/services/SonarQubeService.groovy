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

    /**
     * Runs a SonarQube scan.
     * @param options Map containing:
     *   - properties: Map
     *   - gitCommit: String
     *   - pullRequestInfo: Map (optional)
     *   - sonarQubeEdition: String
     *   - exclusions: String
     *   - privateToken: String
     */
    def scan(Map options) {
        Map properties = options.properties
        String gitCommit = options.gitCommit
        Map pullRequestInfo = options.pullRequestInfo ?: [:]
        String sonarQubeEdition = options.sonarQubeEdition
        String exclusions = options.exclusions
        String privateToken = options.privateToken

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
            if (privateToken) {
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

    def generateCNESReport(
        String projectKey,
        String author,
        String sonarBranch,
        String sonarQubeEdition,
        String privateToken
    ) {
        withSonarServerConfig { hostUrl, authToken ->
            def branchParam = sonarQubeEdition == 'community' ? '' : "-b ${sonarBranch}"
            def tokenToUse = privateToken ?: authToken
            script.sh(
                label: 'Generate CNES Report',
                script: """
                ${logger.shellScriptDebugFlag}
                java -jar /usr/local/cnes/cnesreport.jar \
                    -s ${hostUrl} \
                    -t ${tokenToUse} \
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
        String bitbucketPullRequestKey,
        String privateToken
    ) {
        withSonarServerConfig { hostUrl, authToken ->
            def getStatusUrl = "${hostUrl}/api/qualitygates/project_status"
            def urlEncodingFlags = "--data-urlencode projectKey=${projectKey}"
            if (bitbucketPullRequestKey && (sonarQubeEdition != 'community')) {
                urlEncodingFlags += " --data-urlencode pullRequest=${bitbucketPullRequestKey}"
            } else if (sonarQubeEdition != 'community') {
                urlEncodingFlags += " --data-urlencode branch=${gitBranch}"
            }
            def tokenToUse = privateToken ?: authToken
            script.sh(
                label: 'Get status of quality gate',
                script: "curl -s -u ${tokenToUse}: --get --url ${getStatusUrl} ${urlEncodingFlags}",
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
    def getComputeEngineTaskJSON(String taskid, String privateToken) {
        withSonarServerConfig { hostUrl, authToken ->
            def tokenToUse = privateToken ?: authToken
            script.sh(
                label: 'Get status of compute engine task',
                script: "curl -s -u ${tokenToUse}: ${hostUrl}/api/ce/task?id=${taskid}",
                returnStdout: true
            )
        }
    }

    String getComputeEngineTaskResult(String taskid, String privateToken) {
        def computeEngineTaskJSON = getComputeEngineTaskJSON(taskid, privateToken)
        def computeEngineTaskResult = script.readJSON(text: computeEngineTaskJSON)
        def status = computeEngineTaskResult?.task?.status ?: 'UNKNOWN'
        return status.toUpperCase()
    }

    String getSonarQubeHostUrl() {
        withSonarServerConfig { hostUrl, authToken ->
            return hostUrl
        }
    }

    /**
     * Generates and stores a SonarQube token in OpenShift secret, or retrieves it if it already exists.
     * Returns the token string, or empty string if not available.
     */
    def generateAndStoreSonarQubeToken(String credentialsId, String ocNamespace, String ocSecretName) {
        def hostUrl = getSonarQubeHostUrl()
        def secretOutput = script.sh(
            script: "oc -n ${ocNamespace} get secret ${ocSecretName} --ignore-not-found",
            returnStdout: true
        )?.trim()
        if (secretOutput) {
            logger.info("OpenShift secret ${ocSecretName} already exists in namespace ${ocNamespace}.")
            return ""
        }
        script.withCredentials([
            script.usernamePassword(
            credentialsId: credentialsId,
            usernameVariable: 'username',
            passwordVariable: 'password'
            )
        ]) {
            def createTokenUrl = "${hostUrl}/api/user_tokens/generate"
            def tokenName = "jenkins-${ocNamespace}-${new Date().format('yyyyMMddHHmmss')}"
            def tokenFile = "${tokenName}.json"
            def curlCmd = (
                "curl -s -o ${tokenFile} -w '%{http_code}' " +
                "-u ${script.env.username}:${script.env.password} " +
                "--data \"name=${tokenName}\" ${createTokenUrl}"
            )
            def httpCode = script.sh(
                label: "Generate SonarQube token for user ${script.env.username}",
                script: curlCmd,
                returnStdout: true
            )?.trim()
            def jsonResponse = ""
            try {
                jsonResponse = script.readFile("${tokenFile}")?.trim()
            } catch (Exception e) {
                logger.info("Failed to read response file: ${e.message}")
            }
            if (httpCode == "401") {
                logger.info(
                    "Authentication failed when generating SonarQube token. " +
                    "HTTP 401 - Check credentials for user ${script.env.username}"
                )
                return ""
            } else if (httpCode == "403") {
                logger.info(
                    "Access denied when generating SonarQube token. " +
                    "HTTP 403 - User ${script.env.username} lacks permission to generate tokens"
                )
                return ""
            } else if (httpCode != "200") {
                logger.info("SonarQube API call failed with HTTP ${httpCode}.")
                return ""
            }
            if (!jsonResponse) {
                logger.info("Empty response from SonarQube API despite HTTP 200 status")
                return ""
            }
            try {
                def json = script.readJSON(text: jsonResponse)
                def token = json?.token
                if (!token) {
                    logger.info("No token found in SonarQube API response")
                    return ""
                }
                // Overwrite the token file so it contains only the token string
                script.writeFile(file: tokenFile, text: token)
                // Prepare basic auth secret YAML
                def secretYaml = """
apiVersion: v1
kind: Secret
metadata:
  name: ${ocSecretName}
  namespace: ${ocNamespace}
  labels:
    credential.sync.jenkins.openshift.io: 'true'
type: kubernetes.io/basic-auth
stringData:
  username: ${script.env.username}
  password: ${token}
"""
                def yamlFile = "${tokenName}-secret.yaml"
                script.writeFile(file: yamlFile, text: secretYaml)
                def ocApplyCmd = "oc apply -f ${yamlFile}"
                script.sh(
                    label: "Store SonarQube token as basic-auth secret in OpenShift",
                    script: ocApplyCmd,
                    returnStdout: false
                )
                script.sh(script: "rm -f ${tokenFile} ${yamlFile}", label: "Clean up token and yaml files")
                logger.info(
                    "SonarQube token generated and stored in OpenShift basic-auth secret " +
                    "${ocSecretName} with jenkins sync label."
                )
            } catch (Exception e) {
                logger.info("Failed to parse SonarQube API response as JSON. Error: ${e.message}")
                return ""
            }
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
