package vars

import org.ods.component.Context
import org.ods.component.IContext
import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
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
      componentId: 'bar'
  ]

  def "run successfully without Tailor"() {
    given:
    def c = config + [environment: 'dev', targetProject: 'foo-dev', openshiftRolloutTimeoutRetries: 6]
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Stub(OpenShiftService.class)
    openShiftService.resourceExists(*_) >> true
    openShiftService.getLatestVersion(*_) >> 123
    openShiftService.rollout(*_) >> "${config.componentId}-124"
    // test the handover of the poddata retries
    openShiftService.getPodDataForDeployment(_,6) >> [ deploymentId: "${config.componentId}-124" ]
    openShiftService.getImagesOfDeploymentConfig (*_) >> [[ repository: 'foo', name: 'bar' ]]
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
    deploymentInfo.deploymentId == "${config.componentId}-124"

    // test artifact URIS
    def buildArtifacts = context.getBuildArtifactURIs()
    buildArtifacts.size() > 0
    buildArtifacts.deployments.containsKey (config.componentId)
    buildArtifacts.deployments[config.componentId].deploymentId == deploymentInfo.deploymentId
  }

  def "run successfully with Tailor"() {
    given:
    def c = config + [environment: 'dev', targetProject: 'foo-dev', openshiftRolloutTimeoutRetries: 5]
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Mock(OpenShiftService.class)
    openShiftService.resourceExists(*_) >> true
    openShiftService.getLatestVersion(*_) >> 123
    openShiftService.rollout(*_) >> "${config.componentId}-124"
    openShiftService.getPodDataForDeployment(*_) >> [ deploymentId: "${config.componentId}-124" ]
    openShiftService.getImagesOfDeploymentConfig (*_) >> [[ repository: 'foo', name: 'bar' ]]
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)
    JenkinsService jenkinsService = Stub(JenkinsService.class)
    jenkinsService.maybeWithPrivateKeyCredentials(*_) >> { args -> args[1]('/tmp/file') }
    ServiceRegistry.instance.add(JenkinsService, jenkinsService)

    when:
    def script = loadScript('vars/odsComponentStageRolloutOpenShiftDeployment.groovy')
    helper.registerAllowedMethod('fileExists', [ String ]) { String args ->
      true
    }
    def deploymentInfo = script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
    deploymentInfo.deploymentId == "${config.componentId}-124"

    // test artifact URIS
    def buildArtifacts = context.getBuildArtifactURIs()
    buildArtifacts.size() > 0
    buildArtifacts.deployments.containsKey (config.componentId)
    buildArtifacts.deployments[config.componentId].deploymentId == deploymentInfo.deploymentId

    1 * openShiftService.tailorApply(
      [selector: 'app=foo-bar', exclude: 'bc,is'],
      '',
      [],
      [],
      '/tmp/file',
      false
    )
  }

  @Unroll
  def "fails when rollout info cannot be retrieved"() {
    given:
    def c = config + [environment: 'dev', targetProject: 'foo-dev', openshiftRolloutTimeoutRetries: 5]
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Stub(OpenShiftService.class)
    openShiftService.resourceExists({ it == 'DeploymentConfig' }, _) >> dcExists
    openShiftService.resourceExists({ it == 'ImageStream' }, _) >> isExists
    openShiftService.getImagesOfDeploymentConfig (*_) >> images
    openShiftService.getLatestVersion(*_) >> latestVersion
    openShiftService.rollout(*_) >> { x, y, z ->
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
    false    | true     | []                                     | 0             || "DeploymentConfig 'bar' does not exist."
    true     | false    | [[repository: 'foo-dev', name: 'baz']] | 0             || "The following ImageStream resources  for DeploymentConfig 'bar' do not exist: '[foo-dev/baz]'."
    true     | true     | [[repository: 'foo-dev', name: 'bar']] | 123           || "Boom!"
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
