package vars

import org.ods.quickstarter.Context
import org.ods.quickstarter.IContext
import vars.test_helper.PipelineSpockTestBase
import spock.lang.*

class OdsQuickstarterStageRenderJenkinsfileSpec extends PipelineSpockTestBase {

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
    IContext context = new Context(config)

    when:
    def script = loadScript('vars/odsQuickstarterStageRenderJenkinsfile.groovy')
    script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
  }

}
