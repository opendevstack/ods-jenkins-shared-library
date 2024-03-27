package org.ods.orchestration.util

import org.ods.util.IPipelineSteps

import groovy.json.JsonOutput

class DeploymentDescriptor {

    static final String FILE_NAME = 'ods-deployments.json'
    static final String CREATED_BY_BUILD_STR = 'CREATED_BY_BUILD'

    private final String createdByBuild
    private Map deployments

    DeploymentDescriptor(Map deployments, String createdByBuild) {
        this.deployments = deployments ?: [:]
        this.createdByBuild = createdByBuild ?: ''
    }

    static Map stripDeployments(Map deployments) {
        def strippedDownDeployments = [:]
        def deploymentMeanPostfix = 'deploymentMean'
        deployments.each { dn, d ->
            def strippedDownContainers = [:]
            if (!dn.endsWith(deploymentMeanPostfix)) {
                d.containers.each { cn, image ->
                    def imageParts = image.split('/')[-2..-1]
                    if (MROPipelineUtil.EXCLUDE_NAMESPACES_FROM_IMPORT.contains(imageParts.first())) {
                        strippedDownContainers[cn] = imageParts.join('/')
                    } else {
                        strippedDownContainers[cn] = imageParts.last()
                    }
                }
                strippedDownDeployments[dn] = [containers: strippedDownContainers]
                // get if exists the mean of deployment - this is the gstring pain again
                String dMKey = dn + '-' + deploymentMeanPostfix
                Map deploymentMean = deployments[dMKey]
                if (deploymentMean) {
                    strippedDownDeployments[dn] << [(deploymentMeanPostfix): deploymentMean]
                }
            }
        }
        strippedDownDeployments
    }

    static DeploymentDescriptor readFromFile(IPipelineSteps steps) {
        if (!steps.fileExists(FILE_NAME)) {
            throw new RuntimeException("No such file: ${FILE_NAME}")
        }
        def dd = steps.readJSON(file: FILE_NAME)
        // Backwards compatibility layer for master
        def deployments = dd.deployments
        if (!deployments) {
            deployments = [:]
            dd.each { deploymentName, deployment ->
                if (deploymentName != CREATED_BY_BUILD_STR) {
                    deployments[deploymentName] = deployment
                }
            }
        }
        new DeploymentDescriptor(deployments, dd[CREATED_BY_BUILD_STR])
    }

    Map getDeployments() {
        deployments
    }

    List<String> getDeploymentNames() {
        deployments.collect { k, v -> k }
    }

    String getCreatedByBuild() {
        createdByBuild
    }

    boolean buildValidForVersion(String version) {
        def buildParts = createdByBuild.split('/')
        buildParts.size() == 2 && buildParts.first() == version
    }

    void writeToFile(IPipelineSteps steps) {
        def content = [
            deployments: deployments,
            (CREATED_BY_BUILD_STR): createdByBuild,
        ]
        steps.writeFile(
            file: FILE_NAME,
            text: JsonOutput.prettyPrint(JsonOutput.toJson(content))
        )
    }

}
