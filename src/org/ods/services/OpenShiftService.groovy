package org.ods.services

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.PodData

import java.security.SecureRandom

@SuppressWarnings(['ClassSize', 'MethodCount'])
@TypeChecked
class OpenShiftService {

    static final String EXPORTED_TEMPLATE_FILE = 'template.yml'
    static final String ROLLOUT_COMPLETE = 'complete'
    static final String ROLLOUT_WAITING = 'waiting'
    static final String DEPLOYMENTCONFIG_KIND = 'DeploymentConfig'
    static final String DEPLOYMENT_KIND = 'Deployment'

    private final IPipelineSteps steps
    private final ILogger logger

    OpenShiftService(IPipelineSteps steps, ILogger logger) {
        this.steps = steps
        this.logger = logger
    }

    static void createProject(IPipelineSteps steps, String name) {
        steps.sh(
            script: "oc new-project ${name}",
            label: "Create new OpenShift project ${name}"
        )
    }

    static void loginToExternalCluster(IPipelineSteps steps, String apiUrl, String apiToken) {
        steps.sh(
            script: "oc login ${apiUrl} --token=${apiToken} >& /dev/null",
            label: "login to external cluster (${apiUrl})"
        )
    }

    static String getApiUrl(IPipelineSteps steps) {
        steps.sh(
            script: 'oc whoami --show-server',
            label: 'Get OpenShift API server URL',
            returnStdout: true
        ).toString().trim()
    }

    static boolean tooManyEnvironments(IPipelineSteps steps, String projectId, Integer limit) {
        steps.sh(
            returnStdout: true,
            script: "oc projects | grep '^\\s*${projectId}-' | wc -l",
            label: "check ocp environment maximum for '${projectId}-*'"
        ).toString().trim().toInteger() >= limit
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
        if (!envExists(steps, project)) {
            String currentClusterUrl = getApiUrl(steps)
            throw new IOException ("OCP project ${project} on server ${currentClusterUrl}" +
                ' does not exist - cannot create route to retrieve host / domain name!' +
                ' If this is needed, please create project on cluster!')
        }
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
        ).toString().trim()
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
        ).toString().trim()
    }

    boolean imageExists(String project, String name, String tag) {
        steps.sh(
            returnStatus: true,
            script: "oc -n ${project} get istag ${name}:${tag} &> /dev/null",
            label: "Check existance of image ${name}:${tag}"
        ) == 0
    }

    boolean envExists(String project) {
        envExists(steps, project)
    }

    String getApiUrl() {
        getApiUrl(steps)
    }

    // helmUpgrade installs given "release" into "project" from the chart
    // located in the working directory. If "withDiff" is true, a diff is
    // performed beforehand.
    @SuppressWarnings(['ParameterCount', 'LineLength'])
    void helmUpgrade(
        String project,
        String release,
        List<String> valuesFiles,
        Map<String, String> values,
        List<String> defaultFlags,
        List<String> additionalFlags,
        boolean withDiff) {
        def upgradeFlags = defaultFlags.collect { it }
        additionalFlags.collect { upgradeFlags << it }
        valuesFiles.collect { upgradeFlags << "-f ${it}".toString() }
        values.collect { k, v -> upgradeFlags << "--set ${k}=${v}".toString() }
        if (withDiff) {
            def diffFlags = upgradeFlags.findAll { it  }
            diffFlags << '--no-color'
            diffFlags << '--three-way-merge'
            diffFlags << '--normalize-manifests'
            // diffFlags << '--detailed-exitcode'
            steps.sh(
                script: "HELM_DIFF_IGNORE_UNKNOWN_FLAGS=true helm -n ${project} secrets diff upgrade ${diffFlags.join(' ')} ${release} ./",
                label: "Show diff explaining what helm upgrade would change for release ${release} in ${project}"
            )
        }
        def failed = steps.sh(
            script: "helm -n ${project} secrets upgrade ${upgradeFlags.join(' ')} ${release} ./",
            label: "Upgrade Helm release ${release} in ${project}",
            returnStatus: true
        )
        if (failed) {
            throw new RuntimeException(
                'Rollout Failed!. ' +
                    "Helm could not install the ${release} in ${project}"
            )
        }
    }

    @SuppressWarnings(['LineLength', 'ParameterCount'])
    void tailorApply(String project, Map<String, String> target, String paramFile, List<String> params, List<String> preserve, String tailorPrivateKeyFile, boolean verify) {
        def verifyFlag = verify ? '--verify' : ''
        def tailorPrivateKeyFlag = tailorPrivateKeyFile ? "--private-key ${tailorPrivateKeyFile}" : ''
        def selectorFlag = target.selector ? "--selector ${target.selector}" : ''
        def excludeFlag = target.exclude ? "--exclude ${target.exclude}" : ''
        def includeArg = target.include ?: ''
        def paramFileFlag = paramFile ? "--param-file ${paramFile}" : ''
        params << "ODS_OPENSHIFT_APP_DOMAIN=${getApplicationDomain(project)}".toString()
        def paramFlags = params.collect { "--param ${it}" }.join(' ')
        def preserveFlags = preserve.collect { "--preserve ${it}" }.join(' ')
        doTailorApply(project, "${selectorFlag} ${excludeFlag} ${paramFlags} ${preserveFlags} ${paramFileFlag} ${tailorPrivateKeyFlag} ${verifyFlag} --ignore-unknown-parameters ${includeArg}")
    }

    void tailorExport(String project, String selector, Map<String, String> envParams, String targetFile) {
        doTailorExport(project, "-l ${selector}", envParams, targetFile)
    }

    String rollout(String project, String kind, String name, int priorRevision, int timeoutMinutes) {
        def revision = getRevision(project, kind, name)
        if (revision > priorRevision) {
            logger.info "Rollout of deployment for '${name}' has been triggered automatically."
        } else {
            if (kind == OpenShiftService.DEPLOYMENTCONFIG_KIND) {
                startRollout(project, name, revision)
            } else {
                restartRollout(project, name, revision)
            }
        }
        watchRollout(project, kind, name, timeoutMinutes)
    }

    // watchRollout watches the rollout of either a DeploymentConfig or a Deployment resource.
    // It returns the name of the respective "pod manager", which is either a ReplicationController
    // (in the case of kind=DeploymentConfig) or a ReplicaSet (for kind=Deployment).
    String watchRollout(String project, String kind, String name, int timeoutMinutes) {
        def podManagerKind = getPodManagerKind(kind)
        try {
            steps.timeout(time: timeoutMinutes) {
                logger.startClocked("${name}-watch-rollout".toString())
                doWatchRollout(project, kind, name)
                logger.debugClocked("${name}-watch-rollout".toString(), (null as String))
            }
        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
            def revision = getRevision(project, kind, name)
            def podManagerName = getPodManagerName(project, kind, name, revision)
            throw new RuntimeException(
                'Rollout timed out. ' +
                "Observed related event messages:\n${getRolloutEventMessages(project, podManagerKind, podManagerName)}"
            )
        }

        def revision = getRevision(project, kind, name)
        def podManagerName = getPodManagerName(project, kind, name, revision)
        def rolloutStatus = getRolloutStatus(project, kind, podManagerName)
        if (rolloutStatus != ROLLOUT_COMPLETE) {
            throw new RuntimeException(
                "Rollout #${revision} failed with status '${rolloutStatus}'. " +
                "Observed related event messages:\n${getRolloutEventMessages(project, podManagerKind, podManagerName)}"
            )
        } else {
            logger.info "Rollout #${revision} of ${kind} '${name}' successful."
        }
        podManagerName
    }

    int getRevision(String project, String kind, String name) {
        String revision
        if (kind == DEPLOYMENTCONFIG_KIND) {
            revision = steps.sh(
                script: "oc -n ${project} get ${kind}/${name} -o jsonpath='{.status.latestVersion}'",
                label: "Get latest version of ${kind}/${name}",
                returnStdout: true
            ).toString().trim()
        } else {
            def jsonPath = '{.metadata.annotations.deployment\\.kubernetes\\.io/revision}'
            revision = steps.sh(
                script: "oc -n ${project} get ${kind}/${name} -o jsonpath='${jsonPath}'",
                label: "Get revision of ${kind}/${name}",
                returnStdout: true
            ).toString().trim()
        }
        if (!revision.isInteger()) {
            throw new RuntimeException(
                "ERROR: Latest version of ${kind} '${name}' is not a number: '${revision}"
            )
        }
        revision as int
    }

    // getRolloutStatus queries the state of the rollout for given kind/name.
    // It returns either "complete" or some other (temporary) state, e.g. "waiting".
    @TypeChecked(TypeCheckingMode.SKIP)
    @SuppressWarnings('LineLength')
    String getRolloutStatus(String project, String kind, String name) {
        if (kind == DEPLOYMENTCONFIG_KIND) {
            def jsonPath = '{.metadata.annotations.openshift\\.io/deployment\\.phase}'
            return getJSONPath(project, 'rc', name, jsonPath).toLowerCase()
        }
        // Logic for Deployment resources is taken from:
        // https://github.com/kubernetes/kubernetes/blob/2e57e54fa6a251826a14ed365a12c99faa3a93be/staging/src/k8s.io/kubectl/pkg/polymorphichelpers/rollout_status.go#L89.
        // However, we look at the ReplicaSetStatus because that will tell us if
        // the specific rollout we're interested in is successful or not. It
        // could be that some other process has trigger another rollout which
        // cancelled "our" rollout.
        // Documentation of the ReplicaSetStatus is at:
        // https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.19/#replicasetstatus-v1-apps
        def rsJSON = getJSON(project, 'rs', name)
        def status = rsJSON.status
        if (!status.replicas) {
            return ROLLOUT_WAITING // Deployment spec update not observed... (should never happen)
        }
        if (status.fullyLabeledReplicas < status.replicas) {
            return ROLLOUT_WAITING // Not enough pods with matching labels yet
        }
        if (status.availableReplicas < status.replicas) {
            return ROLLOUT_WAITING // Not enough pods available yet
        }
        ROLLOUT_COMPLETE // deployment successfully rolled out
    }

    void setImageTag(String project, String name, String sourceTag, String destinationTag) {
        steps.sh(
            script: "oc -n ${project} tag ${name}:${sourceTag} ${name}:${destinationTag}",
            label: "Set tag ${destinationTag} on is/${name}"
        )
    }

    boolean resourceExists(String project, String kind, String name) {
        steps.sh(
            script: "oc -n ${project} get ${kind}/${name} &> /dev/null",
            returnStatus: true,
            label: "Check existance of ${kind} ${name}"
        ) == 0
    }

    int startBuild(String project, String name, String dir) {
        steps.sh(
            script: "oc -n ${project} start-build ${name} --from-dir ${dir} ${logger.ocDebugFlag}",
            label: "Start Openshift build ${name}",
            returnStdout: true
        ).toString().trim()
        return getLastBuildVersion(project, name)
    }

    String followBuild(String project, String name, int version) {
        steps.sh(
            script: "oc -n ${project} logs -f --version=${version} bc/${name}",
            label: "Logs of Openshift build ${name}",
            returnStdout: true
        ).toString().trim()
    }

    int getLastBuildVersion(String project, String name) {
        def versionNumber = steps.sh(
            returnStdout: true,
            script: "oc -n ${project} get bc/${name} -o jsonpath='{.status.lastVersion}'",
            label: "Get lastVersion of BuildConfig ${name}"
        ).toString().trim()
        if (!versionNumber.isInteger()) {
            throw new RuntimeException(
                "ERROR: Last version of BuildConfig '${name}' is not a number: '${versionNumber}"
            )
        }
        versionNumber as int
    }

    String getBuildStatus(String project, String buildId, int retries) {
        def buildStatus = 'unknown'
        for (def i = 0; i < retries; i++) {
            buildStatus = checkForBuildStatus(project, buildId)
            logger.debug ("Build: '${buildId}' - status: '${buildStatus}'")
            if (buildStatus == 'complete') {
                return buildStatus
            }
            // Wait 12 seconds before asking again. Sometimes the build finishes but the
            // status is not set to "complete" immediately ...
            steps.sleep(12)
        }
        return buildStatus
    }

    @SuppressWarnings('LineLength')
    void patchBuildConfig(String project, String name, String tag, Map buildArgs, Map imageLabels) {
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
        ).toString().trim()
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
            script: """oc -n ${project} patch bc ${name} --type=json --patch '[${patches.join(',')}]' ${logger.ocDebugFlag} """,
            label: "Patch BuildConfig ${name}"
        )
    }

    String getImageReference(String project, String name, String tag) {
        getImageReference(steps, project, name, tag)
    }

    void importImageTagFromProject(
        String project,
        String name,
        String sourceProject,
        String sourceTag,
        String targetTag) {
        importImageFromProject(project, sourceProject, "${name}:${sourceTag}", "${name}:${targetTag}")
    }

    @SuppressWarnings('ParameterCount')
    void importImageTagFromSourceRegistry(
        String project,
        String name,
        String sourceRegistrySecret,
        String sourceProject,
        String sourceTag,
        String targetTag) {
        importImageFromSourceRegistry(
            project, sourceRegistrySecret, sourceProject, "${name}:${sourceTag}", "${name}:${targetTag}"
        )
    }

    void importImageShaFromProject(
        String project,
        String name,
        String sourceProject,
        String imageSha,
        String imageTag) {
        importImageFromProject(project, sourceProject, "${name}@${imageSha}", "${name}:${imageTag}")
    }

    @SuppressWarnings('ParameterCount')
    void importImageShaFromSourceRegistry(
        String project,
        String name,
        String sourceRegistrySecret,
        String sourceProject,
        String imageSha,
        String imageTag) {
        importImageFromSourceRegistry(
            project, sourceRegistrySecret, sourceProject, "${name}@${imageSha}", "${name}:${imageTag}"
        )
    }

    // Returns data about the pods (replicas) of the deployment.
    // If not all pods are running until the retries are exhausted,
    // an exception is thrown.
    List<PodData> getPodDataForDeployment(String project, String kind, String podManagerName, int retries) {
        if (kind in [DEPLOYMENTCONFIG_KIND, DEPLOYMENT_KIND]
            && getDesiredReplicas(project, kind, podManagerName) < 1) {
            return retrieveImageData(project, kind, podManagerName)
        }
        def label = getPodLabelForPodManager(project, kind, podManagerName)
        for (def i = 0; i < retries; i++) {
            def podData = checkForPodData(project, label)
            if (podData) {
                return podData
            }
            steps.echo("Could not find 'running' pod(s) with label '${label}' - waiting")
            steps.sleep(12)
        }
        throw new RuntimeException("Could not find 'running' pod(s) with label '${label}'")
    }

    // getResourcesForComponent returns a map in which each kind is mapped to a list of resources.
    Map<String, List<String>> getResourcesForComponent(String project, List<String> kinds, String selector) {
        def items = steps.sh(
            script: """oc -n ${project} get ${kinds.join(',')} \
                -l ${selector} \
                -o template='{{range .items}}{{.kind}}:{{.metadata.name}} {{end}}'""",
            returnStdout: true,
            label: "Getting all ${kinds.join(',')} names for selector '${selector}'"
        ).toString().trim().tokenize(' ')
        Map<String, List<String>> m = [:]
        items.each {
            def parts = it.split(':')
            if (!m.containsKey(parts[0])) {
                m[parts[0]] = []
            }
            m[parts[0]] << parts[1]
        }
        m
    }

    String getApplicationDomain(String project) {
        getApplicationDomainOfProject(steps, project)
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
        def shaUrl = getImageReference(urlParts[-2], tagParts[0], tagParts[1])
        imageInfoWithSha(shaUrl.split('/').toList())
    }

    List<Map<String, String>> getImagesOfDeployment(String project, String kind, String name) {
        steps.sh(
            script: "oc -n ${project} get ${kind} ${name} -o jsonpath='{.spec.template.spec.containers[*].image}'",
            label: "Get container images for ${kind} ${name}",
            returnStdout: true
        ).toString().trim().tokenize(' ').collect { imageInfoForImageUrl(it) }
    }

    String getOriginUrlFromBuildConfig(String project, String bcName) {
        return steps.sh(
            script: "oc -n ${project} get bc/${bcName} -o jsonpath='{.spec.source.git.uri}'",
            returnStdout: true,
            label: "Get origin from BuildConfig ${bcName}"
        ).toString().trim()
    }

    Map getPodDataForDeployments(String project, String kind, List<String> deploymentNames) {
        Map pods = [:]
        deploymentNames.each { name ->
            Map pod = [:]
            logger.debug("Verifying images of ${kind} '${name}'")
            int revision = 0
            try {
                revision = getRevision(project, kind, name)
            } catch (err) {
                logger.debug("${kind} '${name}' does not exist!")
            }
            if (revision < 1) {
                logger.debug("No revision of ${kind} '${name}' found")
            } else {
                def podManagerName = getPodManagerName(project, kind, name, revision)
                def podData = getPodDataForDeployment(
                    project, kind, podManagerName, 5
                )
                // TODO: Once the orchestration pipeline can deal with multiple replicas,
                // update this to return multiple pods.
                pod = podData[0].toMap()
            }
            pods[name] = pod
        }
        pods
    }

    boolean areImageShasUpToDate(Map defData, Map podData) {
        defData.containers.every { String containerName, String definedImage ->
            verifyImageSha(containerName, definedImage, podData.containers[containerName].toString())
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

    // findOrCreateBuildConfig searches for a BuildConfig with "name" in "project",
    // and if none is found, it creates one.
    void findOrCreateBuildConfig(String project, String name, Map<String, String> labels = [:], String tag = "latest") {
        if (!resourceExists(project, 'BuildConfig', name)) {
            createBuildConfig(project, name, labels, tag)
        }
    }

    // findOrCreateImageStream searches for a ImageStream with "name" in "project",
    // and if none is found, it creates one.
    void findOrCreateImageStream(String project, String name, Map<String, String> labels = [:]) {
        if (!resourceExists(project, 'ImageStream', name)) {
            createImageStream(project, name, labels)
        }
    }

    /**
     * Apply labels provided in <code>labels</code> to resources given in <code>resources</code> (format kind/name).
     * The selection can be restricted to a project and also with a label selector given in <code>selector</code>.
     * You can apply labels to all object kinds with <code>resources=all</code> and using a selector.
     * If the label already exists, it will be overwritten.
     * If the value for one label is the empty string, the label will be set with an empty string as value.
     * If the value for one label is null, the label will be deleted from the selected resources.
     *
     * Allowed selector conditions are:
     * <code>label=value</code>
     * <code>label==value</code>
     * <code>label!=value</code>
     * More than one condition can be specified, separated by commas.
     *
     * @param project the project to apply the operation to. The current project, if null.
     * @param resources the resources to apply the labels to, with format <code>kind[/name]</code>.
     * @param labels a <code>Map</code> containing label keys and values.
     * @param selector a comma-separated list of label conditions.
     * @return the output of the shell script running the OpenShift client command.
     * @throws IllegalArgumentException if no <code>resources</code> or no <code>labels</code> are provided.
     */
    String labelResources(String project, String resources, Map<String, String> labels, String selector = null) {
        if (!resources) {
            throw new IllegalArgumentException('You must specify the resources to label')
        }
        if (!labels) {
            throw new IllegalArgumentException('At least one label update is required')
        }
        def labelStr = labels.collect { key, value ->
            def label = key
            label += value == null ? '-' : "='${value}'"
            return label
        }.join(' ')
        if (logger.getDebugMode()) {
            logger.debug("Setting labels ${labelStr} to resources ${resources} selected by ${selector}")
        }
        def script = "oc label --overwrite ${resources} "
        if (selector) {
            script += "-l ${selector} "
        }
        if (project) {
            script += "-n ${project} "
        }
        script += labelStr
        def scriptLabel = "Set labels to ${resources}"
        if (selector) {
            scriptLabel += " selected by the following labels: ${selector}"
        }
        steps.sh(
            script: script,
            label: scriptLabel,
            returnStdout: true
        ).toString().trim()
    }

    /**
     * Mark the provided resource as paused.
     * Generally used to pause rollouts for a <code>Deployment</code> or <code>DeploymentConfig</code>.
     * No deployments will be triggered until rollouts are resumed.
     * The resource can be specified with the syntax <code>type/resource</code> or <code>type resource</code>.
     * For example, <code>dc/aDeploymentConfig</code> or <code>deployment aDeployment</code>.
     *
     * @param resource the resource to be paused.
     * @param project the namespace of the resource. Default: <code>null</code> (the current project).
     * @return the output of the shell script running the OpenShift client command.
     * @throws IllegalArgumentException if no <code>resource</code> is provided.
     */
    String pause(String resource, String project = null) {
        if (!resource) {
            throw new IllegalArgumentException('You must specify the resource to pause')
        }
        if (logger.getDebugMode()) {
            logger.debug("Pausing ${resource}")
        }
        def script = "oc patch -p '{\"spec\":{\"paused\":true}}' ${resource}"
        if (project) {
            script += " -n ${project} "
        }
        def scriptLabel = "Pause ${resource}"
        steps.sh(
            script: script,
            label: scriptLabel,
            returnStdout: true
        ).toString().trim()
    }

    /**
     * Apply the <code>pause</code> method to each resource provided.
     *
     * @param project the namespace where the resources exist.
     * @param resources a <code>Map</code> with the resource names grouped by resource kind.
     * @return a <code>List</code> or strings with the output of the <code>pause</code> method for each resource.
     */
    List<String> bulkPause(String project, Map<String, List<String>> resources) {
        return bulkApply(project, resources, this.&pause)
    }

    /**
     * Apply the <code>pause</code> method to each resource of the given kinds and selected by the given selector.
     *
     * @param project the namespace where to locate the resources.
     * @param kinds the kinds of resources we want to select.
     * @param selector a label selector to select the resources.
     * @return a <code>List</code> or strings with the output of the <code>pause</code> method for each resource found.
     */
    List<String> bulkPause(String project, List<String> kinds, String selector) {
        return bulkApply(project, kinds, selector, this.&pause)
    }

    /**
     * Resume a paused resource.
     * Generally used to resume rollouts for a <code>Deployment</code> or <code>DeploymentConfig</code>:
     * A rollout will be immediately triggered, if the resource has changed while paused.
     * Note that, if the state of the resource when resuming is exactly the same as the last time it was paused,
     * no rollout will be triggered, no matter the changes it may have suffered while in paused state.
     * The resource can be specified with the syntax <code>type/resource</code> or <code>type resource</code>.
     * For example, <code>dc/aDeploymentConfig</code> or <code>deployment aDeployment</code>.
     *
     * @param resource the resource to be resumed.
     * @param project the namespace of the resource. Default: <code>null</code> (the current project).
     * @return the output of the shell script running the OpenShift client command.
     * @throws IllegalArgumentException if no <code>resource</code> is provided.
     */
    String resume(String resource, String project = null) {
        if (!resource) {
            throw new IllegalArgumentException('You must specify the resource to resume')
        }
        if (logger.getDebugMode()) {
            logger.debug("Resuming ${resource}")
        }
        def script = "oc patch -p '{\"spec\":{\"paused\":false}}' ${resource}"
        if (project) {
            script += " -n ${project} "
        }
        def scriptLabel = "Resume ${resource}"
        steps.sh(
            script: script,
            label: scriptLabel,
            returnStdout: true
        ).toString().trim()
    }

    /**
     * Apply the <code>resume</code> method to each resource provided.
     *
     * @param project the namespace where the resources exist.
     * @param resources a <code>Map</code> with the resource names grouped by resource kind.
     * @return a <code>List</code> or strings with the output of the <code>resume</code> method for each resource.
     */
    List<String> bulkResume(String project, Map<String, List<String>> resources) {
        return bulkApply(project, resources, this.&resume)
    }

    /**
     * Apply the <code>resume</code> method to each resource of the given kinds and selected by the given selector.
     *
     * @param project the namespace where to locate the resources.
     * @param kinds the kinds of resources we want to select.
     * @param selector a label selector to select the resources.
     * @return a <code>List</code> or strings with the output of the <code>resume</code> method for each resource found.
     */
    List<String> bulkResume(String project, List<String> kinds, String selector) {
        return bulkApply(project, kinds, selector, this.&resume)
    }

    /**
     * Applies a patch to the given resource.
     * If the path is specified, it must be the absolute path of some member belonging to the resource definition
     * to which the patch is to be applied.
     * If any suffix of the path does not exist, all the nested members in the path will be created.
     * If the patch is null, the member specified by the path will be removed.
     * (Note: This method cannot be used to delete a resource.
     * Specifying both a null patch and a null path will raise an exception.)
     * Otherwise, the patch will be performed by applying the following rules:
     * Any members of the resource not present in the patch will be left untouched.
     * Any member in the patch with non-null value and which does not appear in the resource will be added.
     * For any members in the patch that also exist in the resource:
     * If the value in the patch is <code>null</code>, the member in the resource will be removed.
     * If the value in the patch is not a <code>Map</code>, it will replace the value in the resource.
     * If the value in the patch is a <code>Map</code>, it will patch the value in the resource in a recursive fashion.
     *
     * Note that the <code>path</code> parameter is just a convenience.
     * The same functionality can be obtained by nesting maps in the <code>patch</code>.
     *
     * @param resource the resource to patch, with syntax type/resource or type resource.
     * @param patch a <code>Map</code> specifying the patch to apply.
     * @param path the optional absolute path at which to apply the patch.
     * @param project the namespace of the resource. Default: null (the current project).
     * @return
     */
    String patch(String resource, Map<String, ?> patch, String path = null, String project = null) {
        if (!resource) {
            throw new IllegalArgumentException('You must specify the resource to path')
        }
        if (path != null && !path.startsWith('/')) {
            throw new IllegalArgumentException("The path must start with a slash. path == '${path}'")
        }
        if (patch == null && path == null) {
            throw new IllegalArgumentException('You must specify either a patch or a path')
        }
        def fullPatch = patch
        if (path) {
            path.substring(1).split('/').reverseEach { member ->
                fullPatch = [(member): fullPatch]
            }
        }
        def jsonPatch = JsonOutput.toJson(fullPatch)
        if (logger.getDebugMode()) {
            def namespace = project ?: 'current'
            logger.debug("Patching ${resource} in the ${namespace} project with ${jsonPatch}")
        }
        def script = "oc patch ${resource} --type='merge' -p '${jsonPatch}'"
        if (project) {
            script += " -n ${project}"
        }
        def scriptLabel = "Patch ${resource}"
        steps.sh(
            script: script,
            label: scriptLabel,
            returnStdout: true
        ).toString().trim()
    }

    /**
     * Apply the <code>patch</code> method to each resource provided.
     *
     * @param project the namespace where the resources exist.
     * @param resources a <code>Map</code> with the resource names grouped by resource kind.
     * @param patch the patch to apply.
     * @param path the optional absolute path at which to apply the patch.
     * @return a <code>List</code> or strings with the output of the <code>patch</code> method for each resource.
     */
    List<String> bulkPatch (
        String project,
        Map<String, List<String>> resources,
        Map<String, ?> patch,
        String path = null
    ) {
        def results = []
        resources.each { kind, names ->
            names.each { name ->
                results << this.patch("${kind}/${name}", patch, path, project)
            }
        }
        return results
    }

    /**
     * Apply the <code>patch</code> method to each resource of the given kinds and selected by the given selector.
     *
     * @param project the namespace where to locate the resources.
     * @param kinds the kinds of resources we want to select.
     * @param selector a label selector to select the resources.
     * @param patch the patch to apply.
     * @param path the optional absolute path at which to apply the patch.
     * @return a <code>List</code> or strings with the output of the <code>patch</code> method for each resource found.
     */
    List<String> bulkPatch (
        String project,
        List<String> kinds,
        String selector,
        Map<String, ?> patch,
        String path = null
    ) {
        def resources = getResourcesForComponent(project, kinds, selector)
        return bulkPatch(project, resources, patch, path)
    }

    /**
     * Apply the given closure to each resource provided.
     *
     * @param project the namespace where the resources exist.
     * @param resources a <code>Map</code> with the resource names grouped by resource kind.
     * @return a <code>List</code> or strings with the output of the given closure for each resource.
     */
    //TODO Adapt it to admit arbitrary additional parameters to the closure
    private List<String> bulkApply(String project, Map<String, List<String>> resources, Closure body) {
        def results = []
        resources.each { kind, names ->
            names.each { name ->
                results << body("${kind}/${name}", project)
            }
        }
        return results
    }

    /**
     * Apply the given closure to each resource of the given kinds and selected by the given selector.
     *
     * @param project the namespace where to locate the resources.
     * @param kinds the kinds of resources we want to select.
     * @param selector a label selector to select the resources.
     * @return a <code>List</code> or strings with the output of the given closure for each resource found.
     */
    //TODO Adapt it to admit arbitrary additional parameters to the closure
    private List<String> bulkApply(String project, List<String> kinds, String selector, Closure body) {
        def resources = getResourcesForComponent(project, kinds, selector)
        return bulkApply(project, resources, body)
    }

    // getConfigMapData returns the data content of given ConfigMap.
    Map getConfigMapData(String project, String name) {
        getJSON(project, "ConfigMap", name).data as Map
    }

    private void createBuildConfig(String project, String name, Map<String, String> labels, String tag) {
        logger.info "Creating BuildConfig ${name} in ${project} ... "
        def bcYml = buildConfigBinaryYml(name, labels, tag)
        createResource(project, bcYml)
    }

    private void createImageStream(String project, String name, Map<String, String> labels) {
        logger.info "Creating ImageStream ${name} in ${project} ... "
        def isYml = imageStreamYml(name, labels)
        createResource(project, isYml)
    }

    @NonCPS
    private String buildConfigBinaryYml(String name, Map<String,String> labels, String tag) {
        """\
          apiVersion: v1
          kind: BuildConfig
          metadata:
            labels: {${labels.collect { k, v -> "${k}: '${v}'" }.join(', ')}}
            name: ${name}
          spec:
            failedBuildsHistoryLimit: 5
            nodeSelector: null
            output:
              to:
                kind: ImageStreamTag
                name: ${name}:${tag}
            postCommit: {}
            resources:
              limits:
                cpu: '1'
                memory: '2Gi'
              requests:
                cpu: '200m'
                memory: '1Gi'
            runPolicy: Serial
            source:
              type: Binary
              binary: {}
            strategy:
              type: Docker
              dockerStrategy: {}
            successfulBuildsHistoryLimit: 5
        """.stripIndent()
    }

    @NonCPS
    private String imageStreamYml(String name, Map<String,String> labels) {
        """\
          apiVersion: v1
          kind: ImageStream
          metadata:
            labels: {${labels.collect { k, v -> "${k}: '${v}'" }.join(', ')}}
            name: ${name}
          spec:
            lookupPolicy:
              local: false
        """.stripIndent()
    }

    private String createResource(String project, String yml) {
        def filename = ".${UUID.randomUUID().toString()}.yml"
        steps.writeFile(file: filename, text: yml)
        steps.sh """
            oc -n ${project} create -f ${filename};
            rm ${filename}
        """
    }

    private String getJSONPath(String project, String kind, String name, String jsonPath) {
        steps.sh(
            script: "oc -n ${project} get ${kind}/${name} -o jsonpath='${jsonPath}'",
            label: "Get ${jsonPath} of ${kind} ${name}",
            returnStdout: true
        ).toString().trim()
    }

    private Map getJSON(String project, String kind, String name) {
        def stdout = steps.sh(
            script: "oc -n ${project} get ${kind}/${name} -o json",
            returnStdout: true,
            label: "Get JSON of ${kind}/${name}"
        ).toString().trim()
        def json = new JsonSlurperClassic().parseText(stdout)
        json as Map
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    // checkForPodData returns a subset of information from every pod, once
    // all pods matching the label are "running". If this is not the case,
    // it returns an empty list.
    List<PodData> checkForPodData(String project, String label) {
        List<PodData> pods = []
        def stdout = steps.sh(
            script: "oc -n ${project} get pod -l ${label} -o json",
            returnStdout: true,
            label: "Getting OpenShift pod data for pods labelled with ${label}"
        ).toString().trim()
        def podJson = new JsonSlurperClassic().parseText(stdout)
        if (podJson && podJson.items.collect { it.status?.phase?.toLowerCase() }.every { it == 'running' }) {
            pods = extractPodData(podJson)
        }
        pods
    }

    private String getPodManagerName(String project, String kind, String name, int revision) {
        if (kind == DEPLOYMENTCONFIG_KIND) {
            "${name}-${revision}"
        } else {
            getReplicaSetName(project, name, revision)
        }
    }

    private String getPodLabelForPodManager(String project, String kind, String podManagerName) {
        if (kind == DEPLOYMENTCONFIG_KIND) {
            return "deployment=${podManagerName}"
        }
        "pod-template-hash=${getReplicaSetPodTemplateHash(project, podManagerName)}"
    }

    // TODO: Is it reliable to use the last part of the podManagerName as the hash?
    // E.g. ReplicaSet "foo-5b78f6b48b" typically has label "pod-template-hash=5b78f6b48b".
    private String getReplicaSetPodTemplateHash(String project, String replicaSetName) {
        steps.sh(
            script: "oc -n ${project} get rs ${replicaSetName} -ojsonpath='{.metadata.labels.pod-template-hash}'",
            label: "Get pod-template-hash label of ${replicaSetName}",
            returnStdout: true
        ).toString().trim()
    }

    private String getResourceUID(String project, String kind, String name) {
        steps.sh(
            script: "oc -n ${project} get ${kind}/${name} -ojsonpath='{.metadata.uid}'",
            label: "Get UID of ${kind}/${name}",
            returnStdout: true
        ).toString().trim()
    }

    // getReplicaSetName returns the name of the ReplicaSet which has the given
    // deployment as its owner, and has been created for the given revision.
    // TODO: Newer versions of Go should allow {{break}} in the template to
    // avoid potentially getting an array, from which we take the first entry now.
    @SuppressWarnings('LineLength')
    private String getReplicaSetName(String project, String deploymentName, int revision) {
        def uid = getResourceUID(project, DEPLOYMENT_KIND, deploymentName)
        steps.sh(
            script: """oc -n ${project} get rs -o go-template --template='{{range .items}}{{if eq (index .metadata.annotations "deployment.kubernetes.io/revision") "${revision}"}}{{\$name := .metadata.name}}{{range .metadata.ownerReferences}}{{if eq .uid "${uid}"}}{{\$name}} {{end}}{{end}}{{end}}{{end}}'""",
            label: "Get current ReplicaSet of ${deploymentName}",
            returnStdout: true
        ).toString().trim().tokenize(' ')[0]
    }

    // Pods of DeploymentConfig resources are managed by ReplicationController resources.
    // Pods of Deployment resources are managed by ReplicaSet resources.
    private String getPodManagerKind(String deploymentKind) {
        if (deploymentKind == DEPLOYMENTCONFIG_KIND) {
            return "ReplicationController"
        }
        "ReplicaSet"
    }

    private String getEventMessages(String project, String kind, String name) {
        steps.sh(
            script: """
                oc -n ${project} get events \
                    --field-selector involvedObject.kind=${kind},involvedObject.name=${name} \
                    -o custom-columns=MESSAGE:.message --no-headers
            """,
            returnStdout: true,
            label: "Get event messages for ${kind} ${name}"
        ).toString().trim()
    }

    private List<String> getPodsWithLabel(String project, String label) {
        steps.sh(
            script: """
                oc -n ${project} get pods \
                    -l ${label} \
                    -ojsonpath='{.items[*].metadata.name}'
            """,
            returnStdout: true,
            label: "Get pods with label '${label}'"
        ).toString().trim().tokenize(' ')
    }

    private String getRolloutEventMessages(String project, String kind, String podManagerName) {
        String managerEventMessages
        try {
            managerEventMessages = getEventMessages(project, kind, podManagerName)
        } catch (ex) {
            logger.debug("Error when retrieving events for ${kind} ${podManagerName}:\n${ex}")
        }
        if (!managerEventMessages) {
            return "Could not find any events for ${kind} ${podManagerName}."
        }
        managerEventMessages = "=== Events from ${kind} '${podManagerName}' ===\n${managerEventMessages}"
        String pod
        try {
            def label = getPodLabelForPodManager(project, kind, podManagerName)
            pod = getPodsWithLabel(project, label)[0]
        } catch (ex) {
            logger.debug("Error when retrieving pod for ${kind} ${podManagerName}:\n${ex}")
        }
        if (!pod) {
            return "${managerEventMessages}\nCould not find any pod for ${kind} ${podManagerName}."
        }
        String podEventMessages
        try {
            podEventMessages = getEventMessages(project, "Pod", pod)
        } catch (ex) {
            logger.debug("Error when retrieving events for pod ${pod}:\n${ex}")
        }
        if (!podEventMessages) {
            return "${managerEventMessages}\nCould not find any events for pod for ${pod}."
        }
        "${managerEventMessages}\n=== Events from Pod '${pod}' ===\n${podEventMessages}"
    }

    // startRollout triggers a rollout of a DeploymentConfig resource.
    // Deployment resources cannot be targeted using "rollout latest".
    private void startRollout(String project, String name, int version) {
        try {
            steps.sh(
                script: "oc -n ${project} rollout latest dc/${name}",
                label: "Rollout latest version of dc/${name}"
            )
        } catch (ex) {
            // It could be that some other process (e.g. image trigger) started
            // a rollout just before we wanted to start it. In that case, we
            // do not need to fail.
            def newVersion = getRevision(project, DEPLOYMENTCONFIG_KIND, name)
            if (newVersion > version) {
                logger.debug("Rollout #${newVersion} has been started by another process")
            } else {
                throw ex
            }
        }
    }

    // restartRollout triggers a rollout of a Deployment resource.
    // Available in Kubernetes 1.15+, see https://github.com/kubernetes/kubernetes/issues/13488.
    // This means this will simply fail on an OpenShift 3.11 cluster.
    // DeploymentConfig resources cannot be targeted using "rollout restart".
    private void restartRollout(String project, String name, int version) {
        try {
            steps.sh(
                script: "oc -n ${project} rollout restart deployment/${name} ${logger.ocDebugFlag}",
                label: "Rollout restart of deployment/${name}"
            )
        } catch (ex) {
            // It could be that some other process (e.g. image trigger) started
            // a rollout just before we wanted to start it. In that case, we
            // do not need to fail.
            def newVersion = getRevision(project, DEPLOYMENT_KIND, name)
            if (newVersion > version) {
                logger.debug("Rollout #${newVersion} has been started by another process")
            } else {
                throw ex
            }
        }
    }

    private void doWatchRollout(String project, String kind, String name) {
        steps.sh(
            script: "oc -n ${project} rollout status ${kind}/${name} --watch=true",
            label: "Watch rollout of latest version of ${kind}/${name}"
        )
    }

    private void reloginToCurrentClusterIfNeeded() {
        def kubeUrl = steps.env.KUBERNETES_MASTER ?: 'https://kubernetes.default:443'
        def success = steps.sh(
            script: """
               ${logger.shellScriptDebugFlag}
                oc login ${kubeUrl} --insecure-skip-tls-verify=true \
                --token=\$(cat /run/secrets/kubernetes.io/serviceaccount/token) &> /dev/null
            """,
            returnStatus: true,
            label: 'Check if OCP session exists'
        ) == 0
        if (!success) {
            throw new RuntimeException(
                'Could not (re)login to cluster, this is a systemic failure'
            )
        }
    }

    private void importImageFromProject(
        String project,
        String sourceProject,
        String sourceImageRef,
        String targetImageRef) {
        steps.sh(
            script: """oc -n ${project} tag ${sourceProject}/${sourceImageRef} ${targetImageRef}""",
            label: "Import image ${sourceImageRef} to ${project}/${targetImageRef}"
        )
    }

    private void importImageFromSourceRegistry(
        String project,
        String sourceRegistrySecret,
        String sourceProject,
        String sourceImageRef,
        String targetImageRef) {
        def sourceClusterRegistryHost = getSourceClusterRegistryHost(project, sourceRegistrySecret)
        def sourceImageFull = "${sourceClusterRegistryHost}/${sourceProject}/${sourceImageRef}"
        steps.sh(
            script: """
              oc -n ${project} import-image ${targetImageRef} \
                --from=${sourceImageFull} \
                --confirm \
                ${logger.ocDebugFlag}
            """,
            label: "Import image ${sourceImageFull} into ${project}/${targetImageRef}"
        )
    }

    @SuppressWarnings(['CyclomaticComplexity', 'AbcMetric'])
    @TypeChecked(TypeCheckingMode.SKIP)
    private List<PodData> extractPodData(Map podJson) {
        List<PodData> pods = []
        podJson.items.each { podOCData ->
            def pod = [:]
            // Only set needed data on "pod"
            pod.podName = podOCData.metadata?.name ?: 'N/A'
            pod.podNamespace = podOCData.metadata?.namespace ?: 'N/A'
            pod.podMetaDataCreationTimestamp = podOCData.metadata?.creationTimestamp ?: 'N/A'
            pod.deploymentId = 'N/A'
            if (podOCData.metadata?.generateName) {
                pod.deploymentId = podOCData.metadata.generateName - ~/-$/ // Trim dash suffix
            }
            pod.podNode = podOCData.spec?.nodeName ?: 'N/A'
            pod.podIp = podOCData.status?.podIP ?: 'N/A'
            pod.podStatus = podOCData.status?.phase ?: 'N/A'
            pod.podStartupTimeStamp = podOCData.status?.startTime ?: 'N/A'
            pod.containers = [:]
            // We need to get the image SHA from the containerStatuses, and not
            // from the pod spec because the pod spec image field is optional
            // and may not contain an image SHA, but e.g. a tag, depending on
            // the pod manager (e.g. ReplicationController, ReplicaSet). See
            // https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.19/#container-v1-core.
            podOCData.spec?.containers?.each { container ->
                podOCData.status?.containerStatuses?.each { containerStatus ->
                    if (containerStatus.name == container.name) {
                        pod.containers[container.name] = containerStatus.imageID - ~/^docker-pullable:\/\//
                    }
                }
            }
            pods << new PodData(pod)
        }
        pods
    }

    private String tailorVerboseFlag() {
        steps.env.DEBUG ? '--verbose' : ''
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

    private void doTailorExport(
        String project,
        String tailorParams,
        Map<String,
        String> envParams,
        String targetFile) {
        reloginToCurrentClusterIfNeeded()
        // Export
        steps.sh(
            script: "tailor ${tailorVerboseFlag()} -n ${project} export ${tailorParams} > ${targetFile}",
            label: "Tailor export of ${project} (${tailorParams}) into ${targetFile}"
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
            envParams['TAILOR_NAMESPACE'] = project
            steps.sh(
                script: """echo "parameters:" >> ${targetFile}""",
                label: "Add parameters section into ${targetFile}"
            )
        }

        // Replace values from envParams with parameters, and add parameters into template.
        envParams['ODS_OPENSHIFT_APP_DOMAIN'] = getApplicationDomain(project)
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

    @TypeChecked(TypeCheckingMode.SKIP)
    private String getSourceClusterRegistryHost(String project, String secretName) {
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

    private String checkForBuildStatus(String project, String buildId) {
        steps.sh(
            returnStdout: true,
            script: "oc -n ${project} get build ${buildId} -o jsonpath='{.status.phase}'",
            label: "Get phase of build ${buildId}"
        ).toString().trim().toLowerCase()
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

    private int getDesiredReplicas(String project, String kind, String podManagerName) {
        def jsonPath = '{.spec.replicas}'
        def replicas = getJSONPath(project, (kind == DEPLOYMENTCONFIG_KIND) ? 'rc' : 'rs',
                                   podManagerName, jsonPath)
        if (!replicas.isInteger()) {
            throw new RuntimeException(
                "ERROR: Desired replicas of '${podManagerName}' is not a number: '${replicas}"
            )
        }

        replicas as int
    }

    private List<PodData> retrieveImageData(String project, String kind, String podManagerName) {
        def jsonPath = '{.spec.template.spec.containers[0].image}'
        def image = getJSONPath(project, (kind == DEPLOYMENTCONFIG_KIND) ? 'rc' : 'rs',
                                podManagerName, jsonPath)
        def imageInfo = imageInfoForImageUrl(image)
        def podJson = [
            items: [
                [
                    spec: [containers: [[name: imageInfo.name]]],
                    status: [containerStatuses: [[name: imageInfo.name, imageID: image]]]
                ]
            ]
        ]

        extractPodData(podJson)
    }

}
