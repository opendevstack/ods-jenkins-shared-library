package vars

import org.ods.component.Context
import org.ods.component.IContext
import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.PodData
import org.ods.util.RegistryAccessInfo
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
    openShiftService.rollout('foo-dev', 'DeploymentConfig', 'bar', 123, 5) >> "bar-124"
    // test the handover of the poddata retries
    openShiftService.getPodDataForDeployment('foo-dev', 'DeploymentConfig', 'bar-124', 6) >> [new PodData([ deploymentId: "bar-124" ])]
    openShiftService.getImagesOfDeployment('foo-dev', 'DeploymentConfig', 'bar') >> [[ repository: 'foo', name: 'bar' ]]
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)

    when:
    def script = loadScript('vars/odsComponentStageRolloutOpenShiftDeployment.groovy')
    helper.registerAllowedMethod('fileExists', [ String ]) { String args ->
      false
    }
    def deploymentInfo = script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
    deploymentInfo.local['DeploymentConfig/bar'][0].deploymentId == "bar-124"

    // test artifact URIS
    def buildArtifacts = context.getBuildArtifactURIs()
    buildArtifacts.size() > 0
    buildArtifacts.deployments.containsKey (config.componentId)
    buildArtifacts.deployments[config.componentId].deploymentId == deploymentInfo.local['DeploymentConfig/bar'][0].deploymentId
  }

  def "run successfully without Tailor [Deployment]"() {
    given:
    def c = config + [environment: 'dev', targetProject: 'foo-dev', openshiftRolloutTimeoutRetries: 6]
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Stub(OpenShiftService.class)
    openShiftService.getResourcesForComponent('foo-dev', ['Deployment', 'DeploymentConfig'], 'app=foo-bar') >> [Deployment: ['bar']]
    openShiftService.getRevision('foo-dev', 'Deployment', 'bar') >> 123
    openShiftService.rollout('foo-dev', 'Deployment', 'bar', 123, 5) >> "bar-6f8db5fb69"
    // test the handover of the poddata retries
    openShiftService.getPodDataForDeployment('foo-dev', 'Deployment', 'bar-6f8db5fb69', 6) >> [new PodData([ deploymentId: "bar-6f8db5fb69" ])]
    openShiftService.getImagesOfDeployment('foo-dev', 'Deployment', 'bar') >> [[ repository: 'foo', name: 'bar' ]]
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)

    when:
    def script = loadScript('vars/odsComponentStageRolloutOpenShiftDeployment.groovy')
    helper.registerAllowedMethod('fileExists', [ String ]) { String args ->
      false
    }
    def deploymentInfo = script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
    deploymentInfo.local['Deployment/bar'][0].deploymentId == "bar-6f8db5fb69"

    // test artifact URIS
    def buildArtifacts = context.getBuildArtifactURIs()
    buildArtifacts.size() > 0
    buildArtifacts.deployments.containsKey (config.componentId)
    buildArtifacts.deployments[config.componentId].deploymentId == deploymentInfo.local['Deployment/bar'][0].deploymentId
  }

  def "run successfully with multiple targets"() {
    given:
    def c = config + [environment: 'dev', targetProject: 'foo-dev', openshiftRolloutTimeoutRetries: 5]
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Stub(OpenShiftService.class)
    openShiftService.resourceExists('foo-cd', 'cm', 'ods-envs') >> true
    openShiftService.getConfigMapDataKey('foo-cd', 'ods-envs', 'dev') >> """\
    local:
      namespace: foo-first
    second:
      namespace: foo-second
    third:
      namespace: foo-third
      apiUrl: https://api.example.com
      apiCredentialsSecret: api-secret
      registryHost: image-registry.example.com
    """
    openShiftService.getResourcesForComponent('foo-first', ['Deployment', 'DeploymentConfig'], 'app=foo-bar') >> [Deployment: ['bar']]
    openShiftService.getResourcesForComponent('foo-second', ['Deployment', 'DeploymentConfig'], 'app=foo-bar') >> [Deployment: ['bar']]
    openShiftService.getResourcesForComponent('foo-third', ['Deployment', 'DeploymentConfig'], 'app=foo-bar') >> [Deployment: ['bar']]
    openShiftService.getResourcesForComponent('foo-cd', ['ImageStream'], 'app=foo-bar') >> [ImageStream: ['bar']]
    openShiftService.getRegistrySecretOfServiceAccount('foo-cd', 'jenkins') >> 'jenkins-dockercfg-q239j'
    openShiftService.getRegistryAccessInfo('foo-cd', 'jenkins-dockercfg-q239j') >> new RegistryAccessInfo([host: 'src.com', username: 'src-user', password: 'src-pwd'])
    openShiftService.getRegistrySecretOfServiceAccount('foo-third', 'builder') >> 'builder-dockercfg-x239j'
    openShiftService.getRegistryAccessInfo('foo-third', 'builder-dockercfg-x239j') >> new RegistryAccessInfo([host: 'dest.com', username: 'dest-user', password: 'dest-pwd'])
    openShiftService.pushImage('docker://src.com/foo-cd/bar:cd3e9082', 'docker://dest.com/foo-third/bar:cd3e9082', 'src-user:src-pwd', 'dest-user:dest-pwd', []) >> true
    openShiftService.getRevision('foo-first', 'Deployment', 'bar') >> 123
    openShiftService.getRevision('foo-second', 'Deployment', 'bar') >> 456
    openShiftService.getRevision('foo-third', 'Deployment', 'bar') >> 789
    openShiftService.rollout('foo-first', 'Deployment', 'bar', 123, 5) >> "bar-6f8db5fb69"
    openShiftService.rollout('foo-second', 'Deployment', 'bar', 456, 5) >> "bar-7f8db5fb69"
    openShiftService.rollout('foo-third', 'Deployment', 'bar', 789, 5) >> "bar-8f8db5fb69"
    openShiftService.getPodDataForDeployment('foo-first', 'Deployment', 'bar-6f8db5fb69', 5) >> [new PodData([ deploymentId: "bar-6f8db5fb69" ])]
    openShiftService.getPodDataForDeployment('foo-second', 'Deployment', 'bar-7f8db5fb69', 5) >> [new PodData([ deploymentId: "bar-7f8db5fb69" ])]
    openShiftService.getPodDataForDeployment('foo-third', 'Deployment', 'bar-8f8db5fb69', 5) >> [new PodData([ deploymentId: "bar-8f8db5fb69" ])]
    openShiftService.getImagesOfDeployment('foo-first', 'Deployment', 'bar') >> [[ repository: 'foo', name: 'bar' ]]
    openShiftService.getImagesOfDeployment('foo-second', 'Deployment', 'bar') >> [[ repository: 'foo', name: 'bar' ]]
    openShiftService.getImagesOfDeployment('foo-third', 'Deployment', 'bar') >> [[ repository: 'foo', name: 'bar' ]]
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)

    when:
    def script = loadScript('vars/odsComponentStageRolloutOpenShiftDeployment.groovy')
    helper.registerAllowedMethod('fileExists', [ String ]) { String args ->
      false
    }
    helper.registerAllowedMethod("withOpenShiftCluster", [String, String, Closure], { String apiUrl, String credentialsId, Closure block ->
      block()
    })
    def deploymentInfo = script.call(context, [environmentsConfigMap: 'ods-envs'])

    then:
    printCallStack()
    assertJobStatusSuccess()
    deploymentInfo.local['Deployment/bar'][0].deploymentId == "bar-6f8db5fb69"
    deploymentInfo.second['Deployment/bar'][0].deploymentId == "bar-7f8db5fb69"
    deploymentInfo.third['Deployment/bar'][0].deploymentId == "bar-8f8db5fb69"
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
    deploymentInfo.local['DeploymentConfig/bar'][0].deploymentId == "bar-124"

    // test artifact URIS
    def buildArtifacts = context.getBuildArtifactURIs()
    buildArtifacts.size() > 0
    buildArtifacts.deployments.containsKey(config.componentId)
    buildArtifacts.deployments[config.componentId].deploymentId == deploymentInfo.local['DeploymentConfig/bar'][0].deploymentId

    1 * openShiftService.tailorApply(
      'foo-dev',
      [selector: 'app=foo-bar', exclude: 'bc,is'],
      '',
      [],
      [],
      '/tmp/file',
      false
    )
  }

  def "run successfully with Helm"() {
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
      args == 'chart/Chart.yaml'
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

    1 * openShiftService.helmUpgrade('foo-dev', 'bar', [], [imageTag: 'cd3e9082'], ['--install', '--atomic'], [], true)
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
    script.call(context)

    then:
    printCallStack()
    assertCallStackContains("Deployment resources cannot be used in the orchestration pipeline yet")
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

  def "fail when triggered by orchestration and environmentsConfigMap is given"() {
    given:
    def c = config + [
      environment: 'dev',
      openshiftRolloutTimeoutRetries: 5,
      triggeredByOrchestrationPipeline: true
    ]
    IContext context = new Context(null, c, logger)

    when:
    def script = loadScript('vars/odsComponentStageRolloutOpenShiftDeployment.groovy')
    script.call(context, [environmentsConfigMap: 'foo'])

    then:
    printCallStack()
    assertCallStackContains("Environment configuration via 'environmentsConfigMap' is not supported in the orchestration pipeline yet")
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

}
