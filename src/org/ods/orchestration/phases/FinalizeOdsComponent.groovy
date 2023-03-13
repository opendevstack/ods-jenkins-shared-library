package org.ods.orchestration.phases

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.orchestration.util.DeploymentDescriptor
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.services.GitService
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
// Finalize ODS comnponent (code or service) in 'dev'.
@TypeChecked
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

    // @ FIXME - this is all hardcoded - and should come off the deployments!!!
    @TypeChecked(TypeCheckingMode.SKIP)
    public void run(Map repo, String baseDir) {
        this.os = ServiceRegistry.instance.get(OpenShiftService)

        steps.dir(baseDir) {
            Map deploymentMean = verifyDeploymentsBuiltByODS(repo).values().get(0) as Map
            logger.debug("DeploymentMean: ${deploymentMean}")

            def openshiftDir
            if (deploymentMean.type == 'helm') {
                openshiftDir = deploymentMean.chartDir
            } else {
                openshiftDir = findOrCreateOpenShiftDir()
            }

            def componentSelector = deploymentMean.selector

            steps.dir(openshiftDir) {
                def filesToStage = []
                def commitMessage = ''
                if (openshiftDir == 'openshift-exported') {
                    def envParamsFile = project.environmentParamsFile
                    def envParams = project.getEnvironmentParams(envParamsFile)
                    commitMessage = 'ODS: Export OpenShift configuration ' +
                        "\r${commitBuildReference()}"
                    logger.debugClocked(
                        "export-ocp-${repo.id}",
                        "Exporting current OpenShift state to folder '${openshiftDir}'."
                    )
                    os.tailorExport(
                        project.targetProject,
                        componentSelector as String,
                        envParams as Map<String, String>,
                        OpenShiftService.EXPORTED_TEMPLATE_FILE
                    )
                    filesToStage << OpenShiftService.EXPORTED_TEMPLATE_FILE
                    logger.debugClocked("export-ocp-${repo.id}", (null as String))
                } else {
                    commitMessage = "ODS: Export Openshift deployment state " +
                        "\r${commitBuildReference()}"
                    // TODO: Display drift?
                }

                writeDeploymentDescriptor(repo)

                logger.debugClocked("export-ocp-git-${repo.id}", (null as String))
                filesToStage << DeploymentDescriptor.FILE_NAME
                git.commit(filesToStage, "${commitMessage} [ci skip]")
                logger.debugClocked("export-ocp-git-${repo.id}", (null as String))
            }
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private String commitBuildReference() {
        "${steps.currentBuild.description}\r${steps.env.BUILD_URL}"
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

    @TypeChecked(TypeCheckingMode.SKIP)
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

    @TypeChecked(TypeCheckingMode.SKIP)
    @SuppressWarnings(['AbcMetric'])
    private Map verifyDeploymentsBuiltByODS(Map repo) {
        def os = ServiceRegistry.instance.get(OpenShiftService)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)
        logger.debugClocked("export-ocp-verify-${repo.id}", (null as String))
        // Verify that all DCs are managed by ODS
        def odsBuiltDeploymentInformation = repo.data.openshift.deployments ?: [:]
        def odsBuiltDeployments = odsBuiltDeploymentInformation.keySet()

        Map deploymentMeans = odsBuiltDeploymentInformation.findAll {it.key.endsWith('-deploymentMean') }

        logger.debug("Found Deploymentmean(s) for ${repo.id}: \n${deploymentMeans}")

        if (deploymentMeans.size() == 0) {
            throw new RuntimeException ("Deploymentmeans for repo ${repo.id}" +
                " are not found. These means are automatically generated!\n" +
                " Likely you upgraded and tried to deploy to Q instead of rebuilding on dev")
        }

        deploymentMeans.values().each{deploymentMean->
            String componentSelector = deploymentMean.selector

            def allComponentDeploymentsByKind = os.getResourcesForComponent(
                project.targetProject,
                [OpenShiftService.DEPLOYMENTCONFIG_KIND, OpenShiftService.DEPLOYMENT_KIND],
                componentSelector
            )
            def allComponentDeployments = allComponentDeploymentsByKind[OpenShiftService.DEPLOYMENTCONFIG_KIND] ?: []
            if (allComponentDeploymentsByKind[OpenShiftService.DEPLOYMENT_KIND]) {
                allComponentDeployments.addAll(allComponentDeploymentsByKind[OpenShiftService.DEPLOYMENT_KIND])
            }
            logger.debug(
                "ODS created deployments for ${repo.id}: " +
                "${odsBuiltDeployments}, all deployments: ${allComponentDeployments}"
            )

            odsBuiltDeployments.each { odsBuiltDeploymentName ->
                allComponentDeployments.remove(odsBuiltDeploymentName)
            }


            if (allComponentDeployments != null && allComponentDeployments.size() > 0 ) {
                def message = "DeploymentConfigs (component: '${repo.id}') found that are not ODS managed: " +
                    "'${allComponentDeployments}'!\r" +
                    "Please fix by rolling them out through 'odsComponentStageRolloutOpenShiftDeployment()'!"
                if (project.isWorkInProgress) {
                    util.warnBuild(message)
                } else {
                    throw new RuntimeException(message)
                }
            }
        }

        def imagesFromOtherProjectsFail = []
        // All images in *-cd are also present in *-dev, *-test, etc. so even
        // if the underlying image points to *-cd, we can continue.
        def excludedProjects = MROPipelineUtil.EXCLUDE_NAMESPACES_FROM_IMPORT + ["${project.key}-cd".toString()]
        odsBuiltDeploymentInformation.each { String odsBuiltDeploymentName, Map odsBuiltDeployment ->
            odsBuiltDeployment.containers?.each { String containerName, String containerImage ->
                def owningProject = os.imageInfoWithShaForImageStreamUrl(containerImage).repository
                if (project.targetProject != owningProject && !excludedProjects.contains(owningProject)) {
                    def msg = "Deployment: ${odsBuiltDeploymentName} / " +
                        "Container: ${containerName} / Owner: ${owningProject}/ Excluded Projects: ${excludedProjects}"
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
        logger.debugClocked("export-ocp-verify-${repo.id}", (null as String))
        return deploymentMeans
    }
}
