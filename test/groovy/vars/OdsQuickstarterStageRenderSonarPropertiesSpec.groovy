package vars

import org.ods.quickstarter.Context
import org.ods.quickstarter.IContext
import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase
import util.PipelineSteps
import spock.lang.*

class OdsQuickstarterStageRenderSonarPropertiesSpec extends PipelineSpockTestBase {

  private Logger logger = new Logger (new PipelineSteps(), true)

  def "run successfully"() {
    given:
    def config = [
        projectId: 'foo',
        componentId: 'bar',
        sourceDir: 'be-golang-plain',
        targetDir: 'out',
        gitUrlHttp: 'https://bitbucket.example.com/scm/foo/bar.git',
        odsImageTag: '2.x',
        odsGitRef: '2.x'
    ]
    IContext context = new Context(null, config, logger)

    when:
    def script = loadScript('vars/odsQuickstarterStageRenderSonarProperties.groovy')
    script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
  }

}
