package vars

import org.ods.component.Logger
import org.ods.component.Context
import org.ods.component.IContext
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import vars.test_helper.PipelineSpockTestBase
import spock.lang.*

class OdsComponentStageBuildOpenShiftImageSpec extends PipelineSpockTestBase {

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
      "componentId" : 'test',
  ]

  def "run successfully"() {
    given:
    def c = config + [environment: 'dev']
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Stub(OpenShiftService.class)
    openShiftService.startAndFollowBuild(_, _) >> 'foo-123'
    openShiftService.getLastBuildVersion(_) >> '123'
    openShiftService.getBuildStatus(_) >> 'complete'
    openShiftService.getImageReference(_, _) >> '0daecc05'
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)

    when:
    def script = loadScript('vars/odsComponentStageBuildOpenShiftImage.groovy')
    String fileContent
    helper.registerAllowedMethod("writeFile", [ Map ]) { Map args -> fileContent = args.text }
    def buildInfo = script.call(context)

    then:
    printCallStack()
    assertCallStackContains('Build #123 has produced image: 0daecc05.')
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
    buildInfo.buildId == "${config.componentId}-123"
    buildInfo.image == "0daecc05"
    
    // test artifact URIS
    def buildArtifacts = context.getBuildArtifactURIs()
    buildArtifacts.size() > 0
    // [builds:[test:[buildId:test-123, image:0daecc05]], deployments:[:]
    buildArtifacts.builds.containsKey (config.componentId)
    buildArtifacts.builds[config.componentId].buildId == buildInfo.buildId
    buildArtifacts.builds[config.componentId].image == buildInfo.image
  }

  def "run successfully with overwrite component and image labels"() {
    given:
    def c = config + [environment: 'dev', "globalExtensionImageLabels" : [ "globalext": "extG" ]]
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Stub(OpenShiftService.class)
    openShiftService.startAndFollowBuild(_, _) >> 'foo-123'
    openShiftService.getLastBuildVersion(_) >> '123'
    openShiftService.getBuildStatus(_) >> 'complete'
    openShiftService.getImageReference(_, _) >> '0daecc05'
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)

    def configOverwrite = ["componentId" : "overwrite", "imageLabels" : [ "testLabelOnBuild" : "buildLabel"]]
    when:
    def script = loadScript('vars/odsComponentStageBuildOpenShiftImage.groovy')
    String fileContent
    helper.registerAllowedMethod("writeFile", [ Map ]) { Map args -> fileContent = args.text }
    def buildInfo = script.call(context, configOverwrite)

    then:
    printCallStack()
    assertCallStackContains('Build #123 has produced image: 0daecc05.')
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
    buildInfo.buildId == "${configOverwrite.componentId}-123"
    buildInfo.image == "0daecc05"
    
    // test artifact URIS
    def buildArtifacts = context.getBuildArtifactURIs()
    buildArtifacts.size() > 0
    // [builds:[test:[buildId:test-123, image:0daecc05]], deployments:[:]
    buildArtifacts.builds.containsKey (configOverwrite.componentId)
    buildArtifacts.builds[configOverwrite.componentId].buildId == buildInfo.buildId
    buildArtifacts.builds[configOverwrite.componentId].image == buildInfo.image
  }

  @Unroll
  def "fails when build info cannot be retrieved"() {
    given:
    def c = config + [environment: 'dev']
    IContext context = new Context(null, c, logger)
    OpenShiftService openShiftService = Stub(OpenShiftService.class)
    openShiftService.startAndFollowBuild(_, _) >> startBuildOutput
    openShiftService.getLastBuildVersion(_) >> lastBuildVersion
    openShiftService.getBuildStatus(_) >> buildStatus
    openShiftService.getImageReference() >> imageReference
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)

    when:
    def script = loadScript('vars/odsComponentStageBuildOpenShiftImage.groovy')
    helper.registerAllowedMethod("writeFile", [ Map ]) { }
    script.call(context)

    then:
    printCallStack()
    assertCallStackContains(errorMessage)
    assertJobStatusFailure()

    where:
    startBuildOutput        | lastBuildVersion | buildStatus | imageReference || errorMessage
    'Build foo-123 started' | ''               | 'complete'  | '0daecc05'     || 'Could not get last version of BuildConfig'
    'Build foo-123 started' | '123'            | 'running'   | '0daecc05'     || 'OpenShift Build #123 was not successful'
  }

  def "skip when no environment given"() {
    given:
    def config = [environment: null]
    def context = new Context(null, config, logger)

    when:
    def script = loadScript('vars/odsComponentStageBuildOpenShiftImage.groovy')
    script.call(context)

    then:
    printCallStack()
    assertCallStackContains("Skipping for empty environment ...")
    assertJobStatusSuccess()
  }

}
