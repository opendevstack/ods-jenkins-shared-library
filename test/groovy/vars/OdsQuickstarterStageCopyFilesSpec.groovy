package vars

import org.ods.quickstarter.Context
import org.ods.quickstarter.IContext
import vars.test_helper.PipelineSpockTestBase
import spock.lang.*

class OdsQuickstarterStageCopyFilesSpec extends PipelineSpockTestBase {
 
  def "run successfully"() {
    given:
    def config = [
        sourceDir: 'be-golang-plain',
        targetDir: 'out'
    ]
    IContext context = new Context(config)

    when:
    def script = loadScript('vars/odsQuickstarterStageCopyFiles.groovy')
    script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
  }

}
