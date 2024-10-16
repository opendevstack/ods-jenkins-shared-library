package org.ods.util

import groovy.json.JsonSlurperClassic
import org.ods.services.OpenShiftService
import util.FixtureHelper
import util.SpecHelper

class HelmStatusSpec extends SpecHelper {
    def "helm status parsing"() {
        given:
        def file = new FixtureHelper().getResource("helmstatus.json")

        when:
        def jsonObject = new JsonSlurperClassic().parseText(file.text)
        def helmStatus = HelmStatusSimpleData.fromJsonObject(jsonObject)
        def simpleStatusMap = helmStatus.toMap()
        def simpleStatusNoResources = simpleStatusMap.findAll { k,v -> k != "resourcesByKind"}
        def deploymentResources = helmStatus.getResourcesByKind([
            OpenShiftService.DEPLOYMENT_KIND, OpenShiftService.DEPLOYMENTCONFIG_KIND,])
        then:
        simpleStatusNoResources == [
            name: 'standalone-app',
            version: '43',
            namespace: 'guardians-test',
            status: 'deployed',
            description: 'Upgrade complete',
            lastDeployed: '2024-03-04T15:21:09.34520527Z'
        ]
        simpleStatusMap.resourcesByKind == [
            ConfigMap: ['core-appconfig-configmap'],
            Deployment: ['core', 'standalone-gateway'],
            Service: ['core', 'standalone-gateway'],
            Cluster: ['edb-cluster'],
            Secret: ['core-rsa-key-secret', 'core-security-exandradev-secret', 'core-security-unify-secret']
        ]
        deploymentResources == [
            Deployment: [ 'core', 'standalone-gateway']
        ]

    }
}
