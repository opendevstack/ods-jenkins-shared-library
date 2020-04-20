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
      String openshiftAppDomain = getOpenshiftApplicationDomain(project)
      doTailorApply(project, "-l ${selector} ${excludeFlag} ${buildParamFileFlag(paramFile)} --param=ODS_OPENSHIFT_APP_DOMAIN=${openshiftAppDomain} --ignore-unknown-parameters ${tailorPrivateKeyFlag()} ${verifyFlag}")
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
      envParams['ODS_OPENSHIFT_APP_DOMAIN'] = getOpenshiftApplicationDomain(project)
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

    String getRunningImageSha(String project, String component, String version, index = 0) {
      def runningImage = steps.sh(
        script: "oc -n ${project} get rc/${component}-${version} -o jsonpath='{.spec.template.spec.containers[${index}].image}'",
        label: "Get running image for rc/${component}-${version} containerIndex: ${index}",
        returnStdout: true
      ).trim()
      return runningImage.substring(runningImage.lastIndexOf("@sha256:") + 1)
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
        script: """oc -n ${targetProject} tag ${sourceProject}/${name}@${imageSha} ${name}:${imageTag}""",
        label: "tag image ${name} into ${targetProject}"
      )
    }

    void tagImageWithLatest(String name, String project, String sourceTag) {
      steps.sh(
        script: """oc -n ${project} tag ${name}:${sourceTag} ${name}:latest""",
        label: "tag image ${name}:${sourceTag} with latest"
      )
    }

    void tagImageSha(String name, String project, String sourceSha, String targetTag) {
      def shaPrefix = 'sha256:'
      if (!sourceSha.startsWith(shaPrefix)) {
        sourceSha = "${shaPrefix}${sourceSha}"
      }
      steps.sh(
        script: """oc -n ${project} tag ${name}@${sourceSha} ${name}:${targetTag}""",
        label: "tag image ${name}@${sourceSha} with ${targetTag}"
      )
    }

    boolean envExists(String name) {
      def environment = name
      steps.echo("searching for ${environment}")
      def statusCode = steps.sh(
        script: "oc project ${environment} &> /dev/null",
        label: "check if OCP environment exists",
        returnStatus: true
      )
      steps.echo("searching for ${environment} - result ${statusCode}")
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

    // Gets pod of deployment
    Map getPodDataForDeployment(String component, String version) {
      def deployment = "${component}-${version}"
      def stdout = this.steps.sh(
        script: "oc get pod -l deployment=${deployment} -o json",
        returnStdout: true,
        label: "Getting OpenShift pod data for deployment ${deployment}"
      ).trim()

      extractPodData(stdout, "deployment '${deployment}'")
    }

    // Gets current pod for component
    Map getPodDataForComponent(String project, String component) {
      def componentSelector = "app=${project}-${component}"
      def stdout = this.steps.sh(
        script: "oc get pod -l ${componentSelector} -o json --show-all=false",
        returnStdout: true,
        label: "Getting OpenShift pod data for component ${component}"
      ).trim()

      extractPodData(stdout, "component '${component}'")
    }

    private Map extractPodData(String ocOutput, String description) {
      def j = new JsonSlurperClassic().parseText(ocOutput)
      if (j?.items[0]?.status?.phase?.toLowerCase() != 'running') {
        throw new RuntimeException("Error: no pod for ${description} running / found.")
      }

      def podOCData = j.items[0]

      // strip all data not needed out
      def pod = [ : ]
        pod.podName = podOCData?.metadata?.name?: "N/A"
        pod.podNamespace = podOCData?.metadata?.namespace?: "N/A"
        pod.podMetaDataCreationTimestamp = podOCData?.metadata?.creationTimestamp?: "N/A"
        pod.deploymentId = podOCData?.metadata?.annotations['openshift.io/deployment.name']?: "N/A"
        pod.podNode = podOCData?.spec?.nodeName ?: "N/A"
        pod.podIp = podOCData?.status?.podIP ?: "N/A"
        pod.podStatus = podOCData?.status?.phase ?: "N/A"
        pod.podStartupTimeStamp = podOCData?.status?.startTime ?: "N/A"
        pod["containers"] = [ : ]
        
        podOCData?.spec?.containers?.each { container ->
          pod.containers[container.name] = container.image
        } 
      return pod
    }

    List getDeploymentConfigsForComponent(String componentSelector) {
      def stdout = this.steps.sh(
        script: "oc get dc -l ${componentSelector} -o jsonpath='{.items[*].metadata.name}'",
        returnStdout: true,
        label: "Getting all deploymentconfig names for selector '${componentSelector}'"
      ).trim()

      def deploymentNames = []

      stdout.tokenize(" ").each {dc -> 
        deploymentNames.add (dc)
      }
      return deploymentNames
    }
    
    String getOpenshiftApplicationDomain (String project) {
      def routeName = "test-route-" + System.currentTimeMillis()
      this.steps.sh (
        script: "oc -n ${project} create route edge ${routeName} --service=dummy --port=80 | true",
        label : "create dummy route for extraction (${routeName})")
      def routeUrl = this.steps.sh (script: "oc -n ${project} get route ${routeName} -o jsonpath='{.spec.host}'",
        returnStdout : true, label : "get cluster route domain")
      def routePrefixLength = "${routeName}-${project}".length() + 1
      String openShiftPublicHost = routeUrl.substring(routePrefixLength)
      this.steps.sh (script: "oc -n ${project} delete route ${routeName} | true",
        label : "delete dummy route for extraction (${routeName})")
      
      return openShiftPublicHost
    }
    
  Map<String, String> getImageInformationFromImageUrl (String url) {
    this.steps.echo ("Deciphering imageURL ${url} into pieces")
    def imageInformation = [ : ]
    List <String> imagePath
    if (url?.contains("@")) {
      List <String> imageStreamDefinition = (url.split ("@"))
      imageInformation [ "imageSha" ] = imageStreamDefinition [1]
      imageInformation [ "imageShaStripped" ] = (imageStreamDefinition [1]).replace("sha256:","")
      imagePath = imageStreamDefinition[0].split("/")
    } else {
      url = url.split(":")[0]
      imagePath = url.split("/")
      imageInformation [ "imageSha" ] = url
      imageInformation [ "imageShaStripped" ] = url
    }
    imageInformation [ "imageStreamProject" ] = imagePath[imagePath.size()-2]
    imageInformation [ "imageStream" ] = imagePath[imagePath.size()-1]
      
    return imageInformation
  }

}
