package vars

import org.ods.quickstarter.Context
import org.ods.quickstarter.IContext
import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase
import util.PipelineSteps
import spock.lang.*

class OdsQuickstarterStageCopyFilesSpec extends PipelineSpockTestBase {

  private Logger logger = new Logger (new PipelineSteps(), true)
  
  def "run successfully"() {
    given:
    def config = [
        sourceDir: 'be-golang-plain',
        targetDir: 'out'
    ]
    IContext context = new Context(config, logger, null)

    when:
    def script = loadScript('vars/odsQuickstarterStageCopyFiles.groovy')
    script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
  }

}
