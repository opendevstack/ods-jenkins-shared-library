package org.ods.services

import groovy.json.JsonSlurperClassic

import org.ods.util.ILogger
import org.ods.util.IPipelineSteps

@SuppressWarnings(['MethodCount'])
class OpenShiftService {

    private final IPipelineSteps steps
    private final ILogger logger
    private final String project

    OpenShiftService(IPipelineSteps steps, ILogger logger, String project) {
        this.steps = steps
        this.logger = logger
        this.project = project
    }

    // TODO

    void loginToExternalCluster(String apiUrl, String apiToken) {
        steps.sh(
            script: """oc login ${apiUrl} --token=${apiToken} >& /dev/null""",
            label: "login to external cluster (${apiUrl})"
        )
    }

    String getApiUrl() {
        steps.sh(
            script: 'oc whoami --show-server',
            label: 'Get OpenShift API server URL',
            returnStdout: true
        ).trim()
    }

    boolean tooManyEnvironments(String projectId, Integer limit) {
        steps.sh(
            returnStdout: true,
            script: "oc projects | grep '^\\s*${projectId}-' | wc -l",
            label: "check ocp environment maximum for '${projectId}-*'"
        ).trim().toInteger() >= limit
    }

    // END TODO

    boolean envExists() {
        def statusCode = steps.sh(
            script: "oc project ${project} &> /dev/null",
            label: "Check if OpenShift project '${project}' exists",
            returnStatus: true
        )
        def exists = statusCode == 0
        steps.echo("OpenShift project '${project}' ${exists ? 'exists' : 'does not exist'}")
        return exists
    }

    def createVersionedDevelopmentEnvironment(String projectKey, String sourceEnvName) {
        def limit = 3
        if (tooManyEnvironments("${projectKey}-dev-", limit)) {
            throw new RuntimeException(
                "Error: only ${limit} versioned ${projectKey}-dev-* environments are allowed. " +
                'Please clean up and run the pipeline again.'
            )
        }
        def tmpDir = "tmp-${project}"
        steps.sh(
            script: "mkdir -p ${tmpDir}",
            label: "Ensure ${tmpDir} exists"
        )
        steps.dir(tmpDir) {
            createProject(project)
            doTailorExport("${projectKey}-${sourceEnvName}", 'serviceaccount,rolebinding', [:], 'template.yml')
            doTailorApply('serviceaccount,rolebinding --upsert-only')
            steps.deleteDir()
        }
    }

    void tailorApply(String selector, String exclude, String paramFile, String tailorPrivateKeyFile, boolean verify) {
        def verifyFlag = ''
        if (verify) {
            verifyFlag = '--verify'
        }
        def excludeFlag = ''
        if (exclude) {
            excludeFlag = "--exclude ${exclude}"
        }
        def tailorPrivateKeyFlag = ''
        if (tailorPrivateKeyFile) {
            tailorPrivateKeyFlag = "--private-key ${tailorPrivateKeyFile}"
        }
        def tailorParams = """
        -l ${selector} ${excludeFlag} ${buildParamFileFlag(paramFile)} ${tailorPrivateKeyFlag} ${verifyFlag} \
        --param=ODS_OPENSHIFT_APP_DOMAIN=${getApplicationDomainOfProject(project)} \
        --ignore-unknown-parameters
        """
        doTailorApply(tailorParams)
    }

    void tailorExport(String selector, Map<String, String> envParams, String targetFile) {
        doTailorExport(project, "-l ${selector}", envParams, targetFile)
    }

    void startRollout(String name) {
        steps.sh(
            script: "oc -n ${project} rollout latest dc/${name}",
            label: "Rollout latest version of dc/${name}"
        )
    }

    void watchRollout(String name) {
        steps.sh(
            script: "oc -n ${project} rollout status dc/${name} --watch=true",
            label: "Watch rollout of latest version of dc/${name}"
        )
    }

    String getLatestVersion(String name) {
        steps.sh(
            script: "oc -n ${project} get dc/${name} -o jsonpath='{.status.latestVersion}'",
            label: "Get latest version of dc/${name}",
            returnStdout: true
        ).trim()
    }

    String getRolloutStatus(String name) {
        def jsonPath = '{.metadata.annotations.openshift\\.io/deployment\\.phase}'
        steps.sh(
            script: "oc -n ${project} get rc/${name} -o jsonpath='${jsonPath}'",
            label: "Get status of ReplicationController ${name}",
            returnStdout: true
        ).trim().toLowerCase()
    }

    void setImageTag(String name, String sourceTag, String destinationTag) {
        steps.sh(
            script: "oc -n ${project} tag ${name}:${sourceTag} ${name}:${destinationTag}",
            label: "Set tag ${destinationTag} on is/${name}"
        )
    }

    @SuppressWarnings('LineLength')
    boolean automaticImageChangeTriggerEnabled(String name) {
        def tmpl = '{{range .spec.triggers}}{{if eq .type "ImageChange" }}{{.imageChangeParams.automatic}}{{end}}{{end}}'
        try {
            def automaticValue = steps.sh(
                script: """oc -n ${project} get dc/${name} -o template --template '${tmpl}'""",
                returnStdout: true,
                label: "Check ImageChange trigger for dc/${name}"
            ).trim()
            automaticValue.contains('true')
        } catch (Exception ex) {
            return false
        }
    }

    boolean resourceExists(String kind, String name) {
        steps.sh(
            script: "oc -n ${project} get ${kind}/${name} &> /dev/null",
            returnStatus: true,
            label: "Check existance of ${kind} ${name}"
        ) == 0
    }

    String startAndFollowBuild(String name, String dir) {
        steps.sh(
            script: "oc -n ${project} start-build ${name} --from-dir ${dir} --follow",
            label: "Start and follow OpenShift build ${name}",
            returnStdout: true
        ).trim()
    }

    String getLastBuildVersion(String name) {
        steps.sh(
            returnStdout: true,
            script: "oc -n ${project} get bc ${name} -o jsonpath='{.status.lastVersion}'",
            label: "Get lastVersion of BuildConfig ${name}"
        ).trim()
    }

    String getBuildStatus(String buildId) {
        def buildStatus = 'unknown'
        def retries = 3
        for (def i = 0; i < retries; i++) {
            buildStatus = checkForBuildStatus(buildId)
            if (buildStatus == 'complete') {
                return buildStatus
            }
            // Wait 5 seconds before asking again. Sometimes the build finishes but the
            // status is not set to "complete" immediately ...
            steps.sleep(5)
        }
        return buildStatus
    }

    @SuppressWarnings('LineLength')
    void patchBuildConfig(String name, String tag, Map buildArgs, Map imageLabels) {
        def odsImageLabels = []
        for (def key : imageLabels.keySet()) {
            odsImageLabels << """{"name": "${key}", "value": "${imageLabels[key]}"}"""
        }

        // Normally we want to replace the imageLabels, but in case they are not
        // present yet, we need to add them this time.
        def imageLabelsOp = 'replace'
        def imageLabelsValue = steps.sh(
            script: "oc -n ${project} get bc/${name} -o jsonpath='{.spec.output.imageLabels}'",
            returnStdout: true,
            label: 'Test existance of path .spec.output.imageLabels'
        ).trim()
        if (imageLabelsValue.length() == 0) {
            imageLabelsOp = 'add'
        }

        def patches = [
            '{"op": "replace", "path": "/spec/source", "value": {"type":"Binary"}}',
            """{"op": "replace", "path": "/spec/output/to/name", "value": "${name}:${tag}"}""",
            """{"op": "${imageLabelsOp}", "path": "/spec/output/imageLabels", "value": [
              ${odsImageLabels.join(",")}
            ]}"""
        ]

        // Add build args
        def buildArgsItems = []
        for (def key : buildArgs.keySet()) {
            def val = buildArgs[key]
            buildArgsItems << """{"name": "${key}", "value": "${val}"}"""
        }
        if (buildArgsItems.size() > 0) {
            def buildArgsPatch = """{"op": "replace", "path": "/spec/strategy/dockerStrategy/buildArgs", "value": [${buildArgsItems.join(",")}]}"""
            patches << buildArgsPatch
        }

        steps.sh(
            script: """oc -n ${project} patch bc ${name} --type=json --patch '[${patches.join(",")}]'""",
            label: "Patch BuildConfig ${name}"
        )
    }

    String getImageReference(String name, String tag) {
        steps.sh(
            returnStdout: true,
            script: "oc get -n ${project} istag ${name}:${tag} -o jsonpath='{.image.dockerImageReference}'",
            label: "Get image reference of ${name}:${tag}"
        ).trim()
    }

    private String checkForBuildStatus(String buildId) {
        steps.sh(
            returnStdout: true,
            script: "oc -n ${project} get build ${buildId} -o jsonpath='{.status.phase}'",
            label: "Get phase of build ${buildId}"
        ).trim().toLowerCase()
    }

    String getContainerForImage(String projectId, String rc, String image) {
        def jsonPath = """{.spec.template.spec.containers[?(contains .image "${image}")].name}"""
        steps.sh(
            script: "oc -n ${projectId} get rc ${rc} -o jsonpath='${jsonPath}'",
            returnStdout: true,
            label: "Getting containers for ${rc} and image ${image}"
        )
    }

    String getRunningImageSha(String component, String version, index = 0) {
        def runningImage = steps.sh(
            script: """oc -n ${project} get rc/${component}-${version} \
            -o jsonpath='{.spec.template.spec.containers[${index}].image}'
            """,
            label: "Get running image for rc/${component}-${version} containerIndex: ${index}",
            returnStdout: true
        ).trim()
        return runningImage.substring(runningImage.lastIndexOf("@sha256:") + 1)
    }

    void importImageFromProject(String name, String sourceProject, String imageSha, String imageTag) {
        steps.sh(
            script: """oc -n ${project} tag ${sourceProject}/${name}@${imageSha} ${name}:${imageTag}""",
            label: "tag image ${name} into ${project}"
        )
    }

    void importImageFromSourceRegistry(String name, String sourceProject, String imageSha, String imageTag) {
        def sourceClusterRegistryHost = getSourceClusterRegistryHost()
        def sourceImage = "${sourceClusterRegistryHost}/${sourceProject}/${name}@${imageSha}"
        steps.sh(
            script: """
              oc -n ${project} import-image ${name}:${imageTag} \
                --from=${sourceImage} \
                --confirm
            """,
            label: "import image ${sourceImage} into ${project}/${name}:${imageTag}"
        )
    }

    private String getSourceClusterRegistryHost() {
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
            throw new RuntimeException(
                "Error: 'dockerconfigjson' of secret '${secretName}' has more than one registry host entry."
            )
        }
        authKeys.first()
    }

    // Gets pod of deployment
    Map getPodDataForDeployment(String rc) {
        def index = 5
        while (index > 0) {
            def podStatus = steps.sh(
                script: "oc -n ${project} get pod -l deployment=${rc} -o jsonpath='{.items[*].status.phase}'",
                returnStdout: true,
                label: "Getting OpenShift pod data for deployment ${rc}"
            )
            if (podStatus && podStatus == 'Running') {
                break
            } else {
                steps.sleep(60)
                index--
            }
        }

        def stdout = steps.sh(
            script: "oc -n ${project} get pod -l deployment=${rc} -o json",
            returnStdout: true,
            label: "Getting OpenShift pod data for deployment ${rc}"
        ).trim()

        extractPodData(stdout, "deployment '${rc}'")
    }

    List getDeploymentConfigsForComponent(String componentSelector) {
        steps.sh(
            script: "oc -n ${project} get dc -l ${componentSelector} -o jsonpath='{.items[*].metadata.name}'",
            returnStdout: true,
            label: "Getting all DeploymentConfig names for selector '${componentSelector}'"
        ).trim().tokenize(' ')
    }

    String getApplicationDomain() {
        getApplicationDomainOfProject(project)
    }

    Map<String, String> getImageInformationFromImageUrl(String url) {
        steps.echo("Deciphering imageURL ${url} into pieces")
        def imageInformation = [:]
        List<String> imagePath
        if (url?.contains('@')) {
            List<String> imageStreamDefinition = (url.split('@'))
            imageInformation['imageSha'] = imageStreamDefinition[1]
            imageInformation['imageShaStripped'] = (imageStreamDefinition[1]).replace('sha256:', '')
            imagePath = imageStreamDefinition[0].split('/')
        } else {
            url = url.split(':')[0]
            imagePath = url.split('/')
            imageInformation['imageSh'] = url
            imageInformation['imageShaStrippe'] = url
        }
        imageInformation['imageStreamProject'] = imagePath[imagePath.size()-2]
        imageInformation['imageStream'] = imagePath[imagePath.size()-1]

        return imageInformation
    }

    List<Map<String, String>> getImageStreamsForDeploymentConfig(String dc) {
        def imageString = steps.sh (
            script: "oc -n ${project} get dc ${dc} -o jsonpath='{.spec.template.spec.containers[*].image}'",
            label: "Get container images for deploymentconfigs (${dc})",
            returnStdout: true
        )
        imageString.tokenize(' ').collect { getImageInformationFromImageUrl(it) }
    }

    String getOriginUrlFromBuildConfig(String bcName) {
        return steps.sh(
            script: "oc -n ${project} get bc/${bcName} -o jsonpath='{.spec.source.git.uri}'",
            returnStdout: true,
            label: "get origin from openshift bc ${bcName}"
        ).trim()
    }

    @SuppressWarnings('CyclomaticComplexity')
    private Map extractPodData(String ocOutput, String description) {
        def j = new JsonSlurperClassic().parseText(ocOutput)
        if (j?.items[0]?.status?.phase?.toLowerCase() != 'running') {
            throw new RuntimeException("Error: no pod for ${description} running / found.")
        }

        def podOCData = j.items[0]

        // strip all data not needed out
        def pod = [:]
        pod.podName = podOCData?.metadata?.name ?: 'N/A'
        pod.podNamespace = podOCData?.metadata?.namespace ?: 'N/A'
        pod.podMetaDataCreationTimestamp = podOCData?.metadata?.creationTimestamp ?: 'N/A'
        pod.deploymentId = podOCData?.metadata?.annotations['openshift.io/deployment.name'] ?: 'N/A'
        pod.podNode = podOCData?.spec?.nodeName ?: 'N/A'
        pod.podIp = podOCData?.status?.podIP ?: 'N/A'
        pod.podStatus = podOCData?.status?.phase ?: 'N/A'
        pod.podStartupTimeStamp = podOCData?.status?.startTime ?: 'N/A'
        pod['containers'] = [:]

        podOCData?.spec?.containers?.each { container ->
            pod.containers[container.name] = container.image
        }
        return pod
    }

    private String buildParamFileFlag(String paramFile) {
        def paramFileFlag = ''
        if (paramFile) {
            paramFileFlag = "--param-file ${paramFile}"
        }
        paramFileFlag
    }

    private String tailorVerboseFlag() {
        if (steps.env.DEBUG) {
            return '--verbose'
        }
        ''
    }

    private def createProject() {
        steps.sh(
            script: "oc new-project ${project}",
            label: "create new OpenShift project ${project}"
        )
    }

    private void doTailorApply(String tailorParams) {
        steps.sh(
            script: """tailor \
              ${tailorVerboseFlag()} \
              --non-interactive \
              -n ${project} \
              apply ${tailorParams}""",
            label: "tailor apply for ${project} (${tailorParams})"
        )
    }

    private void doTailorExport(String exportProject, String tailorParams, Map<String, String> envParams, String targetFile) {
        envParams['TAILOR_NAMESPACE'] = exportProject
        envParams['ODS_OPENSHIFT_APP_DOMAIN'] = getApplicationDomainOfProject(exportProject)
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
                    -n ${exportProject} \
                    export ${tailorParams} > ${targetFile}
                    sed -i -e "${sedReplacements}" ${targetFile}
                    echo "parameters:" >> ${targetFile}
                    echo "${templateParams}" >> ${targetFile}
            """,
            label: "tailor export of ${exportProject} (${tailorParams}) into ${targetFile}"
        )
    }

    private String getApplicationDomainOfProject(String appProject) {
        def routeName = 'test-route-' + System.currentTimeMillis()
        steps.sh (
            script: "oc -n ${appProject} create route edge ${routeName} --service=dummy --port=80 | true",
            label: "create dummy route for extraction (${routeName})"
        )
        def routeUrl = steps.sh (
            script: "oc -n ${appProject} get route ${routeName} -o jsonpath='{.spec.host}'",
            returnStdout: true,
            label: 'get cluster route domain'
        )
        def routePrefixLength = "${routeName}-${appProject}".length() + 1
        String openShiftPublicHost = routeUrl.substring(routePrefixLength)
        steps.sh (
            script: "oc -n ${appProject} delete route ${routeName} | true",
            label: "delete dummy route for extraction (${routeName})"
        )
        return openShiftPublicHost
    }
}
