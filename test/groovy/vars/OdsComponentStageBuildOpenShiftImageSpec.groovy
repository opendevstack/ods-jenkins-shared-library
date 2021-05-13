package vars

import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import groovy.lang.MissingPropertyException
import org.ods.component.Context
import org.ods.component.IContext
import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase
import util.PipelineSteps
import spock.lang.*

class OdsComponentStageBuildOpenShiftImageSpec extends PipelineSpockTestBase {

  private Logger logger = new Logger (new PipelineSteps(), true)

  @Shared
  def config = [
      gitUrl: 'https://example.com/scm/foo/bar.git',
      gitCommit: 'cd3e9082d7466942e1de86902bb9e663751dae8e',
      gitCommitMessage: """Foo\n\nSome "explanation".""",
      gitCommitAuthor: "John O'Hare",
      gitCommitTime: '2020-03-23 12:27:08 +0100',
      gitBranch: 'master',
      branchToEnvironmentMapping: ['master': 'dev'],
      buildUrl: 'https://jenkins.example.com/job/foo-cd/job/foo-cd-bar-master/11/console',
      buildTime: '2020-03-23 12:27:08 +0100',
      odsSharedLibVersion: '2.x',
      projectId: 'foo',
      componentId: 'bar'
  ]

  def "run successfully"() {
    given:
    def c = config + [environment: 'dev', targetProject: 'foo-dev']
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Mock(OpenShiftService.class)
    openShiftService.resourceExists(*_) >> true
    openShiftService.startBuild(*_) >> 123
    openShiftService.getLastBuildVersion(*_) >> 123
    openShiftService.getBuildStatus(*_) >> 'complete'
    openShiftService.getImageReference(*_) >> '0daecc05'
    
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)

    when:
    def script = loadScript('vars/odsComponentStageBuildOpenShiftImage.groovy')
    String fileContent
    helper.registerAllowedMethod("writeFile", [ Map ]) { Map args -> fileContent = args.text }
    helper.registerAllowedMethod('fileExists', [ String ]) { String args ->
      false
    }
    helper.registerAllowedMethod('echo', [ String ]) {String args -> }
    def buildInfo = script.call(context)

    then:
    printCallStack()
    assertCallStackContains('''Build #123 of 'bar' has produced image: 0daecc05.''')
    assertJobStatusSuccess()
    def expectedFileContent = """[
{"name": "ods.build.source.repo.url", "value": "https://example.com/scm/foo/bar.git"},
{"name": "ods.build.source.repo.commit.sha", "value": "cd3e9082d7466942e1de86902bb9e663751dae8e"},
{"name": "ods.build.source.repo.commit.msg", "value": "Foo Some explanation."},
{"name": "ods.build.source.repo.commit.author", "value": "John OHare"},
{"name": "ods.build.source.repo.commit.timestamp", "value": "2020-03-23 12:27:08 +0100"},
{"name": "ods.build.source.repo.branch", "value": "master"},
{"name": "ods.build.jenkins.job.url", "value": "https://jenkins.example.com/job/foo-cd/job/foo-cd-bar-master/11/console"},
{"name": "ods.build.timestamp", "value": "2020-03-23 12:27:08 +0100"},
{"name": "ods.build.lib.version", "value": "2.x"}
]"""
    fileContent == expectedFileContent
    // test immediate return
    buildInfo.buildId == 'bar-123'
    buildInfo.image == "0daecc05"
    
    // test artifact URIS
    def buildArtifacts = context.getBuildArtifactURIs()
    buildArtifacts.size() > 0
    // [builds:[bar:[buildId:bar-123, image:0daecc05]], deployments:[:]
    buildArtifacts.builds.containsKey('bar')
    buildArtifacts.builds.bar.buildId == buildInfo.buildId
    buildArtifacts.builds.bar.image == buildInfo.image
  }

  def "run successfully with overwrite component and image labels"() {
    given:
    def c = config + [
      environment: 'dev',
      targetProject: 'foo-dev',
      globalExtensionImageLabels: [ "globalext": "extG" ]
    ]
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Stub(OpenShiftService.class)
    openShiftService.resourceExists(*_) >> true
    openShiftService.startBuild(*_) >> 123
    openShiftService.getLastBuildVersion(*_) >> 123
    openShiftService.getBuildStatus(*_) >> 'complete'
    openShiftService.getImageReference(*_) >> '0daecc05'
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)

    def configOverwrite = [
      resourceName: 'overwrite',
      imageLabels: [testLabelOnBuild: 'buildLabel'],
    ]
    when:
    def script = loadScript('vars/odsComponentStageBuildOpenShiftImage.groovy')
    String fileContent
    helper.registerAllowedMethod("writeFile", [ Map ]) { Map args -> fileContent = args.text }
    helper.registerAllowedMethod('fileExists', [ String ]) { String args ->
      false
    }
    def buildInfo = script.call(context, configOverwrite)

    then:
    printCallStack()
    assertCallStackContains('''Build #123 of 'overwrite' has produced image: 0daecc05.''')
    assertJobStatusSuccess()
    def expectedFileContent = """[
{"name": "ods.build.source.repo.url", "value": "https://example.com/scm/foo/bar.git"},
{"name": "ods.build.source.repo.commit.sha", "value": "cd3e9082d7466942e1de86902bb9e663751dae8e"},
{"name": "ods.build.source.repo.commit.msg", "value": "Foo Some explanation."},
{"name": "ods.build.source.repo.commit.author", "value": "John OHare"},
{"name": "ods.build.source.repo.commit.timestamp", "value": "2020-03-23 12:27:08 +0100"},
{"name": "ods.build.source.repo.branch", "value": "master"},
{"name": "ods.build.jenkins.job.url", "value": "https://jenkins.example.com/job/foo-cd/job/foo-cd-bar-master/11/console"},
{"name": "ods.build.timestamp", "value": "2020-03-23 12:27:08 +0100"},
{"name": "ods.build.lib.version", "value": "2.x"},
{"name": "ext.testLabelOnBuild", "value": "buildLabel"},
{"name": "ext.globalext", "value": "extG"}
]"""
    fileContent == expectedFileContent
    // test immediate return
    buildInfo.buildId == 'overwrite-123'
    buildInfo.image == "0daecc05"
    
    // test artifact URIS
    def buildArtifacts = context.getBuildArtifactURIs()
    buildArtifacts.size() > 0
    // [builds:[bar:[buildId:overwrite-123, image:0daecc05]], deployments:[:]
    buildArtifacts.builds.containsKey('overwrite')
    buildArtifacts.builds.overwrite.buildId == buildInfo.buildId
    buildArtifacts.builds.overwrite.image == buildInfo.image
  }

  def "run in orchestration pipeline regardless of branch config"() {
    given:
    def c = config + [
      environment: 'dev',
      targetProject: 'foo-dev',
      gitBranch: 'release/1',
      triggeredByOrchestrationPipeline: true
    ]
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Mock(OpenShiftService.class)
    openShiftService.resourceExists(*_) >> true
    openShiftService.startBuild(*_) >> 123
    openShiftService.getLastBuildVersion(*_) >> 123
    openShiftService.getBuildStatus(*_) >> 'complete'
    openShiftService.getImageReference(*_) >> '0daecc05'
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)

    when:
    def script = loadScript('vars/odsComponentStageBuildOpenShiftImage.groovy')
    helper.registerAllowedMethod("writeFile", [ Map ]) { Map args -> }
    helper.registerAllowedMethod('fileExists', [ String ]) { String args -> false }
    def buildInfo = script.call(context)

    then:
    printCallStack()
    assertCallStackContains('''Build #123 of 'bar' has produced image: 0daecc05.''')
    assertJobStatusSuccess()
  }

  @Unroll
  def "fails when build info cannot be retrieved"() {
    given:
    def c = config + [environment: 'dev']
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Stub(OpenShiftService.class)
    openShiftService.getLastBuildVersion(*_) >> lastBuildVersion
    openShiftService.startBuild(*_) >> lastBuildVersion
    openShiftService.getBuildStatus(*_) >> buildStatus
    openShiftService.getImageReference(*_) >> imageReference
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)

    when:
    def script = loadScript('vars/odsComponentStageBuildOpenShiftImage.groovy')
    helper.registerAllowedMethod("writeFile", [ Map ]) { }
    helper.registerAllowedMethod('fileExists', [ String ]) { String args ->
      false
    }
    script.call(context)

    then:
    printCallStack()
    assertCallStackContains(errorMessage)
    assertJobStatusFailure()

    where:
    startBuildOutput        | lastBuildVersion | buildStatus | imageReference || errorMessage
    'Build foo-123 started' | 123              | 'running'   | '0daecc05'     || 'OpenShift Build #123 was not successful'
  }

  def "skip when branch config does not cover current branch"() {
    given:
    def config = [
      environment: null,
      gitBranch: gitBranch,
      gitCommit: 'cd3e9082d7466942e1de86902bb9e663751dae8e',
      branchToEnvironmentMapping: branchToEnvironmentMapping
    ]
    def context = new Context(null, config, logger)

    when:
    def script = loadScript('vars/odsComponentStageBuildOpenShiftImage.groovy')
    if (branches != null) {
      script.call(context, [branches: branches])
    } else {
      script.call(context)
    }

    then:
    printCallStack()
    assertCallStackContains("Skipping stage 'Build OpenShift Image'")
    assertJobStatusSuccess()

    where:
    gitBranch | branchToEnvironmentMapping           | branches
    'develop' | [:]                                  | []
    'develop' | [:]                                  | ['master']
    'develop' | ['master':'dev']                     | null
    'develop' | ['master':'dev', 'release/': 'test'] | null
  }

  def "fails on incorrect options"() {
    given:
    def config = [
      environment: null,
      gitBranch: 'master',
      gitCommit: 'cd3e9082d7466942e1de86902bb9e663751dae8e',
      branchToEnvironmentMapping: [:]
    ]
    def context = new Context(null, config, logger)

    when:
    def script = loadScript('vars/odsComponentStageBuildOpenShiftImage.groovy')
    script.call(context, options)

    then:
    def exception = thrown(wantEx)
    exception.message == wantExMessage

    where:
    options           || wantEx                   | wantExMessage
    [branches: 'abc'] || GroovyCastException      | "Cannot cast object 'abc' with class 'java.lang.String' to class 'java.util.List'"
    [foobar: 'abc']   || MissingPropertyException | "No such property: foobar for class: org.ods.component.BuildOpenShiftImageOptions"
  }

}
