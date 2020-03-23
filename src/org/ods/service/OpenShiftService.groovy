package org.ods.service

import groovy.json.JsonSlurperClassic

import org.ods.util.IPipelineSteps

class OpenShiftService {

    private IPipelineSteps steps

    def String openshiftApiUrl
    def String bitbucketUrl
    def String bitbucketUser
    def String bitbucketPassword
    def String tailorPrivateKeyFile
    
    OpenShiftService(
      IPipelineSteps steps,
      String openshiftApiUrl,
      String bitbucketHost,
      String bitbucketUser,
      String bitbucketPassword,
      String tailorPrivateKeyFile) {
        this.steps = steps
        if (!openshiftApiUrl) {
            throw new IllegalArgumentException("Error: unable to connect ocp api host - openshiftApiUrl NOT defined")
        }
        this.openshiftApiUrl = openshiftApiUrl
        if (!bitbucketHost) {
            throw new IllegalArgumentException("Error: unable to connect bitbucket host - bitbucketHost NOT defined")
        }
        this.bitbucketUrl = "https://${bitbucketHost}"
        this.bitbucketUser = bitbucketUser
        this.bitbucketPassword = bitbucketPassword
        this.tailorPrivateKeyFile = tailorPrivateKeyFile
    }

    void loginToExternalCluster(String apiUrl, String apiToken) {
      steps.sh(
        script: """oc login ${apiUrl} --token=${apiToken} >& /dev/null""",
        label: "login to external cluster (${apiUrl})"
      )
    }

    void tailorApply(String project, String selector, String exclude, String paramFile, boolean verify) {
      def verifyFlag = ''
      if (verify) {
        verifyFlag = '--verify'
      }
      def excludeFlag = ''
      if (exclude) {
        excludeFlag = "--exclude ${exclude}"
      }
      doTailorApply(project, "-l ${selector} ${excludeFlag} ${buildParamFileFlag(paramFile)} --ignore-unknown-parameters ${tailorPrivateKeyFlag()} ${verifyFlag}")
    }

    private void doTailorApply(String project, String tailorParams) {
      steps.sh(
        script: """tailor \
          ${tailorVerboseFlag()} \
          --non-interactive \
          -n ${project} \
          apply ${tailorParams}""",
        label: "tailor apply for ${project} (${tailorParams})"
      )
    }

    boolean tailorHasDrift(String project, String selector, String paramFile) {
      def diffStatus = steps.sh(
        script: """tailor \
          -n ${project} \
          diff \
          -l ${selector} \
          ${buildParamFileFlag(paramFile)} \
          --ignore-unknown-parameters \
          ${tailorPrivateKeyFlag()} \
          ${tailorVerboseFlag()}""",
        label: "tailor diff in ${project}",
        returnStatus: true
      )
      return diffStatus != 0
    }

    void tailorExport(String project, String selector, Map<String, String> envParams, String targetFile) {
      doTailorExport(project, "-l ${selector}", envParams, targetFile)
    }

    private void doTailorExport(String project, String tailorParams, Map<String, String> envParams, String targetFile) {
      envParams['TAILOR_NAMESPACE'] = project
      def templateParams = ''
      def sedReplacements = ''
      envParams.each { key, val ->
        sedReplacements += "s|${val}|\\\${${key}}|g;"
        templateParams += "- name: ${key}\n  required: true\n"
      }
      steps.sh(
        script: """
          tailor \
            ${tailorVerboseFlag()} \
            -n ${project} \
            export ${tailorParams} > ${targetFile}
          sed -i -e "${sedReplacements}" ${targetFile}
          echo "parameters:" >> ${targetFile}
          echo "${templateParams}" >> ${targetFile}
        """,
        label: "tailor export of ${project} (${tailorParams}) into ${targetFile}"
      )
    }

    String getSessionApiUrl() {
      steps.sh(
        script: "oc whoami --show-server",
        label: "Get URL of API server",
        returnStdout: true
      ).trim()
    }

    void watchRollout(String project, String component, int openshiftRolloutTimeoutMinutes) {
      steps.timeout(time: openshiftRolloutTimeoutMinutes) {
        steps.sh(
          script: "oc -n ${project} rollout status dc/${component} --watch=true",
          label: "Watch rollout of latest deployment of dc/${component}"
        )
      }
    }

    String getLatestVersion(String project, String component) {
      steps.sh(
        script: "oc -n ${project} get dc/${component} -o jsonpath='{.status.latestVersion}'",
        label: "Get latest version of dc/${component}",
        returnStdout: true
      ).trim()
    }

    String getRunningImageSha(String project, String component, String version) {
      def runningImage = steps.sh(
        script: "oc -n ${project} get rc/${component}-${version} -o jsonpath='{.spec.template.spec.containers[0].image}'",
        label: "Get running image",
        returnStdout: true
      ).trim()
      runningImage.substring(runningImage.lastIndexOf("@sha256:") + 1)
    }

    void importImageFromSourceRegistry(String name, String sourceProject, String imageSha, String targetProject, String imageTag) {
      def sourceClusterRegistryHost = getSourceClusterRegistryHost(targetProject)
      steps.sh(
        script: """
          oc -n ${targetProject} import-image ${name}:${imageTag} \
            --from=${sourceClusterRegistryHost}/${sourceProject}/${name}@${imageSha} \
            --confirm
        """,
        label: "import image ${sourceClusterRegistryHost}/${sourceProject}/${name}@${imageSha} into ${targetProject}/${name}:${imageTag}"
      )
    }

    String getSourceClusterRegistryHost(String project) {
      def secretName = 'mro-image-pull'
      def dockerConfig = steps.sh(
        script: """
        oc -n ${project} get secret ${secretName} --output="jsonpath={.data.\\.dockerconfigjson}" | base64 --decode
        """,
        returnStdout: true,
        label: "read source cluster registry host from ${secretName}"
      )
      def dockerConfigJson = steps.readJSON(text: dockerConfig)
      def auths = dockerConfigJson.auths
      def authKeys = auths.keySet()
      if (authKeys.size() > 1) {
        throw new RuntimeException("Error: 'dockerconfigjson' of secret '${secretName}' has more than one registry host entry.")
      }
      authKeys.first()
    }

    void importImageFromProject(String name, String sourceProject, String imageSha, String targetProject, String imageTag) {
      steps.sh(
        script: """
          oc -n ${targetProject} tag ${sourceProject}/${name}@${imageSha} ${name}:${imageTag}
        """,
        label: "tag image ${name} into ${targetProject}"
      )
    }

    void tagImageWithLatest(String name, String project, String imageTag) {
      steps.sh(
        script: """
          oc -n ${project} tag ${name}:${imageTag} ${name}:latest
        """,
        label: "tag image ${name}:${imageTag} with latest"
      )
    }

    boolean envExists(String name) {
      def environment = name
      steps.echo "searching for ${environment}"
      def statusCode = steps.sh(
        script:"oc project ${environment} &> /dev/null",
        label :"check if OCP environment exists",
        returnStatus: true
      )
      steps.echo "searching for ${environment} - result ${statusCode}"
      return (statusCode == 0) 
    }

    boolean tooManyEnvironments(String projectPrefix, Integer limit) {
      steps.sh(
        returnStdout: true,
        script: "oc projects | grep '^\\s*${projectPrefix}' | wc -l",
        label : "check ocp environment maximum"
      ).trim().toInteger() >= limit
    }

    def createVersionedDevelopmentEnvironment(String projectKey, String sourceEnvName, String newEnvName) {
      def limit = 3
      if (tooManyEnvironments("${projectKey}-dev-", limit)) {
        throw new RuntimeException("Error: only ${limit} versioned ${projectKey}-dev-* environments are allowed. Please clean up and run the pipeline again.")
      }
      def newNamespace = "${projectKey}-${newEnvName}"
      def tmpDir = "tmp-${newNamespace}"
      steps.sh(
        script: "mkdir -p ${tmpDir}",
        label: "Ensure ${tmpDir} exists"
      )
      steps.dir(tmpDir) {
        createProject(newNamespace)
        doTailorExport("${projectKey}-${sourceEnvName}", 'serviceaccount,rolebinding', [:], 'template.yml')
        doTailorApply(newNamespace, 'serviceaccount,rolebinding --upsert-only')
        steps.deleteDir()
      }
    }

    def createProject(String name) {
      steps.sh(
        script: "oc new-project ${name}",
        label: "create new OpenShift project ${name}"
      )
    }

    private String buildParamFileFlag(String paramFile) {
      def paramFileFlag = ''
      if (paramFile) {
        paramFileFlag = "--param-file ${paramFile}"
      }
      paramFileFlag
    }

    private String tailorPrivateKeyFlag() {
      if (tailorPrivateKeyFile) {
        return "--private-key ${tailorPrivateKeyFile}"
      }
      ''
    }

    private String tailorVerboseFlag() {
      if (steps.env.DEBUG) {
        return '--verbose'
      }
      ''
    }

    Map getPodDataForComponent(String name) {
        String stdout = this.steps.sh(
          script: "oc get pod -l component=${name} -o json --show-all=false",
          returnStdout: true,
          label: "Getting OpenShift Pod data for ${name}"
        ).trim()

        return new JsonSlurperClassic().parseText(stdout)
    }
}
