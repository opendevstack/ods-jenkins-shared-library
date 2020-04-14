package vars

import org.ods.quickstarter.Context
import org.ods.quickstarter.IContext
import vars.test_helper.PipelineSpockTestBase
import spock.lang.*

class OdsQuickstarterStageCreateOpenShiftResourcesSpec extends PipelineSpockTestBase {

  def "run successfully"() {
    given:
    def config = [
        sourceDir: 'be-golang-plain',
        targetDir: 'out',
        projectId: 'foo',
        componentId: 'bar'
    ]
    IContext context = new Context(config)

    when:
    def script = loadScript('vars/odsQuickstarterStageCreateOpenShiftResources.groovy')
    helper.registerAllowedMethod("fileExists", [ String ]) { true }
    script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
  }

}
