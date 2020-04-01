package vars

import org.ods.component.Logger
import org.ods.component.Context
import vars.test_helper.PipelineSpockTestBase

class StageUploadToNexusSpec extends PipelineSpockTestBase {

  private Logger logger = Mock(Logger)

  def "run successfully"() {
    given:
    def config = [
        componentId  : 'componentId',
        tagversion   : 'tagversion',
        nexusUsername: 'nexusUsername',
        nexusPassword: 'nexusPassword',
        nexusHost    : 'host',
        groupId      : 'groupId'
    ]
    def context = new Context(null, config, logger)

    when:
    def script = loadScript('vars/stageUploadToNexus.groovy')
    script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
  }

}
