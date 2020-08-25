package org.ods.services

import groovy.json.JsonSlurperClassic

import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import java.security.SecureRandom

@SuppressWarnings('MethodCount')
class OpenShiftService {

    static final String EXPORTED_TEMPLATE_FILE = 'template.yml'

    private final IPipelineSteps steps
    private final ILogger logger
    private final String project
    private String appDomain

    OpenShiftService(IPipelineSteps steps, ILogger logger, String project) {
        this.steps = steps
        this.logger = logger
        this.project = project
    }

    static void createProject(IPipelineSteps steps, String name) {
        steps.sh(
            script: "oc new-project ${name}",
            label: "Create new OpenShift project ${name}"
        )
    }

    static void loginToExternalCluster(IPipelineSteps steps, String apiUrl, String apiToken) {
        steps.sh(
            script: """oc login ${apiUrl} --token=${apiToken} >& /dev/null""",
            label: "login to external cluster (${apiUrl})"
        )
    }

    static String getApiUrl(IPipelineSteps steps) {
        steps.sh(
            script: 'oc whoami --show-server',
            label: 'Get OpenShift API server URL',
            returnStdout: true
        ).trim()
    }

    static boolean tooManyEnvironments(IPipelineSteps steps, String projectId, Integer limit) {
        steps.sh(
            returnStdout: true,
            script: "oc projects | grep '^\\s*${projectId}-' | wc -l",
            label: "check ocp environment maximum for '${projectId}-*'"
        ).trim().toInteger() >= limit
    }

    static boolean envExists(IPipelineSteps steps, String project) {
        def exists = steps.sh(
            script: "oc project ${project} &> /dev/null",
            label: "Check if OpenShift project '${project}' exists",
            returnStatus: true
        ) == 0
        steps.echo("OpenShift project '${project}' ${exists ? 'exists' : 'does not exist'}")
        exists
    }

    static String getApplicationDomainOfProject(IPipelineSteps steps, String project) {
        def routeName = 'test-route-' + (System.currentTimeMillis() +
            new SecureRandom().nextInt(1000))
        steps.sh (
            script: "oc -n ${project} create route edge ${routeName} --service=dummy --port=80 | true",
            label: "create dummy route for extraction (${routeName})"
        )
        def routeUrl = steps.sh (
            script: "oc -n ${project} get route ${routeName} -o jsonpath='{.spec.host}'",
            returnStdout: true,
            label: 'get cluster route domain'
        ).trim()
        def routePrefixLength = "${routeName}-${project}".length() + 1
        def openShiftPublicHost = routeUrl[routePrefixLength..-1]
        steps.sh (
            script: "oc -n ${project} delete route ${routeName} | true",
            label: "delete dummy route for extraction (${routeName})"
        )
        return openShiftPublicHost
    }

    static String getImageReference(IPipelineSteps steps, String project, String name, String tag) {
        steps.sh(
            returnStdout: true,
            script: "oc -n ${project} get istag ${name}:${tag} -o jsonpath='{.image.dockerImageReference}'",
            label: "Get image reference of ${name}:${tag}"
        ).trim()
    }

    static String imageExists(IPipelineSteps steps, String project, String name, String tag) {
        steps.sh(
            returnStatus: true,
            script: "oc -n ${project} get istag ${name}:${tag} &> /dev/null",
            label: "Check existance of image ${name}:${tag}"
        ) == 0
    }

    boolean envExists() {
        envExists(steps, project)
    }

    def createVersionedDevelopmentEnvironment(String projectKey, String sourceEnvName) {
        def limit = 3
        if (tooManyEnvironments(steps, "${projectKey}-dev-", limit)) {
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
            createProject(steps, project)
            doTailorExport("${projectKey}-${sourceEnvName}", 'serviceaccount,rolebinding', [:], 'template.yml')
            doTailorApply('serviceaccount,rolebinding --upsert-only')
            steps.deleteDir()
        }
    }

    String getApiUrl() {
        getApiUrl(steps)
    }

    @SuppressWarnings(['LineLength', 'ParameterCount'])
    void tailorApply(Map<String, String> target, String paramFile, List<String> params, List<String> preserve, String tailorPrivateKeyFile, boolean verify) {
        def verifyFlag = verify ? '--verify' : ''
        def tailorPrivateKeyFlag = tailorPrivateKeyFile ? "--private-key ${tailorPrivateKeyFile}" : ''
        def selectorFlag = target.selector ? "--selector ${target.selector}" : ''
        def excludeFlag = target.exclude ? "--exclude ${target.exclude}" : ''
        def includeArg = target.include ?: ''
        def paramFileFlag = paramFile ? "--param-file ${paramFile}" : ''
        params << "ODS_OPENSHIFT_APP_DOMAIN=${getApplicationDomain()}"
        def paramFlags = params.collect { "--param ${it}" }.join(' ')
        def preserveFlags = preserve.collect { "--preserve ${it}" }.join(' ')
        doTailorApply("${selectorFlag} ${excludeFlag} ${paramFlags} ${preserveFlags} ${paramFileFlag} ${tailorPrivateKeyFlag} ${verifyFlag} --ignore-unknown-parameters ${includeArg}")
    }

    void tailorExport(String selector, Map<String, String> envParams, String targetFile) {
        doTailorExport(project, "-l ${selector}", envParams, targetFile)
    }

    String rollout(String name, int priorVersion, int timeoutMinutes) {
        def latestVersion = getLatestVersion(name)
        if (latestVersion > priorVersion) {
            logger.info "Rollout of deployment for '${name}' has been triggered automatically."
        } else {
            startRollout(name, latestVersion)
        }
        try {
            steps.timeout(time: timeoutMinutes) {
                logger.startClocked("${name}-watch-rollout")
                watchRollout(name)
                logger.debugClocked("${name}-watch-rollout")
            }
        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
            latestVersion = getLatestVersion(name)
            def replicationController = "${name}-${latestVersion}"
            throw new RuntimeException(
                'Deployment timed out. ' +
                "Observed related event messages:\n${getEventMessages(replicationController)}"
            )
        }

        latestVersion = getLatestVersion(name)
        def replicationController = "${name}-${latestVersion}"
        def rolloutStatus = getRolloutStatus(replicationController)
        if (rolloutStatus != 'complete') {
            throw new RuntimeException(
                "Deployment #${latestVersion} failed with status '${rolloutStatus}'. " +
                "Observed related event messages:\n${getEventMessages(replicationController)}"
            )
        } else {
            logger.info "Deployment #${latestVersion} of '${name}' successfully rolled out."
        }
        replicationController
    }

    void startRollout(String name, int version) {
        try {
            steps.sh(
                script: "oc -n ${project} rollout latest dc/${name}",
                label: "Rollout latest version of dc/${name}"
            )
        } catch (ex) {
            // It could be that some other process (e.g. image trigger) started
            // a rollout just before we wanted to start it. In that case, we
            // do not need to fail.
            def newVersion = getLatestVersion(name)
            if (newVersion > version) {
                logger.debug("Deployment #${newVersion} has been started by another process")
            } else {
                throw ex
            }
        }
    }

    void watchRollout(String name) {
        steps.sh(
            script: "oc -n ${project} rollout status dc/${name} --watch=true",
            label: "Watch rollout of latest version of dc/${name}"
        )
    }

    int getLatestVersion(String name) {
        def versionNumber = steps.sh(
            script: "oc -n ${project} get dc/${name} -o jsonpath='{.status.latestVersion}'",
            label: "Get latest version of dc/${name}",
            returnStdout: true
        ).trim()
        if (!versionNumber.isInteger()) {
            throw new RuntimeException(
                "ERROR: Latest version of DeploymentConfig '${name}' is not a number: '${versionNumber}"
            )
        }
        versionNumber as int
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

    int startBuild(String name, String dir) {
        steps.sh(
            script: "oc -n ${project} start-build ${name} --from-dir ${dir}",
            label: "Start Openshift build ${name}",
            returnStdout: true
        ).trim()
        return getLastBuildVersion(name)
    }

    String followBuild(String name, int version) {
        steps.sh(
            script: "oc logs --follow --version${version} bc/${name}"
        ).trim()
    }

    int getLastBuildVersion(String name) {
        def versionNumber = steps.sh(
            returnStdout: true,
            script: "oc -n ${project} get bc/${name} -o jsonpath='{.status.lastVersion}'",
            label: "Get lastVersion of BuildConfig ${name}"
        ).trim()
        if (!versionNumber.isInteger()) {
            throw new RuntimeException(
                "ERROR: Last version of BuildConfig '${name}' is not a number: '${versionNumber}"
            )
        }
        versionNumber as int
    }

    String getBuildStatus(String buildId) {
        def buildStatus = 'unknown'
        def retries = 5
        for (def i = 0; i < retries; i++) {
            buildStatus = checkForBuildStatus(buildId)
            logger.debug ("Build: '${buildId}' - status: '${buildStatus}'")
            if (buildStatus == 'complete') {
                return buildStatus
            } else if (buildStatus == 'running') {
                // reset retries
                retries = 5
            }
            // Wait 12 seconds before asking again. Sometimes the build finishes but the
            // status is not set to "complete" immediately ...
            steps.sleep(12)
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
              ${odsImageLabels.join(',')}
            ]}""",
        ]

        // Add build args
        def buildArgsItems = []
        for (def key : buildArgs.keySet()) {
            def val = buildArgs[key]
            buildArgsItems << """{"name": "${key}", "value": "${val}"}"""
        }
        if (buildArgsItems.size() > 0) {
            def buildArgsPatch = """{"op": "replace", "path": "/spec/strategy/dockerStrategy/buildArgs", "value": [${buildArgsItems.join(',')}]}"""
            patches << buildArgsPatch
        }

        steps.sh(
            script: """oc -n ${project} patch bc ${name} --type=json --patch '[${patches.join(',')}]'""",
            label: "Patch BuildConfig ${name}"
        )
    }

    String getImageReference(String name, String tag) {
        getImageReference(steps, project, name, tag)
    }

    String getContainerForImage(String projectId, String rc, String image) {
        def jsonPath = """{.spec.template.spec.containers[?(contains .image "${image}")].name}"""
        steps.sh(
            script: "oc -n ${projectId} get rc ${rc} -o jsonpath='${jsonPath}'",
            returnStdout: true,
            label: "Getting containers for ${rc} and image ${image}"
        )
    }

    void importImageTagFromProject(String name, String sourceProject, String sourceTag, String targetTag) {
        importImageFromProject(sourceProject, "${name}:${sourceTag}", "${name}:${targetTag}")
    }

    void importImageTagFromSourceRegistry(
        String name,
        String sourceRegistrySecret,
        String sourceProject,
        String sourceTag,
        String targetTag) {
        importImageFromSourceRegistry(
            sourceRegistrySecret, sourceProject, "${name}:${sourceTag}", "${name}:${targetTag}"
        )
    }

    void importImageShaFromProject(String name, String sourceProject, String imageSha, String imageTag) {
        importImageFromProject(sourceProject, "${name}@${imageSha}", "${name}:${imageTag}")
    }

    void importImageShaFromSourceRegistry(
        String name,
        String sourceRegistrySecret,
        String sourceProject,
        String imageSha,
        String imageTag) {
        importImageFromSourceRegistry(
            sourceRegistrySecret, sourceProject, "${name}@${imageSha}", "${name}:${imageTag}"
        )
    }

    String getEventMessages(String rc) {
        String rcEventMessages
        try {
            rcEventMessages = steps.sh(
                script: """
                    oc -n ${project} get events \
                        --field-selector involvedObject.kind=ReplicationController,involvedObject.name=${rc} \
                        -o custom-columns=MESSAGE:.message --no-headers
                """,
                returnStdout: true,
                label: "Get event messages for replication controller ${rc}"
            ).trim()
        } catch (ex) {
            logger.debug("Error when retrieving events for replication controller ${rc}:\n${ex}")
        }
        if (!rcEventMessages) {
            return "Could not find any events for replication controller ${rc}."
        }
        rcEventMessages = "=== Events from ReplicationController '${rc}' ===\n${rcEventMessages}"
        String pod
        try {
            pod = steps.sh(
                script: """
                    oc -n ${project} get pod \
                        -l deployment=${rc} \
                        -o custom-columns=NAME:.metadata.name --no-headers | head -1
                """,
                returnStdout: true,
                label: "Get first pod name of replication controller ${rc}"
            ).trim()
        } catch (ex) {
            logger.debug("Error when retrieving pod for replication controller ${rc}:\n${ex}")
        }
        if (!pod) {
            return "${rcEventMessages}\nCould not find any pod for replication controller ${rc}."
        }
        String podEventMessages
        try {
            podEventMessages = steps.sh(
                script: """
                    oc -n ${project} get events \
                        --field-selector involvedObject.kind=Pod,involvedObject.name=${pod} \
                        -o custom-columns=MESSAGE:.message --no-headers
                """,
                returnStdout: true,
                label: "Get event messages for pod ${pod}"
            ).trim()
        } catch (ex) {
            logger.debug("Error when retrieving events for pod ${pod}:\n${ex}")
        }
        if (!podEventMessages) {
            return "${rcEventMessages}\nCould not find any events for pod for ${pod}."
        }
        "${rcEventMessages}\n=== Events from Pod '${pod}' ===\n${podEventMessages}"
    }

    Map getPodDataForDeployment(String rc) {
        def retries = 5
        for (def i = 0; i < retries; i++) {
            def podData = checkForPodData(rc)
            if (podData) {
                return podData
            }
            // Wait 12 seconds before asking again. Sometimes the deployment finishes but the
            // status is not set to "running" immediately ...
            steps.echo("Could not find 'running' pod for deployment=${rc} - waiting")
            steps.sleep(12)
        }
        throw new RuntimeException("Could not find 'running' pod for deployment=${rc}")
    }

    Map checkForPodData(String rc) {
        def stdout = steps.sh(
            script: "oc -n ${project} get pod -l deployment=${rc} -o json",
            returnStdout: true,
            label: "Getting OpenShift pod data for deployment ${rc}"
        ).trim()
        def podJson = new JsonSlurperClassic().parseText(stdout)
        if (podJson?.items[0]?.status?.phase?.toLowerCase() != 'running') {
            return [:]
        }
        extractPodData(podJson)
    }

    List getDeploymentConfigsForComponent(String componentSelector) {
        steps.sh(
            script: "oc -n ${project} get dc -l ${componentSelector} -o jsonpath='{.items[*].metadata.name}'",
            returnStdout: true,
            label: "Getting all DeploymentConfig names for selector '${componentSelector}'"
        ).trim().tokenize(' ')
    }

    String getApplicationDomain() {
        if (!this.appDomain) {
            this.appDomain = getApplicationDomainOfProject(steps, project)
        }
        this.appDomain
    }

    // imageInfoForImageUrl expects an image URL like one of the following:
    // 172.30.21.196:5000/foo/bar:2-3ec425bc
    // 172.30.21.196:5000/foo/bar@sha256:eec4a4451a307bd1fa44bde6642077a3c2a722e0ad370c1c22fcebcd8d4efd33
    // The registry part is optional.
    //
    // It returns a map with image parts:
    // - registry (empty if not specified)
    // - repository (= OpenShift project in case of image from ImageStream)
    // - name (= ImageStream name in case of image from ImageStream)
    Map<String, String> imageInfoForImageUrl(String url) {
        def imageInfo = [:]
        def urlParts = url.split('/').toList()

        if (urlParts.size() < 2) {
            logger.debug "Image URL ${url} does not define the repository explicitly."
            imageInfo.repository = ''
        } else {
            imageInfo.repository = urlParts[-2]
        }

        if (urlParts.size() > 2) {
            imageInfo.registry = urlParts[-3]
        } else {
            logger.debug "Image URL ${url} does not define the registry explicitly."
            imageInfo.registry = ''
        }

        if (urlParts[-1].contains('@')) {
            def shaParts = urlParts[-1].split('@').toList()
            imageInfo.name = shaParts[0]
        } else {
            def tagParts = urlParts[-1].split(':').toList()
            imageInfo.name = tagParts[0]
        }
        imageInfo
    }

    // imageInfoWithShaForImageStreamUrl expects an image URL like one of the following:
    // 172.30.21.196:5000/foo/bar:2-3ec425bc
    // 172.30.21.196:5000/foo/bar@sha256:eec4a4451a307bd1fa44bde6642077a3c2a722e0ad370c1c22fcebcd8d4efd33
    // The registry part is optional.
    //
    // It returns a map with image parts:
    // - registry (empty if not specified)
    // - repository (= OpenShift project in case of image from ImageStream)
    // - name (= ImageStream name in case of image from ImageStream)
    // - sha (= sha256:<sha-identifier>)
    // - shaStripped (= <sha-identifier>)
    Map<String, String> imageInfoWithShaForImageStreamUrl(String imageStreamUrl) {
        def urlParts = imageStreamUrl.split('/').toList()
        if (urlParts.size() < 2) {
            throw new RuntimeException(
                "ERROR: Image URL ${imageStreamUrl} must have at least two parts (repository/reference)"
            )
        }
        if (urlParts[-1].contains('@sha256:')) {
            return imageInfoWithSha(urlParts)
        }

        // If the imageStreamUrl contains a tag, we resolve it to a SHA.
        def tagParts = urlParts[-1].split(':')
        if (tagParts.size() != 2) {
            throw new RuntimeException(
                "ERROR: Image reference ${urlParts[2]} does not consist of two parts (name:tag)"
            )
        }
        def shaUrl = getImageReference(steps, urlParts[-2], tagParts[0], tagParts[1])
        imageInfoWithSha(shaUrl.split('/').toList())
    }

    List<Map<String, String>> getImagesOfDeploymentConfig(String dc) {
        def imageString = steps.sh (
            script: "oc -n ${project} get dc ${dc} -o jsonpath='{.spec.template.spec.containers[*].image}'",
            label: "Get container images for deploymentconfigs (${dc})",
            returnStdout: true
        )
        imageString.tokenize(' ').collect { imageInfoForImageUrl(it) }
    }

    String getOriginUrlFromBuildConfig(String bcName) {
        return steps.sh(
            script: "oc -n ${project} get bc/${bcName} -o jsonpath='{.spec.source.git.uri}'",
            returnStdout: true,
            label: "Get origin from BuildConfig ${bcName}"
        ).trim()
    }

    Map getPodDataForDeployments(List<String> deploymentNames) {
        Map pods = [:]
        deploymentNames.each { deploymentName ->
            Map podData = [:]
            logger.debug("Verifying images of deployment '${deploymentName}'")
            int latestDeployedVersion = 0
            try {
                latestDeployedVersion = getLatestVersion(deploymentName)
            } catch (err) {
                logger.debug("DeploymentConfig '${deploymentName}' does not exist!")
            }
            if (latestDeployedVersion < 1) {
                logger.debug("Image SHAs of '${deploymentName}' could not be compared")
            } else {
                podData = getPodDataForDeployment("${deploymentName}-${latestDeployedVersion}")
            }
            pods[deploymentName] = podData
        }
        pods
    }

    boolean areImageShasUpToDate(Map defData, Map podData) {
        defData.containers.every { containerName, definedImage ->
            verifyImageSha(containerName, definedImage, podData.containers[containerName])
        }
    }

    boolean verifyImageSha(String containerName, String definedImage, String actualImageRaw) {
        logger.debug("Verifiying image of container '${containerName}' ...")
        def runningImageSha = imageInfoWithShaForImageStreamUrl(actualImageRaw).sha
        def definedImageSha = definedImage.split('@').last()
        if (runningImageSha != definedImageSha) {
            logger.debug(
                "Container '${containerName}' is using image '${runningImageSha}' " +
                "which differs from defined image '${definedImageSha}'."
            )
            return false
        }
        logger.debug("Container '${containerName}' is using defined image '${definedImageSha}'.")
        true
    }

    private void importImageFromProject(String sourceProject, String sourceImageRef, String targetImageRef) {
        steps.sh(
            script: """oc -n ${project} tag ${sourceProject}/${sourceImageRef} ${targetImageRef}""",
            label: "Import image ${sourceImageRef} to ${project}/${targetImageRef}"
        )
    }

    private void importImageFromSourceRegistry(
        String sourceRegistrySecret,
        String sourceProject,
        String sourceImageRef,
        String targetImageRef) {
        def sourceClusterRegistryHost = getSourceClusterRegistryHost(sourceRegistrySecret)
        def sourceImageFull = "${sourceClusterRegistryHost}/${sourceProject}/${sourceImageRef}"
        steps.sh(
            script: """
              oc -n ${project} import-image ${targetImageRef} \
                --from=${sourceImageFull} \
                --confirm
            """,
            label: "Import image ${sourceImageFull} into ${project}/${targetImageRef}"
        )
    }

    @SuppressWarnings(['CyclomaticComplexity', 'AbcMetric'])
    private Map extractPodData(Map podJson) {
        def podOCData = podJson.items[0] ?: [:]

        def pod = [:]
        // Only set needed data on "pod"
        pod.podName = podOCData.metadata?.name ?: 'N/A'
        pod.podNamespace = podOCData.metadata?.namespace ?: 'N/A'
        pod.podMetaDataCreationTimestamp = podOCData.metadata?.creationTimestamp ?: 'N/A'
        pod.deploymentId = 'N/A'
        if (podOCData.metadata?.annotations && podOCData.metadata.annotations['openshift.io/deployment.name']) {
            pod.deploymentId = podOCData.metadata.annotations['openshift.io/deployment.name']
        }
        pod.podNode = podOCData.spec?.nodeName ?: 'N/A'
        pod.podIp = podOCData.status?.podIP ?: 'N/A'
        pod.podStatus = podOCData.status?.phase ?: 'N/A'
        pod.podStartupTimeStamp = podOCData.status?.startTime ?: 'N/A'
        pod.containers = [:]
        podOCData.spec?.containers?.each { container ->
            podOCData.status?.containerStatuses?.each { containerStatus ->
                if (containerStatus.name == container.name) {
                    pod.containers[container.name] = containerStatus.imageID - ~/^docker-pullable:\/\//
                }
            }
        }
        pod
    }

    private String tailorVerboseFlag() {
        steps.env.DEBUG ? '--verbose' : ''
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

    private void doTailorExport(
        String exportProject,
        String tailorParams,
        Map<String,
        String> envParams,
        String targetFile) {
        // Export
        steps.sh(
            script: "tailor ${tailorVerboseFlag()} -n ${exportProject} export ${tailorParams} > ${targetFile}",
            label: "Tailor export of ${exportProject} (${tailorParams}) into ${targetFile}"
        )

        // Tailor prior to 1.0.0 did not handle TAILOR_NAMESPACE automatically.
        // For backwards compatibility, fill it in if it is missing from the
        // template. This should not be necessary even in ODS 3, but can
        // definitely be removed in ODS 4.
        def templateContainsTailorNamespace = steps.sh(
            script: """grep "name: TAILOR_NAMESPACE" ${targetFile}""",
            label: 'Check if template contains TAILOR_NAMESPACE',
            returnStatus: true
        ) == 0
        if (!templateContainsTailorNamespace) {
            envParams['TAILOR_NAMESPACE'] = exportProject
            steps.sh(
                script: """echo "parameters:" >> ${targetFile}""",
                label: "Add parameters section into ${targetFile}"
            )
        }

        // Replace values from envParams with parameters, and add parameters into template.
        envParams['ODS_OPENSHIFT_APP_DOMAIN'] = getApplicationDomainOfProject(steps, exportProject)
        def templateParams = ''
        def sedReplacements = ''
        envParams.each { key, val ->
            sedReplacements += "s|${val}|\\\${${key}}|g;"
            templateParams += "- name: ${key}\n  required: true\n"
        }
        steps.sh(
            script: """
                sed -i -e "${sedReplacements}" ${targetFile}
                echo "${templateParams}" >> ${targetFile}
            """,
            label: "Add parameters into ${targetFile}"
        )
    }

    private String getSourceClusterRegistryHost(String secretName) {
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

    private String checkForBuildStatus(String buildId) {
        steps.sh(
            returnStdout: true,
            script: "oc -n ${project} get build ${buildId} -o jsonpath='{.status.phase}'",
            label: "Get phase of build ${buildId}"
        ).trim().toLowerCase()
    }

    private Map<String, String> imageInfoWithSha(List<String> urlParts) {
        def imageInfo = [:]
        def url = urlParts.join('/')
        if (urlParts.size() < 2 || !urlParts[-1].contains('@sha256:')) {
            throw new RuntimeException(
                "ERROR: Image URL '${url}' must be of form [REGISTRY/]REPOSITORY/NAME@sha256:ID)"
            )
        }
        if (urlParts.size() > 2) {
            imageInfo.registry = urlParts[-3]
        } else {
            logger.debug "Image URL ${url} does not define the registry explicitly."
            imageInfo.registry = ''
        }
        imageInfo.repository = urlParts[-2]
        def nameParts = urlParts[-1].split('@')
        imageInfo.name = nameParts[0]
        imageInfo.sha = nameParts[1]
        imageInfo.shaStripped = nameParts[1].replace('sha256:', '')
        imageInfo
    }

}
