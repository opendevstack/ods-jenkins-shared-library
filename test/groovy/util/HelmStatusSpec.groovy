package org.ods.util


import org.ods.services.OpenShiftService
import util.FixtureHelper
import util.SpecHelper

class HelmStatusSpec extends SpecHelper {
    def "helm status parsing"() {
        given:
        def helmStatusJsonObj = FixtureHelper.createHelmCmdStatusMap()

        when:
        def helmStatus = HelmStatus.fromJsonObject(helmStatusJsonObj)
        def simpleStatusMap = helmStatus.toMap()
        def simpleStatusNoResources = simpleStatusMap.findAll { k,v -> k != "resourcesByKind"}
        def helmStatusResources = helmStatus.getResources()
        def deploymentResources = helmStatusResources.subMap([
            OpenShiftService.DEPLOYMENT_KIND, OpenShiftService.DEPLOYMENTCONFIG_KIND])

        then:
        simpleStatusNoResources == [
            name: 'standalone-app',
            version: '43',
            namespace: 'myproject-test',
            status: 'deployed',
            description: 'Upgrade complete',
            lastDeployed: '2024-03-04T15:21:09.34520527Z'
        ]

        simpleStatusMap.resourcesByKind == [
            'Cluster': ['some-cluster'],
            'ConfigMap': ['core-appconfig-configmap'],
            'Deployment': ['core', 'standalone-gateway'],
            'Secret': ['core-rsa-key-secret', 'core-security-exandradev-secret', 'core-security-unify-secret'],
            'Service': ['core', 'standalone-gateway'],
        ]

        deploymentResources == [
            Deployment: [ 'core', 'standalone-gateway']
        ]
    }
}
