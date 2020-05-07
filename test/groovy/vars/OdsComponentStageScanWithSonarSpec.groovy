package vars

import org.ods.component.Context
import org.ods.component.IContext
import org.ods.services.BitbucketService
import org.ods.services.SonarQubeService
import org.ods.util.Logger
import org.ods.services.ServiceRegistry
import vars.test_helper.PipelineSpockTestBase
import spock.lang.*

class OdsComponentStageScanWithSonarSpec extends PipelineSpockTestBase {

  private Logger logger = Mock(Logger)

  @Shared
  def config = [
      bitbucketUrl: 'https://bitbucket.example.com',
      projectId: 'foo',
      componentId: 'bar',
      gitUrl: 'https://bitbucket.example.com/scm/foo/foo-bar.git',
      gitCommit: 'cd3e9082d7466942e1de86902bb9e663751dae8e',
      gitCommitMessage: """Foo\n\nSome "explanation".""",
      gitCommitAuthor: "John O'Hare",
      gitCommitTime: '2020-03-23 12:27:08 +0100',
      gitBranch: 'master',
      buildUrl: 'https://jenkins.example.com/job/foo-cd/job/foo-cd-bar-master/11/console',
      buildTime: '2020-03-23 12:27:08 +0100',
      odsSharedLibVersion: '2.x',
      branchToEnvironmentMapping: ['master': 'dev', 'release/': 'test'],
      sonarQubeBranch: 'master'
  ]

  def "run successfully"() {
    given:
    def c = config + [environment: 'dev']
    IContext context = new Context(null, c, logger)
    BitbucketService bitbucketService = Stub(BitbucketService.class)

    def res = readResource('no-pull-requests.json');
    bitbucketService.getPullRequests(*_) >> res
    ServiceRegistry.instance.add(BitbucketService, bitbucketService)
    SonarQubeService sonarQubeService = Stub(SonarQubeService.class)
    sonarQubeService.readProperties() >> ['sonar.projectKey': 'foo']
    sonarQubeService.scan(*_) >> null
    ServiceRegistry.instance.add(SonarQubeService, sonarQubeService)

    when:
    def script = loadScript('vars/odsComponentStageScanWithSonar.groovy')
    helper.registerAllowedMethod('archiveArtifacts', [ Map ]) { Map args -> }
    helper.registerAllowedMethod('stash', [ Map ]) { Map args -> }
    script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
  }

  def "run successfully with PR analysis"() {
    given:
    def c = config + [environment: 'dev', gitBranch: 'feature/foo']
    IContext context = new Context(null, c, logger)
    BitbucketService bitbucketService = Stub(BitbucketService.class)
    bitbucketService.withTokenCredentials(*_) >> { Closure block -> block('user', 's3cr3t') }

    def res = readResource('pull-requests.json');
    bitbucketService.getPullRequests(*_) >> res
    ServiceRegistry.instance.add(BitbucketService, bitbucketService)
    SonarQubeService sonarQubeService = Mock(SonarQubeService.class)
    sonarQubeService.readProperties() >> ['sonar.projectKey': 'foo']
    ServiceRegistry.instance.add(SonarQubeService, sonarQubeService)

    when:
    def script = loadScript('vars/odsComponentStageScanWithSonar.groovy')
    helper.registerAllowedMethod('archiveArtifacts', [ Map ]) { Map args -> }
    helper.registerAllowedMethod('stash', [ Map ]) { Map args -> }
    script.call(context, ['branch': '*'])

    then:
    printCallStack()
    1 * sonarQubeService.scan(
      ['sonar.projectKey': 'foo-bar', 'sonar.projectName': 'foo-bar'],
      'cd3e9082d7466942e1de86902bb9e663751dae8e',
      [
        bitbucketUrl: 'https://bitbucket.example.com',
        bitbucketToken: 's3cr3t',
        bitbucketProject: 'foo',
        bitbucketRepository: 'foo-bar',
        bitbucketPullRequestKey: 1,
        branch: 'feature/foo',
        baseBranch: 'master'
      ],
      false
    )
    assertJobStatusSuccess()
  }

  @Unroll
  def "checks quality gate"() {
    given:
    def c = config + [sonarQubeBranch: '*', debug: false, componentId: 'bar', buildNumber: '42']
    IContext context = new Context(null, c, logger)
    SonarQubeService sonarQubeService = Stub(SonarQubeService.class)
    sonarQubeService.readProperties() >> ['sonar.projectKey': 'foo']
    sonarQubeService.scan(*_) >> null
    sonarQubeService.getQualityGateJSON(*_) >> """{"projectStatus": ${projectStatus}}"""
    ServiceRegistry.instance.add(SonarQubeService, sonarQubeService)

    when:
    def script = loadScript('vars/odsComponentStageScanWithSonar.groovy')
    helper.registerAllowedMethod('archiveArtifacts', [ Map ]) { Map args -> }
    helper.registerAllowedMethod('stash', [ Map ]) { Map args -> }
    helper.registerAllowedMethod("readJSON", [ Map ]) { Map args ->
      [projectStatus: [projectStatus: projectStatusKey]]
    }
    script.call(context, [requireQualityGatePass: true])

    then:
    printCallStack()
    assertCallStackContains(message)
    if (expectedToFail) {
      assertJobStatusFailure()
    } else {
      assertJobStatusSuccess()
    }

    where:
    projectStatus                      | projectStatusKey || expectedToFail | message
    """{"projectStatus": "ERROR"}"""   | 'ERROR'          || true           | 'Quality gate failed'
    """{"projectStatus": "SUCCESS"}""" | 'SUCCESS'        || false          | 'Quality gate passed'
    """{}"""                           | ''               || true          | 'Quality gate unknown'
  }

  def "skip branch should not be scanned"() {
    given:
    def config = [
      branchToEnvironmentMapping: ['master': 'dev', 'release/': 'test'],
      gitBranch: 'feature/foo'
    ]
    def context = new Context(null, config, logger)

    when:
    def script = loadScript('vars/odsComponentStageScanWithSonar.groovy')
    script.call(context, ['branch': 'master'])

    then:
    printCallStack()
    assertCallStackContains("Skipping as branch 'feature/foo' is not covered by the 'branch' option.")
    assertJobStatusSuccess()
  }

}
