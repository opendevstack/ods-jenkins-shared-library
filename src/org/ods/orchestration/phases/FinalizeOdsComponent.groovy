package org.ods.orchestration.phases

import org.ods.util.IPipelineSteps
import org.ods.util.ILogger
import org.ods.services.GitService
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.services.GitService
import org.ods.orchestration.util.DeploymentDescriptor
import org.ods.orchestration.util.Project
import org.ods.orchestration.util.MROPipelineUtil

// Finalize ODS comnponent (code or service) in 'dev'.
class FinalizeOdsComponent {

    private Project project
    private IPipelineSteps steps
    private GitService git
    private ILogger logger
    private OpenShiftService os

    FinalizeOdsComponent(Project project, IPipelineSteps steps, GitService git, ILogger logger) {
        this.project = project
        this.steps = steps
        this.git = git
        this.logger = logger
    }

    public void run(Map repo, String baseDir) {
        this.os = ServiceRegistry.instance.get(OpenShiftService)

        def componentSelector = "app=${project.key}-${repo.id}"

        verifyDeploymentsBuiltByODS(repo, componentSelector)

        def envParamsFile = project.environmentParamsFile
        def envParams = project.getEnvironmentParams(envParamsFile)

        steps.dir(baseDir) {
            def openshiftDir = findOrCreateOpenShiftDir()
            steps.dir(openshiftDir) {
                def filesToStage = []
                def commitMessage = ''
                if (openshiftDir == 'openshift-exported') {
                    commitMessage = 'ODS: Export OpenShift configuration ' +
                        "\r${steps.currentBuild.description}\r${steps.env.BUILD_URL}"
                    logger.debugClocked(
                        "export-ocp-${repo.id}",
                        "Exporting current OpenShift state to folder '${openshiftDir}'."
                    )
                    os.tailorExport(
                        componentSelector,
                        envParams,
                        OpenShiftService.EXPORTED_TEMPLATE_FILE
                    )
                    filesToStage << OpenShiftService.EXPORTED_TEMPLATE_FILE
                    logger.debugClocked("export-ocp-${repo.id}")
                } else {
                    commitMessage = "ODS: Export Openshift deployment state " +
                        "\r${steps.currentBuild.description}\r${steps.env.BUILD_URL}"
                    // TODO: Display drift?
                }

                writeDeploymentDescriptor(repo)

                logger.debugClocked("export-ocp-git-${repo.id}")
                filesToStage << DeploymentDescriptor.FILE_NAME
                git.commit(filesToStage, "${commitMessage} [ci skip]")
                logger.debugClocked("export-ocp-git-${repo.id}")
            }
        }
    }

    private String findOrCreateOpenShiftDir() {
        def openshiftDir = 'openshift-exported'
        if (steps.fileExists('openshift')) {
            logger.info(
                '''Found 'openshift' folder, current OpenShift state ''' +
                '''will not be exported into 'openshift-exported'.'''
            )
            openshiftDir = 'openshift'
        } else {
            steps.sh(
                script: "mkdir -p ${openshiftDir}",
                label: "Ensure ${openshiftDir} exists"
            )
        }
        openshiftDir
    }

    private void writeDeploymentDescriptor(Map repo) {
        def strippedDeployments = DeploymentDescriptor.stripDeployments(repo.data.openshift.deployments)
        def createdByBuild = repo.data.openshift[DeploymentDescriptor.CREATED_BY_BUILD_STR]
        if (!createdByBuild) {
            if (repo.data.openshift.resurrectedBuild) {
                createdByBuild = repo.data.openshift.resurrectedBuild
            } else {
                throw new RuntimeException(
                    "Could not determine value for ${DeploymentDescriptor.CREATED_BY_BUILD_STR}"
                )
            }
        }
        new DeploymentDescriptor(strippedDeployments, createdByBuild).writeToFile(steps)
    }

    private void verifyDeploymentsBuiltByODS(Map repo, String componentSelector) {
        def os = ServiceRegistry.instance.get(OpenShiftService)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)
        logger.debugClocked("export-ocp-verify-${repo.id}")
        // Verify that all DCs are managed by ODS
        def odsBuiltDeploymentInformation = repo.data.openshift.deployments ?: [:]
        def odsBuiltDeployments = odsBuiltDeploymentInformation.keySet()
        def allComponentDeployments = os.getDeploymentConfigsForComponent(componentSelector)
        logger.debug(
            "ODS created deployments for ${repo.id}: " +
            "${odsBuiltDeployments}, all deployments: ${allComponentDeployments}"
        )

        odsBuiltDeployments.each { odsBuiltDeploymentName ->
            allComponentDeployments.remove(odsBuiltDeploymentName)
        }

        if (allComponentDeployments.size() > 0 ) {
            def message = "DeploymentConfigs (component: '${repo.id}') found that are not ODS managed: " +
                "'${allComponentDeployments}'!\r" +
                "Please fix by rolling them out through 'odsComponentStageRolloutOpenShiftDeployment()'!"
            if (project.isWorkInProgress) {
                util.warnBuild(message)
            } else {
                throw new RuntimeException(message)
            }
        }

        def imagesFromOtherProjectsFail = []
        odsBuiltDeploymentInformation.each { odsBuiltDeploymentName, odsBuiltDeployment ->
            odsBuiltDeployment.containers?.each { containerName, containerImage ->
                def owningProject = os.imageInfoWithShaForImageStreamUrl(containerImage).repository
                if (project.targetProject != owningProject
                    && !MROPipelineUtil.EXCLUDE_NAMESPACES_FROM_IMPORT.contains(owningProject)) {
                    def msg = "Deployment: ${odsBuiltDeploymentName} / " +
                        "Container: ${containerName} / Owner: ${owningProject}"
                    logger.warn "! Image out of scope! ${msg}"
                    imagesFromOtherProjectsFail << msg
                }
            }
        }

        if (imagesFromOtherProjectsFail.size() > 0 ) {
            def message = "Containers (component: '${repo.id}') found " +
                "that will NOT be transferred to other environments - please fix!! " +
                "\rOffending: ${imagesFromOtherProjectsFail}"
            if (project.isWorkInProgress) {
                util.warnBuild(message)
            } else {
                throw new RuntimeException(message)
            }
        }
        logger.debugClocked("export-ocp-verify-${repo.id}")
    }
}
