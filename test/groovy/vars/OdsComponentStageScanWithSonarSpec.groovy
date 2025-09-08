package vars

import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.ods.component.Context
import org.ods.component.IContext
import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.services.SonarQubeService
import org.ods.util.Logger
import org.ods.services.ServiceRegistry
import vars.test_helper.PipelineSpockTestBase
import spock.lang.*

class OdsComponentStageScanWithSonarSpec extends PipelineSpockTestBase {

    def setupSpec() {
        System.setProperty("java.awt.headless", "true")
        org.ods.component.ScanWithSonarStage.metaClass.generateAndArchiveReportInNexus = { Map args -> null }
    }
    
    @Rule
    public TemporaryFolder tempFolder

  private Logger logger = Mock(Logger)

  @Shared
  def config = [
      bitbucketUrl: 'https://bitbucket.example.com',
      projectId: 'foo',
      componentId: 'bar',
      repoName: 'foo-bar',
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
      sonarQubeBranch: 'master',
      sonarQubeEdition: 'developer',
  ]

def "run successfully"() {
    given:
    def c = config + [environment: 'dev']
    IContext context = new Context(null, c, logger)
    BitbucketService bitbucketService = Stub(BitbucketService.class)
    bitbucketService.findPullRequest(*_) >> [:]
    ServiceRegistry.instance.add(BitbucketService, bitbucketService)
    NexusService nexusService = Mock(NexusService.class)
    ServiceRegistry.instance.add(NexusService, nexusService)
    SonarQubeService sonarQubeService = Stub(SonarQubeService.class)
    sonarQubeService.readProperties() >> ['sonar.projectKey': 'foo']
    sonarQubeService.readTask() >> ['ceTaskId': 'AXxaAoUSsjAMlIY9kNmn']
    sonarQubeService.scan(*_) >> null
    sonarQubeService.getQualityGateJSON(*_) >> '{"projectStatus":{"status":"OK"}}'
    sonarQubeService.getComputeEngineTaskResult(*_) >> 'SUCCESS'
    sonarQubeService.getSonarQubeHostUrl() >> "https://sonarqube.example.com"
    ServiceRegistry.instance.add(SonarQubeService, sonarQubeService)
    when:
    def script = loadScript('vars/odsComponentStageScanWithSonar.groovy')
    script.env.WORKSPACE = tempFolder.getRoot().absolutePath
    helper.registerAllowedMethod('archiveArtifacts', [ Map ]) { Map args -> }
    helper.registerAllowedMethod('stash', [ Map ]) { Map args -> }
    helper.registerAllowedMethod('readFile', [ Map ]) { Map args -> ""}
    helper.registerAllowedMethod('sh', [Map]) { Map args -> '{"data": {"sonar.host.url": "https://sonarqube.example.com"}}' }
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
    bitbucketService.findPullRequest(*_) >> [key: 1, base: 'master']
    ServiceRegistry.instance.add(BitbucketService, bitbucketService)
    NexusService nexusService = Mock(NexusService.class)
    ServiceRegistry.instance.add(NexusService, nexusService)
    SonarQubeService sonarQubeService = Mock(SonarQubeService.class)
    sonarQubeService.readProperties() >> ['sonar.projectKey': 'foo']
    sonarQubeService.readTask() >> ['ceTaskId': 'AXxaAoUSsjAMlIY9kNmn']
    sonarQubeService.getQualityGateJSON(*_) >> '{"projectStatus":{"status":"OK"}}'
    sonarQubeService.getComputeEngineTaskResult(*_) >> 'SUCCESS'
    sonarQubeService.getSonarQubeHostUrl() >> "https://sonarqube.example.com"
    ServiceRegistry.instance.add(SonarQubeService, sonarQubeService)
    when:
    def script = loadScript('vars/odsComponentStageScanWithSonar.groovy')
    script.env.WORKSPACE = tempFolder.getRoot().absolutePath
    helper.registerAllowedMethod('archiveArtifacts', [ Map ]) { Map args -> }
    helper.registerAllowedMethod('stash', [ Map ]) { Map args -> }
    helper.registerAllowedMethod('readFile', [ Map ]) { Map args ->""}
    helper.registerAllowedMethod('sh', [Map]) { Map args -> '{"data": {"sonar.host.url": "https://sonarqube.example.com"}}' }
    script.call(context, ['branch': '*', 'analyzePullRequests': 'true'])

    then:
    printCallStack()
    1 * sonarQubeService.scan({
      it.properties == [
        'sonar.projectKey': 'foo-bar',
        'sonar.projectName': 'foo-bar',
        'sonar.branch.name': 'feature/foo'
      ] &&
      it.gitCommit == 'cd3e9082d7466942e1de86902bb9e663751dae8e' &&
      it.pullRequestInfo == [
        bitbucketUrl: 'https://bitbucket.example.com',
        bitbucketToken: 's3cr3t',
        bitbucketProject: 'foo',
        bitbucketRepository: 'foo-bar',
        bitbucketPullRequestKey: 1,
        branch: 'feature/foo',
        baseBranch: 'master'
      ] &&
      it.sonarQubeEdition == 'developer' &&
      it.exclusions == ''
    })
    assertJobStatusSuccess()
  }

  @Unroll
  def "checks quality gate"() {
    given:
    def c = config + [sonarQubeBranch: '*', debug: false, componentId: 'bar', buildNumber: '42']
    IContext context = new Context(null, c, logger)
    SonarQubeService sonarQubeService = Stub(SonarQubeService.class)
    sonarQubeService.readProperties() >> ['sonar.projectKey': 'foo']
    sonarQubeService.readTask() >> ['ceTaskId': 'AXxaAoUSsjAMlIY9kNmn']
    sonarQubeService.getComputeEngineTaskResult(*_) >> 'SUCCESS'
    sonarQubeService.scan(*_) >> null
    sonarQubeService.getQualityGateJSON(*_) >> """{"projectStatus": ${projectStatus}}"""
    ServiceRegistry.instance.add(SonarQubeService, sonarQubeService)
    BitbucketService bitbucketService = Stub(BitbucketService.class)
    bitbucketService.createCodeInsightReport(*_) >> null
    ServiceRegistry.instance.add(BitbucketService, bitbucketService)
    NexusService nexusService = Mock(NexusService.class)
    ServiceRegistry.instance.add(NexusService, nexusService)
    when:
    def script = loadScript('vars/odsComponentStageScanWithSonar.groovy')
    script.env.WORKSPACE = tempFolder.getRoot().absolutePath
    helper.registerAllowedMethod('archiveArtifacts', [ Map ]) { Map args -> }
    helper.registerAllowedMethod('stash', [ Map ]) { Map args -> }
    helper.registerAllowedMethod("readJSON", [ Map ]) { Map args ->
      [projectStatus: [status: projectStatusKey]]
    }
    helper.registerAllowedMethod('readFile', [ Map ]) { Map args -> ""}
    helper.registerAllowedMethod('sh', [Map]) { Map args -> '{"data": {"sonar.host.url": "https://sonarqube.example.com"}}' }
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
    """{}"""                           | ''               || true           | 'Quality gate unknown'
  }

  def "skip branch should not be scanned"() {
    given:
    def config = [
      branchToEnvironmentMapping: ['master': 'dev', 'release/': 'test'],
      gitBranch: 'feature/foo',
      nexusUrl: 'http://nexus'
    ]
    def context = new Context(null, config, logger)
    when:
    NexusService nexusService = Stub(NexusService.class)
    ServiceRegistry.instance.add(NexusService, nexusService)
    def script = loadScript('vars/odsComponentStageScanWithSonar.groovy')
    helper.registerAllowedMethod('sh', [Map]) { Map args -> '{"data": {"sonar.host.url": "https://sonarqube.example.com"}}' }
    script.call(context, ['branch': 'master'])

    then:
    printCallStack()
    assertCallStackContains("Skipping stage 'SonarQube Analysis' for branch 'feature/foo'")
    assertJobStatusSuccess()
  }

  def "fails on incorrect options"() {
    given:
    def config = [
      environment: null,
      gitBranch: 'master',
      gitCommit: 'cd3e9082d7466942e1de86902bb9e663751dae8e',
      branchToEnvironmentMapping: [:],
      nexusUrl: 'http://nexus'
    ]
    def context = new Context(null, config, logger)
    when:
    NexusService nexusService = Stub(NexusService.class)
    ServiceRegistry.instance.add(NexusService, nexusService)
    def script = loadScript('vars/odsComponentStageScanWithSonar.groovy')
    helper.registerAllowedMethod('sh', [Map]) { Map args -> '{"data": {"sonar.host.url": "https://sonarqube.example.com"}}' }
    script.call(context, options)

    then:
    printCallStack()
    assertCallStackContains("WARN: Error with SonarQube scan due to: ${wantExMessage}")
    assertJobStatusSuccess()

    where:
    options           || wantEx                   | wantExMessage
    [branches: 'abc'] || GroovyCastException      | "Cannot cast object 'abc' with class 'java.lang.String' to class 'java.util.List'"
    [foobar: 'abc']   || MissingPropertyException | "No such property: foobar for class: org.ods.component.ScanWithSonarOptions"
  }

}
