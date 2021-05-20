package org.ods.orchestration.phases

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import org.ods.util.IPipelineSteps
import org.ods.util.ILogger
import org.ods.services.GitService
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
            def openShiftDir = computeOpenShiftDir()

            DeploymentDescriptor deploymentDescriptor
            steps.dir(openShiftDir) {
                deploymentDescriptor = DeploymentDescriptor.readFromFile(steps)
            }
            if (!repo.data.openshift.deployments) {
                repo.data.openshift.deployments = [:]
            }

            def originalDeploymentVersions = gatherOriginalDeploymentVersions(deploymentDescriptor.deployments)

            applyTemplates(openShiftDir, "app=${project.key}-${repo.id}")

            deploymentDescriptor.deployments.each { String deploymentName, Map deployment ->

                importImages(deployment, deploymentName, project.sourceProject)

                def replicationController = os.rollout(
                    project.targetProject,
                    OpenShiftService.DEPLOYMENTCONFIG_KIND,
                    deploymentName,
                    originalDeploymentVersions[deploymentName],
                    project.environmentConfig?.openshiftRolloutTimeoutMinutes ?: 10
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

            if (deploymentDescriptor.createdByBuild) {
                def createdByBuildKey = DeploymentDescriptor.CREATED_BY_BUILD_STR
                repo.data.openshift[createdByBuildKey] = deploymentDescriptor.createdByBuild
            }
        }
    }

    private String computeOpenShiftDir() {
        def openShiftDir = 'openshift-exported'
        if (steps.fileExists('openshift')) {
            openShiftDir = 'openshift'
        }
        openShiftDir
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

    private void applyTemplates(String openShiftDir, String componentSelector) {
        def jenkins = ServiceRegistry.instance.get(JenkinsService)
        steps.dir(openShiftDir) {
            logger.info(
                "Applying desired OpenShift state defined in " +
                "${openShiftDir}@${project.baseTag} to ${project.targetProject}."
            )
            def applyFunc = { String pkeyFile ->
                os.tailorApply(
                        project.targetProject,
                        [selector: componentSelector, exclude: 'bc'],
                        project.environmentParamsFile,
                        [], // no params
                        [], // no preserve flags
                        pkeyFile,
                        true // verify
                    )
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
