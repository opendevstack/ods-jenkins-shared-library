package vars

import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import org.ods.component.Context
import org.ods.component.IContext
import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.PodData
import util.PipelineSteps
import vars.test_helper.PipelineSpockTestBase
import spock.lang.*

class OdsComponentStageRolloutOpenShiftDeploymentSpec extends PipelineSpockTestBase {

  private Logger logger = Mock(Logger)

  @Shared
  def config = [
      gitUrl: 'https://example.com/scm/foo/bar.git',
      gitCommit: 'cd3e9082d7466942e1de86902bb9e663751dae8e',
      gitCommitMessage: 'Foo',
      gitCommitAuthor: 'John Doe',
      gitCommitTime: '2020-03-23 12:27:08 +0100',
      gitBranch: 'master',
      buildUrl: 'https://jenkins.example.com/job/foo-cd/job/foo-cd-bar-master/11/console',
      buildTime: '2020-03-23 12:27:08 +0100',
      odsSharedLibVersion: '2.x',
      projectId: 'foo',
      componentId: 'bar',
      cdProject: 'foo-cd',
      artifactUriStore: [builds: [bar: [:]]]
  ]

  def "run successfully without Tailor [DeploymentConfig]"() {
    given:
    def c = config + [environment: 'dev', targetProject: 'foo-dev', openshiftRolloutTimeoutRetries: 6]
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Stub(OpenShiftService.class)
    openShiftService.getResourcesForComponent('foo-dev', ['Deployment', 'DeploymentConfig'], 'app=foo-bar') >> [DeploymentConfig: ['bar']]
    openShiftService.getRevision('foo-dev', 'DeploymentConfig', 'bar') >> 123
    openShiftService.rollout('foo-dev', 'DeploymentConfig', 'bar', 123, 15) >> "bar-124"
    // test the handover of the poddata retries
    openShiftService.getPodDataForDeployment('foo-dev', 'DeploymentConfig', 'bar-124', 6) >> [new PodData([ deploymentId: "bar-124" ])]
    openShiftService.getImagesOfDeployment('foo-dev', 'DeploymentConfig', 'bar') >> [[ repository: 'foo', name: 'bar' ]]
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)
    JenkinsService jenkinsService = Stub(JenkinsService.class)
    jenkinsService.maybeWithPrivateKeyCredentials(*_) >> { args -> args[1]('/tmp/file') }
    ServiceRegistry.instance.add(JenkinsService, jenkinsService)

    when:
    def script = loadScript('vars/odsComponentStageRolloutOpenShiftDeployment.groovy')
    helper.registerAllowedMethod('fileExists', [ String ]) { String args ->
      false
    }
    def deploymentInfo = script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
    deploymentInfo['DeploymentConfig/bar'][0].deploymentId == "bar-124"

    // test artifact URIS
    def buildArtifacts = context.getBuildArtifactURIs()
    buildArtifacts.size() > 0
    buildArtifacts.deployments.containsKey (config.componentId)
    buildArtifacts.deployments[config.componentId].deploymentId == deploymentInfo['DeploymentConfig/bar'][0].deploymentId
  }

  def "run successfully without Tailor [Deployment]"() {
    given:
    def c = config + [environment: 'dev', targetProject: 'foo-dev', openshiftRolloutTimeoutRetries: 6]
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Stub(OpenShiftService.class)
    openShiftService.getResourcesForComponent('foo-dev', ['Deployment', 'DeploymentConfig'], 'app=foo-bar') >> [Deployment: ['bar']]
    openShiftService.getRevision('foo-dev', 'Deployment', 'bar') >> 123
    openShiftService.rollout('foo-dev', 'Deployment', 'bar', 123, 15) >> "bar-6f8db5fb69"
    // test the handover of the poddata retries
    openShiftService.getPodDataForDeployment('foo-dev', 'Deployment', 'bar-6f8db5fb69', 6) >> [new PodData([ deploymentId: "bar-6f8db5fb69" ])]
    openShiftService.getImagesOfDeployment('foo-dev', 'Deployment', 'bar') >> [[ repository: 'foo', name: 'bar' ]]
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)
    JenkinsService jenkinsService = Stub(JenkinsService.class)
    jenkinsService.maybeWithPrivateKeyCredentials(*_) >> { args -> args[1]('/tmp/file') }
    ServiceRegistry.instance.add(JenkinsService, jenkinsService)

    when:
    def script = loadScript('vars/odsComponentStageRolloutOpenShiftDeployment.groovy')
    helper.registerAllowedMethod('fileExists', [ String ]) { String args ->
      false
    }
    def deploymentInfo = script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
    deploymentInfo['Deployment/bar'][0].deploymentId == "bar-6f8db5fb69"

    // test artifact URIS
    def buildArtifacts = context.getBuildArtifactURIs()
    buildArtifacts.size() > 0
    buildArtifacts.deployments.containsKey (config.componentId)
    buildArtifacts.deployments[config.componentId].deploymentId == deploymentInfo['Deployment/bar'][0].deploymentId
  }

  def "run successfully with Tailor"() {
    given:
    def c = config + [environment: 'dev', targetProject: 'foo-dev', openshiftRolloutTimeoutRetries: 5]
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Mock(OpenShiftService.class)
    openShiftService.getResourcesForComponent('foo-dev', ['Deployment', 'DeploymentConfig'], 'app=foo-bar') >> [DeploymentConfig: ['bar']]
    openShiftService.getRevision(*_) >> 123
    openShiftService.rollout(*_) >> "${config.componentId}-124"
    openShiftService.getPodDataForDeployment(*_) >> [new PodData([ deploymentId: "${config.componentId}-124" ])]
    openShiftService.getImagesOfDeployment(*_) >> [[ repository: 'foo', name: 'bar' ]]
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)
    JenkinsService jenkinsService = Stub(JenkinsService.class)
    jenkinsService.maybeWithPrivateKeyCredentials(*_) >> { args -> args[1]('/tmp/file') }
    ServiceRegistry.instance.add(JenkinsService, jenkinsService)

    when:
    def script = loadScript('vars/odsComponentStageRolloutOpenShiftDeployment.groovy')
    helper.registerAllowedMethod('fileExists', [ String ]) { String args ->
      args == 'openshift'
    }
    def deploymentInfo = script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
    deploymentInfo['DeploymentConfig/bar'][0].deploymentId == "bar-124"

    // test artifact URIS
    def buildArtifacts = context.getBuildArtifactURIs()
    buildArtifacts.size() > 0
    buildArtifacts.deployments.containsKey(config.componentId)
    buildArtifacts.deployments[config.componentId].deploymentId == deploymentInfo['DeploymentConfig/bar'][0].deploymentId

    1 * openShiftService.tailorApply(
      'foo-dev',
      [selector: 'app=foo-bar', exclude: 'bc,is'],
      '',
      [],
      [],
      '/tmp/file',
      true
    )
  }

  def "run successfully with Helm"() {
    given:
    def c = config + [environment: 'dev',targetProject: 'foo-dev',openshiftRolloutTimeoutRetries: 5,chartDir: 'chart']
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Mock(OpenShiftService.class)
    openShiftService.getResourcesForComponent('foo-dev', ['Deployment', 'DeploymentConfig'], 'app=foo-bar') >> [Deployment: ['bar']]
    openShiftService.getRevision(*_) >> 123
    openShiftService.rollout(*_) >> "${config.componentId}-124"
    openShiftService.getPodDataForDeployment(*_) >> [new PodData([ deploymentId: "${config.componentId}-124" ])]
    openShiftService.getImagesOfDeployment(*_) >> [[ repository: 'foo', name: 'bar' ]]
    openShiftService.checkForPodData('foo-dev', 'app=foo-bar') >> [new PodData([deploymentId: "${config.componentId}-124"])]
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)
    JenkinsService jenkinsService = Stub(JenkinsService.class)
    jenkinsService.maybeWithPrivateKeyCredentials(*_) >> { args -> args[1]('/tmp/file') }
    ServiceRegistry.instance.add(JenkinsService, jenkinsService)

    when:
    def script = loadScript('vars/odsComponentStageRolloutOpenShiftDeployment.groovy')
    helper.registerAllowedMethod('fileExists', [ String ]) { String args ->
      args == 'chart/Chart.yaml' || args == 'metadata.yml'
    }
    helper.registerAllowedMethod('readYaml', [ Map ]) { Map args ->
        def testSteps = new PipelineSteps()
        testSteps.readYaml(args) { file ->
            def metadata = null
            switch (file) {
                case 'metadata.yml':
                    metadata = [
                        name:        'Name',
                        description: 'Description',
                        supplier:    'none',
                        version:     '1.0',
                        type:        'ods',
                    ]
                    break
                case 'chart/Chart.yaml':
                    metadata = [
                        name:    'myChart',
                        version: '1.0+01',
                    ]
                    break
            }
            return metadata
        }
    }
    def deploymentInfo = script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
    deploymentInfo['Deployment/bar'][0].deploymentId == "bar-124"

    // test artifact URIS
    def buildArtifacts = context.getBuildArtifactURIs()
    buildArtifacts.size() > 0
    buildArtifacts.deployments['bar-deploymentMean']['type'] == 'helm'

    1 * openShiftService.helmUpgrade('foo-dev', 'bar', ['values.yaml'], ['registry':null, 'componentId':'bar', 'global.registry':null, 'global.componentId':'bar', 'imageNamespace':'foo-dev', 'imageTag':'cd3e9082', 'global.imageNamespace':'foo-dev', 'global.imageTag':'cd3e9082'], ['--install', '--atomic'], [], true)
  }

  @Unroll
  def "fails when rollout info cannot be retrieved"() {
    given:
    def cfg = config + [environment: 'dev', targetProject: 'foo-dev', openshiftRolloutTimeoutRetries: 5]
    IContext context = new Context(null, cfg, logger)
    OpenShiftService openShiftService = Stub(OpenShiftService.class)
    openShiftService.getResourcesForComponent(_, { it == ['Deployment', 'DeploymentConfig'] }, _) >> [DeploymentConfig: ['bar']]
    openShiftService.resourceExists(_, { it == 'ImageStream' }, _) >> isExists
    openShiftService.getImagesOfDeployment(*_) >> images
    openShiftService.getRevision(*_) >> latestVersion
    openShiftService.rollout(*_) >> { a, b, c, d, e ->
            throw new RuntimeException('Boom!')
        }
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)
    JenkinsService jenkinsService = Stub(JenkinsService.class)
    jenkinsService.maybeWithPrivateKeyCredentials(*_) >> { args -> args[1]('/tmp/file') }
    ServiceRegistry.instance.add(JenkinsService, jenkinsService)

    when:
    def script = loadScript('vars/odsComponentStageRolloutOpenShiftDeployment.groovy')
    helper.registerAllowedMethod('fileExists', [ String ]) { String args ->
      false
    }
    script.call(context)

    then:
    printCallStack()
    assertCallStackContains(errorMessage)
    assertJobStatusFailure()

    where:
    dcExists | isExists | images                                 | latestVersion || errorMessage
    true     | false    | [[repository: 'foo-dev', name: 'baz']] | 0             || "The following ImageStream resources  for DeploymentConfig 'bar' do not exist: '[foo-dev/baz]'."
    true     | true     | [[repository: 'foo-dev', name: 'baz']] | 123           || "Boom!"
  }

  def "fail when triggered by orchestration and kind=Deployment"() {
    given:
    def c = config + [
      environment: 'dev',
      targetProject: 'foo-dev',
      openshiftRolloutTimeoutRetries: 5,
      triggeredByOrchestrationPipeline: true
    ]
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Stub(OpenShiftService.class)
    openShiftService.getResourcesForComponent(*_) >> [Deployment: ['bar']]
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)

    when:
    def script = loadScript('vars/odsComponentStageRolloutOpenShiftDeployment.groovy')
    helper.registerAllowedMethod('fileExists', [ String ]) { String args ->
        args == 'openshift'
    }
    script.call(context)

    then:
    printCallStack()
    assertCallStackContains("Deployment resources cannot be used in a NON HELM orchestration pipeline.")
    assertJobStatusFailure()
  }

  def "fail when resourceName is given"() {
    given:
    IContext context = new Context(null, [:], logger)

    when:
    def script = loadScript('vars/odsComponentStageRolloutOpenShiftDeployment.groovy')
    script.call(context, [resourceName: 'foo'])

    then:
    printCallStack()
    assertCallStackContains("The config option 'resourceName' has been removed from odsComponentStageRolloutOpenShiftDeployment")
    assertJobStatusFailure()
  }

  def "skip when no environment given"() {
    given:
    def config = [environment: null, gitCommit: 'cd3e9082d7466942e1de86902bb9e663751dae8e', openshiftRolloutTimeoutRetries: 5]
    def context = new Context(null, config, logger)

    when:
    def script = loadScript('vars/odsComponentStageRolloutOpenShiftDeployment.groovy')
    script.call(context)

    then:
    printCallStack()
    assertCallStackContains("WARN: Skipping because of empty (target) environment ...")
    assertJobStatusSuccess()
  }

  def "fails on incorrect options"() {
    given:
    def config = [
      environment: null,
      gitBranch: 'master',
      gitCommit: 'cd3e9082d7466942e1de86902bb9e663751dae8e',
      openshiftRolloutTimeoutRetries: 5,
      branchToEnvironmentMapping: [:]
    ]
    def context = new Context(null, config, logger)

    when:
    def script = loadScript('vars/odsComponentStageRolloutOpenShiftDeployment.groovy')
    script.call(context, options)

    then:
    def exception = thrown(wantEx)
    exception.message == wantExMessage

    where:
    options           || wantEx                   | wantExMessage
    [branches: 'abc'] || GroovyCastException      | "Cannot cast object 'abc' with class 'java.lang.String' to class 'java.util.List'"
    [foobar: 'abc']   || MissingPropertyException | "No such property: foobar for class: org.ods.component.RolloutOpenShiftDeploymentOptions"
  }

}
