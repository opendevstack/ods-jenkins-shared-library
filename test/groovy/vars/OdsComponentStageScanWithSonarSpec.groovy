package vars

import org.ods.component.Logger
import org.ods.component.Context
import org.ods.component.IContext
import org.ods.services.SonarQubeService
import org.ods.services.ServiceRegistry
import vars.test_helper.PipelineSpockTestBase
import spock.lang.*

class OdsComponentStageScanWithSonarSpec extends PipelineSpockTestBase {

  private Logger logger = Mock(Logger)

  @Shared
  def config = [
      gitUrl: 'https://example.com/scm/foo/bar.git',
      gitCommit: 'cd3e9082d7466942e1de86902bb9e663751dae8e',
      gitCommitMessage: """Foo\n\nSome "explanation".""",
      gitCommitAuthor: "John O'Hare",
      gitCommitTime: '2020-03-23 12:27:08 +0100',
      gitBranch: 'master',
      buildUrl: 'https://jenkins.example.com/job/foo-cd/job/foo-cd-bar-master/11/console',
      buildTime: '2020-03-23 12:27:08 +0100',
      odsSharedLibVersion: '2.x',
  ]

  def "run successfully"() {
    given:
    def c = config + [environment: 'dev']
    IContext context = new Context(null, c, logger)
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
    def config = [sonarQubeBranch: 'master', gitBranch: 'feature/foo']
    def context = new Context(null, config, logger)

    when:
    def script = loadScript('vars/odsComponentStageScanWithSonar.groovy')
    script.call(context)

    then:
    printCallStack()
    assertCallStackContains("Skipping as branch 'feature/foo' is not covered by the 'sonarQubeBranch' property.")
    assertJobStatusSuccess()
  }

}
