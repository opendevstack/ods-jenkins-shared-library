package org.ods.orchestration.phases

import org.ods.orchestration.util.DeploymentDescriptor
import org.ods.services.JenkinsService
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.HelmStatus
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import util.FixtureHelper
import spock.lang.Specification

class DeployOdsComponentSpec extends Specification {

    def "passes persisted Helm values to global values"() {
        given:
        def registryAddress = 'image-registry.openshift.svc:5000'
        def helmStatus = HelmStatus.fromJsonObject(FixtureHelper.createHelmCmdStatusMap())
        def deploymentDescriptor = [
            deployments: [
                backend: [
                    containers: [:],
                    deploymentMean: [
                        helmReleaseName: 'backend',
                        helmValuesFiles: ['values.yaml'],
                        helmValues: [registry: registryAddress, featureFlag: true],
                        helmDefaultFlags: ['--install', '--atomic'],
                        helmAdditionalFlags: [],
                        helmEnvBasedValuesFiles: [],
                    ],
                ],
            ],
        ]
        IPipelineSteps steps = Stub()
        steps.dir(_, _ as Closure) >> { args -> args[1]() }
        steps.findFiles(_) >> [new File('chart/ods-deployments.json')]
        steps.fileExists(DeploymentDescriptor.FILE_NAME) >> true
        steps.readJSON(_) >> deploymentDescriptor

        def project = Spy(FixtureHelper.createProject())
        project.sourceProject >> 'myproject-dev'
        project.targetProject = 'myproject-qa'
        def openShift = Mock(OpenShiftService)
        def jenkins = Stub(JenkinsService)
        jenkins.maybeWithPrivateKeyCredentials(_, _ as Closure) >> { args -> args[1]('') }
        ServiceRegistry.instance.add(OpenShiftService, openShift)
        ServiceRegistry.instance.add(JenkinsService, jenkins)

        def repo = [id: 'backend', data: [openshift: [deployments: [:]]]]

        when:
        new DeployOdsComponent(project, steps, null, Stub(ILogger)).run(repo, 'repo')

        then:
        1 * openShift.helmUpgrade(
            'myproject-qa',
            'backend',
            ['values.yaml'],
            { Map values ->
                values.registry == registryAddress &&
                    values['global.registry'] == registryAddress &&
                    values.featureFlag == true &&
                    values['global.featureFlag'] == true
            },
            ['--install', '--atomic'],
            [],
            true
        )
        1 * openShift.helmStatus('myproject-qa', 'backend') >> helmStatus
    }
}
