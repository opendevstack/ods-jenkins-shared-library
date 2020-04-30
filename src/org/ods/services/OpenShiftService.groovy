package org.ods.services

import groovy.json.JsonSlurperClassic

class OpenShiftService {

    private def script
    private String project

    OpenShiftService(def script, String project) {
        this.script = script
        this.project = project
    }

    String getServer() {
        script.sh(
            script: "oc whoami --show-server",
            label: "Get OpenShift server URL",
            returnStdout: true
        ).trim()
    }

    void startRollout(String name) {
        script.sh(
            script: "oc -n ${project} rollout latest dc/${name}",
            label: "Rollout latest version of dc/${name}"
        )
    }

    void watchRollout(String name) {
        script.sh(
            script: "oc -n ${project} rollout status dc/${name} --watch=true",
            label: "Watch rollout of latest version of dc/${name}"
        )
    }

    String getLatestVersion(String name) {
        script.sh(
            script: "oc -n ${project} get dc/${name} -o jsonpath='{.status.latestVersion}'",
            label: "Get latest version of dc/${name}",
            returnStdout: true
        ).trim()
    }

    String getRolloutStatus(String name) {
        def jsonPath = "{.metadata.annotations.openshift\\.io/deployment\\.phase}"
        script.sh(
            script: "oc -n ${project} get rc/${name} -o jsonpath='${jsonPath}'",
            label: "Get status of ReplicationController ${name}",
            returnStdout: true
        ).trim().toLowerCase()
    }

    void setImageTag(String name, String sourceTag, String destinationTag) {
        script.sh(
            script: "oc -n ${project} tag ${name}:${sourceTag} ${name}:${destinationTag}",
            label: "Set tag ${destinationTag} on is/${name}"
        )
    }

    @SuppressWarnings('LineLength')
    boolean automaticImageChangeTriggerEnabled(String name) {
        def tmpl = '{{range .spec.triggers}}{{if eq .type "ImageChange" }}{{.imageChangeParams.automatic}}{{end}}{{end}}'
        try {
            def automaticValue = script.sh(
                script: """oc -n ${project} get dc/${name} -o template --template '${tmpl}'""",
                returnStdout: true,
                label: "Check ImageChange trigger for dc/${name}"
            ).trim()
            automaticValue.contains("true")
        } catch (Exception ex) {
            return false
        }
    }

    boolean resourceExists(String kind, String name) {
        script.sh(
            script: "oc -n ${project} get ${kind}/${name} &> /dev/null",
            returnStatus: true,
            label: "Check existance of ${kind} ${name}"
        ) == 0
    }

    String startAndFollowBuild(String name, String dir) {
        script.sh(
            script: "oc -n ${project} start-build ${name} --from-dir ${dir} --follow",
            label: "Start and follow OpenShift build ${name}",
            returnStdout: true
        ).trim()
    }

    String getLastBuildVersion(String name) {
        script.sh(
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
            if (buildStatus == "complete") {
                return buildStatus
            }
            // Wait 5 seconds before asking again. Sometimes the build finishes but the
            // status is not set to "complete" immediately ...
            script.sleep(5)
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
        def imageLabelsOp = "replace"
        def imageLabelsValue = script.sh(
            script: "oc -n ${project} get bc/${name} -o jsonpath='{.spec.output.imageLabels}'",
            returnStdout: true,
            label: "Test existance of path .spec.output.imageLabels"
        ).trim()
        if (imageLabelsValue.length() == 0) {
            imageLabelsOp = "add"
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

        script.sh(
            script: """oc -n ${project} patch bc ${name} --type=json --patch '[${patches.join(",")}]'""",
            label: "Patch BuildConfig ${name}"
        )
    }

    String getImageReference(String name, String tag) {
        script.sh(
            returnStdout: true,
            script: "oc get -n ${project} istag ${name}:${tag} -o jsonpath='{.image.dockerImageReference}'",
            label: "Get image reference of ${name}:${tag}"
        ).trim()
    }

    boolean tooManyEnvironments(String projectId, Integer limit) {
        script.sh(
            returnStdout: true,
            script: "oc projects | grep '^\\s*${projectId}-' | wc -l",
            label: "check ocp environment maximum"
        ).trim().toInteger() >= limit
    }

    private String checkForBuildStatus(String buildId) {
        script.sh(
            returnStdout: true,
            script: "oc -n ${project} get build ${buildId} -o jsonpath='{.status.phase}'",
            label: "Get phase of build ${buildId}"
        ).trim().toLowerCase()
    }
    
    public String getContainerForImage (String projectId, String rc, String image) {
        def jsonPath = """{.spec.template.spec.containers[?(contains .image "${image}")].name}"""
        script.sh(
            script: "oc -n ${projectId} get rc ${rc} -o jsonpath='${jsonPath}'",
            returnStdout: true,
            label: "Getting containers for ${rc} and image ${image}"
        )
    }
    
    // Gets pod of deployment
    Map getPodDataForDeployment(String rc) {
        def index = 5
        while (index > 0) {
            def podStatus = script.sh(
                script: "oc -n ${project} get pod -l deployment=${rc} -o jsonpath='{.items[*].status.phase}'",
                returnStdout: true,
                label: "Getting OpenShift pod data for deployment ${rc}"
            )
            if (podStatus && podStatus == "Running") {
                break
            } else {
                script.sleep(5000)
                index--
            }
        }
          
        def stdout = script.sh(
            script: "oc -n ${project} get pod -l deployment=${rc} -o json",
            returnStdout: true,
            label: "Getting OpenShift pod data for deployment ${rc}"
        ).trim()

        extractPodData(stdout, "deployment '${rc}'")
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
        pod.podName = podOCData?.metadata?.name?: "N/A"
        pod.podNamespace = podOCData?.metadata?.namespace?: "N/A"
        pod.podMetaDataCreationTimestamp = podOCData?.metadata?.creationTimestamp?: "N/A"
        pod.deploymentId = podOCData?.metadata?.annotations['openshift.io/deployment.name']?: "N/A"
        pod.podNode = podOCData?.spec?.nodeName ?: "N/A"
        pod.podIp = podOCData?.status?.podIP ?: "N/A"
        pod.podStatus = podOCData?.status?.phase ?: "N/A"
        pod.podStartupTimeStamp = podOCData?.status?.startTime ?: "N/A"
        pod["containers"] = [:]
        
        podOCData?.spec?.containers?.each { container ->
            pod.containers[container.name] = container.image
        }
        return pod
    }

    String getOpenshiftApplicationDomain () {
        def routeName = "test-route-" + System.currentTimeMillis()
        script.sh (
            script: "oc -n ${project} create route edge ${routeName} --service=dummy --port=80 | true",
            label: "create dummy route for extraction (${routeName})"
        )
        def routeUrl = script.sh (
            script: "oc -n ${project} get route ${routeName} -o jsonpath='{.spec.host}'",
            returnStdout: true,
            label: "get cluster route domain"
        )
        def routePrefixLength = "${routeName}-${project}".length() + 1
        String openShiftPublicHost = routeUrl.substring(routePrefixLength)
        script.sh (
            script: "oc -n ${project} delete route ${routeName} | true",
            label: "delete dummy route for extraction (${routeName})"
        )
        
        return openShiftPublicHost
    }

    Map<String, String> getImageInformationFromImageUrl (String url) {
        script.echo ("Deciphering imageURL ${url} into pieces")
        def imageInformation = [:]
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
    
    List<Map<String, String>> getImageStreamsForDeploymentConfig (String dc) {
        String imageString = script.sh (
            script: "oc -n ${project} get dc ${dc} -o jsonpath='{.spec.template.spec.containers[*].image}'",
            label: "Get container images for deploymentconfigs (${dc})",
            returnStdout: true
        )
        List images = []
        imageString.tokenize(" ").each { image ->
            images << getImageInformationFromImageUrl(image)
        }
        return images
    }
}
