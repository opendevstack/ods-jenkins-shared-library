package org.ods.orchestration.phases

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

            deploymentDescriptor.deployments.each { deploymentName, deployment ->

                importImages(deployment, deploymentName, computeSourceProject())

                def replicationController = os.rollout(
                    deploymentName,
                    originalDeploymentVersions[deploymentName],
                    project.environmentConfig?.openshiftRolloutTimeoutMinutes ?: 10
                )

                def pod = os.getPodDataForDeployment(replicationController,
                    project.environmentConfig?.openshiftRolloutTimeoutRetries ?: 10)

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

    private void computeSourceProject() {
        def sourceEnv = Project.getConcreteEnvironment(
            project.sourceEnv,
            project.buildParams.version,
            project.versionedDevEnvsEnabled
        )
        "${project.key}-${sourceEnv}"
    }

    private Map gatherOriginalDeploymentVersions(Map deployments) {
        deployments.collectEntries { deploymentName, deployment ->
            def dcExists = os.resourceExists('DeploymentConfig', deploymentName)
            def latestVersion = dcExists ? os.getLatestVersion(deploymentName) : 0
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
            def applyFunc = { pkeyFile ->
                os.tailorApply(
                        [selector: componentSelector, exclude: 'bc'],
                        project.environmentParamsFile,
                        [], // no params
                        [], // no preserve flags
                        pkeyFile,
                        true // verify
                    )
            }
            jenkins.maybeWithPrivateKeyCredentials(project.tailorPrivateKeyCredentialsId) { pkeyFile ->
                applyFunc(pkeyFile)
            }
        }
    }

    private void importImages(Map deployment, String deploymentName, String sourceProject) {
        deployment.containers?.each {containerName, imageRaw ->
            importImage(deploymentName, containerName, imageRaw, sourceProject)
        }
    }

    private void importImage(String deploymentName, String containerName, String imageRaw, String sourceProject) {
        // skip excluded images from defined image streams!
        //def imageInfo = os.imageInfoWithShaForImageStreamUrl(imageRaw)
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
                    imageName,
                    project.sourceRegistrySecretName,
                    sourceProject,
                    imageSha,
                    project.targetTag
                )
            } else {
                os.importImageShaFromProject(
                    imageName,
                    sourceProject,
                    imageSha,
                    project.targetTag
                )
            }
            // tag with latest, which might trigger rollout
            os.setImageTag(imageName, project.targetTag, 'latest')
        }
    }

    private void verifyImageShas(Map deployment, Map podContainers) {
        deployment.containers?.each { containerName, imageRaw ->
            if (!os.verifyImageSha(containerName, imageRaw, podContainers[containerName])) {
                throw new RuntimeException("Error: Image verification for container '${containerName}' failed.")
            }
        }
    }
}
