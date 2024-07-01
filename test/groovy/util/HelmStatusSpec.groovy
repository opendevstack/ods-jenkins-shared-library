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
        def helmParsedStatus = HelmStatusData.fromJsonObject(new JsonSlurperClassic().parseText(file.text))
        def helmStatus = HelmStatusSimpleData.from(helmParsedStatus)
        def simpleStatusMap = helmStatus.toMap()
        def simpleStatusNoResources = simpleStatusMap.findAll { k,v -> k != "resources"}
        def deploymentResources = helmStatus.getResourcesByKind([
            OpenShiftService.DEPLOYMENT_KIND, OpenShiftService.DEPLOYMENTCONFIG_KIND,])
        then:
        simpleStatusNoResources == [
            releaseName: 'standalone-app',
            releaseRevision: '43',
            namespace: 'guardians-test',
            deployStatus: 'deployed',
            deployDescription: 'Upgrade complete',
            lastDeployed: '2024-03-04T15:21:09.34520527Z'
        ]
        simpleStatusMap.resources == [
            [kind: 'ConfigMap', name:'core-appconfig-configmap'],
            [kind: 'Deployment', name:'core'],
            [kind: 'Deployment', name:'standalone-gateway'],
            [kind: 'Service', name:'core'],
            [kind: 'Service', name:'standalone-gateway'],
            [kind: 'Cluster', name:'edb-cluster'],
            [kind: 'Secret', name:'core-rsa-key-secret'],
            [kind: 'Secret', name:'core-security-exandradev-secret'],
            [kind: 'Secret', name:'core-security-unify-secret']
        ]
        deploymentResources == [
            Deployment: [ 'core', 'standalone-gateway']
        ]

    }
}
