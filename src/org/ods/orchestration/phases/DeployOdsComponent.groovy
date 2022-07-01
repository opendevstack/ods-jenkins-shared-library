package org.ods.orchestration.phases

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import org.ods.util.IPipelineSteps
import org.ods.util.ILogger
import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.services.ServiceRegistry
import org.ods.services.GitService
import org.ods.orchestration.util.DeploymentDescriptor
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project

// Deploy ODS comnponent (code or service) to 'qa' or 'prod'.
@TypeChecked
class DeployOdsComponent {

    private Project project
    private IPipelineSteps steps
    private GitService git
    private ILogger logger
    private OpenShiftService os

    DeployOdsComponent(Project project, IPipelineSteps steps, GitService git, ILogger logger) {
        this.project = project
        this.steps = steps
        this.git = git
        this.logger = logger
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    public void run(Map repo, String baseDir) {
        this.os = ServiceRegistry.instance.get(OpenShiftService)

        steps.dir(baseDir) {
            def openShiftDir = computeStartDir()

            DeploymentDescriptor deploymentDescriptor
            steps.dir(openShiftDir) {
                deploymentDescriptor = DeploymentDescriptor.readFromFile(steps)
            }
            if (!repo.data.openshift.deployments) {
                repo.data.openshift.deployments = [:]
            }

            def originalDeploymentVersions = gatherOriginalDeploymentVersions(deploymentDescriptor.deployments)

            def componentSelector = "app=${project.key}-${repo.id}"

            if (openShiftDir == 'chart'){
                componentSelector = "app.kubernetes.io/instance=${repo.id}"
                deploymentDescriptor.deployments.each { String deploymentName, Map deployment ->
                    importImages(deployment, deploymentName, project.sourceProject)
                }

                applyTemplates(openShiftDir, componentSelector, repo.id)

                deploymentDescriptor.deployments.each { String deploymentName, Map deployment ->
                    // fixme? or maybe not - because helm will wait or rollback, so we have to find
                    // the pod on the first attempt
                    def podData = os.checkForPodData(project.targetProject, componentSelector)

                    // TODO: Once the orchestration pipeline can deal with multiple replicas,
                    // update this to deal with multiple pods.
                    def pod = podData[0].toMap()

                    verifyImageShas(deployment, pod.containers)
                    repo.data.openshift.deployments << [(deploymentName): pod]
                }

            } else {
                applyTemplates(openShiftDir, componentSelector)
                deploymentDescriptor.deployments.each { String deploymentName, Map deployment ->

                    importImages(deployment, deploymentName, project.sourceProject)

                    def replicationController = os.rollout(
                        project.targetProject,
                        OpenShiftService.DEPLOYMENTCONFIG_KIND,
                        deploymentName,
                        originalDeploymentVersions[deploymentName],
                        project.environmentConfig?.openshiftRolloutTimeoutMinutes ?: 20
                    )

                    def podData = os.getPodDataForDeployment(
                        project.targetProject,
                        OpenShiftService.DEPLOYMENTCONFIG_KIND,
                        replicationController,
                        project.environmentConfig?.openshiftRolloutTimeoutRetries ?: 10
                    )
                    // TODO: Once the orchestration pipeline can deal with multiple replicas,
                    // update this to deal with multiple pods.
                    def pod = podData[0].toMap()

                    verifyImageShas(deployment, pod.containers)

                    repo.data.openshift.deployments << [(deploymentName): pod]
                }
            }
            if (deploymentDescriptor.createdByBuild) {
                def createdByBuildKey = DeploymentDescriptor.CREATED_BY_BUILD_STR
                repo.data.openshift[createdByBuildKey] = deploymentDescriptor.createdByBuild
            }
        }
    }

    private String computeStartDir() {
        if (steps.fileExists('chart')) {
            return 'chart'
        }
        if (steps.fileExists('openshift')) {
            return 'openshift'
        }
        if (steps.fileExists('openshift-exported')) {
            return 'openshift-exported'
        }
        throw new RuntimeException("Error: Could not determine starting directory. Neither of [chart, openshift, openshift-exported] found.")

    }

    private Map gatherOriginalDeploymentVersions(Map<String, Object> deployments) {
        deployments.collectEntries { deploymentName, deployment ->
            def dcExists = os.resourceExists(
                project.targetProject, OpenShiftService.DEPLOYMENTCONFIG_KIND, deploymentName
            )
            def latestVersion = 0
            if (dcExists) {
                latestVersion = os.getRevision(
                    project.targetProject, OpenShiftService.DEPLOYMENTCONFIG_KIND, deploymentName
                )
            }
            [(deploymentName): latestVersion]
        }
    }

    // TODO FIXME XXX
    private void applyTemplates(String startDir, String componentSelector, String repoId = null) {
        def jenkins = ServiceRegistry.instance.get(JenkinsService)
        steps.dir(startDir) {
            logger.info(
                "Applying desired OpenShift state defined in " +
                    "${startDir}@${project.baseTag} to ${project.targetProject} for component " +
                    "${repoId}"
            )
            def applyFunc = { String pkeyFile ->
                // FIXME: condition!
                if (startDir != 'chart'){
                    os.tailorApply(
                        project.targetProject,
                        [selector: componentSelector, exclude: 'bc'],
                        project.environmentParamsFile,
                        [], // no params
                        [], // no preserve flags
                        pkeyFile,
                        true // verify
                    )
                } else if (startDir == 'chart') {
                    final Map<String, Serializable> BUILD_PARAMS = project.loadBuildParams(steps)

                    // registry.svc:5000/foo/bar:a333333333333333333334
                    //                               ^^^^^^^^^^^^^^
                    final String RELEASE = repoId
                    final List<String> VALUES_FILES = ["values.yaml"]
                    final Map<String, String> VALUES = [
                        "imageTag": project.targetTag, 
                        "imageNamespace" : project.targetProject, 
                        "componentId" : repoId
                    ]
                    final List<String> DEFAULT_FLAGS = ['--install', '--atomic']
                    final List<String> ADDITIONAL_FLAGS = []
                    final boolean WITH_DIFF = true

                    os.helmUpgrade(
                        project.targetProject,
                        RELEASE,
                        VALUES_FILES,
                        VALUES,
                        DEFAULT_FLAGS,
                        ADDITIONAL_FLAGS,
                        WITH_DIFF)
                }
            }
            jenkins.maybeWithPrivateKeyCredentials(project.tailorPrivateKeyCredentialsId) { String pkeyFile ->
                applyFunc(pkeyFile)
            }
        }
    }

    private void importImages(Map deployment, String deploymentName, String sourceProject) {
        deployment.containers?.each { String containerName, String imageRaw ->
            importImage(deploymentName, containerName, imageRaw, sourceProject)
        }
    }

    private void importImage(String deploymentName, String containerName, String imageRaw, String sourceProject) {
        // skip excluded images from defined image streams!
        logger.info(
            "Importing images - deployment: ${deploymentName}, " +
            "container: ${containerName}, image: ${imageRaw}, source: ${sourceProject}"
        )
        def imageParts = imageRaw.split('/')
        if (MROPipelineUtil.EXCLUDE_NAMESPACES_FROM_IMPORT.contains(imageParts.first())) {
            logger.debug(
                "Skipping import of '${imageRaw}', " +
                "because it is defined as excluded: ${MROPipelineUtil.EXCLUDE_NAMESPACES_FROM_IMPORT}"
            )
        } else {
            def imageInfo = imageParts.last().split('@')
            def imageName = imageInfo.first()
            def imageSha = imageInfo.last()
            if (project.targetClusterIsExternal) {
                os.importImageShaFromSourceRegistry(
                    project.targetProject,
                    imageName,
                    project.sourceRegistrySecretName,
                    sourceProject,
                    imageSha,
                    project.targetTag
                )
            } else {
                os.importImageShaFromProject(
                    project.targetProject,
                    imageName,
                    sourceProject,
                    imageSha,
                    project.targetTag
                )
            }
            // tag with latest, which might trigger rollout
            os.setImageTag(project.targetProject, imageName, project.targetTag, 'latest')
        }
    }

    private void verifyImageShas(Map deployment, Map podContainers) {
        deployment.containers?.each { String containerName, String imageRaw ->
            if (!os.verifyImageSha(containerName, imageRaw, podContainers[containerName].toString())) {
                throw new RuntimeException("Error: Image verification for container '${containerName}' failed.")
            }
        }
    }
}
