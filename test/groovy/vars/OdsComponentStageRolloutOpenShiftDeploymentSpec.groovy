package vars

import org.ods.component.Logger
import org.ods.component.Context
import org.ods.component.IContext
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
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

  def "run successfully"() {
    given:
    def c = config + [environment: 'dev']
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Stub(OpenShiftService.class)
    openShiftService.resourceExists(*_) >> true
    openShiftService.automaticImageChangeTriggerEnabled(*_) >> true
    openShiftService.getLatestVersion(*_) >> '123'
    openShiftService.getRolloutStatus(*_) >> 'complete'
    openShiftService.getPodDataForDeployment(*_) >> [ "deploymentId": "${config.componentId}-123" ]
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)

    when:
    def script = loadScript('vars/odsComponentStageRolloutOpenShiftDeployment.groovy')
    def deploymentInfo = script.call(context)

    then:
    printCallStack()
    assertCallStackContains('Deployment #123 successfully rolled out.')
    assertJobStatusSuccess()
    deploymentInfo.deploymentId == "${config.componentId}-123"
    
    // test artifact URIS
    def buildArtifacts = context.getBuildArtifactURIs()
    buildArtifacts.size() > 0
    // 
    buildArtifacts.deployments.containsKey (config.componentId)
    buildArtifacts.deployments[config.componentId].deploymentId == deploymentInfo.deploymentId
  }

  @Unroll
  def "fails when rollout info cannot be retrieved"() {
    given:
    def c = config + [environment: 'dev']
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Stub(OpenShiftService.class)
    openShiftService.resourceExists({ it == 'DeploymentConfig' }, _) >> dcExists
    openShiftService.resourceExists({ it == 'ImageStream' }, _) >> isExists
    openShiftService.automaticImageChangeTriggerEnabled(_) >> imageTrigger
    openShiftService.getLatestVersion(*_) >> latestVersion
    openShiftService.getRolloutStatus(*_) >> rolloutStatus
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)

    when:
    def script = loadScript('vars/odsComponentStageRolloutOpenShiftDeployment.groovy')
    script.call(context)

    then:
    printCallStack()
    assertCallStackContains(errorMessage)
    assertJobStatusFailure()

    where:
    dcExists | isExists | imageTrigger | latestVersion | rolloutStatus || errorMessage
    false    | true     | true         | '123'         | 'complete'    || "DeploymentConfig 'bar' does not exist."
    true     | false    | true         | '123'         | 'complete'    || "ImageStream 'bar' does not exist."
    true     | true     | true         | ''            | 'complete'    || "Could not get latest version of DeploymentConfig 'bar'."
    true     | true     | true         | '123'         | 'stopped'     || "Deployment #123 failed with status 'stopped', please check the error in the OpenShift web console."
  }

  def "skip when no environment given"() {
    given:
    def config = [environment: null]
    def context = new Context(null, config, logger)

    when:
    def script = loadScript('vars/odsComponentStageRolloutOpenShiftDeployment.groovy')
    script.call(context)

    then:
    printCallStack()
    assertCallStackContains("Skipping for empty environment ...")
    assertJobStatusSuccess()
  }

}
