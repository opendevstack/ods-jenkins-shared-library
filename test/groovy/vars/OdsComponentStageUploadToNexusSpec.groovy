package vars

import org.ods.component.Logger
import org.ods.component.Context
import vars.test_helper.PipelineSpockTestBase

class OdsComponentStageUploadToNexusSpec extends PipelineSpockTestBase {

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
    def script = loadScript('vars/odsComponentStageUploadToNexus.groovy')
    def uploadUrl = script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
    uploadUrl && uploadUrl.contains(config.componentId) && uploadUrl.contains("candidates")
  }

  def "run successfully with overrides"() {
    given:
    def config = [
        componentId  : 'componentId',
        tagversion   : 'tagversion',
        nexusUsername: 'nexusUsername',
        nexusPassword: 'nexusPassword',
        nexusHost    : 'host',
        groupId      : 'groupIdOverRide',
        distributionFile : "somedistfile.xx",
        repoType : 'releases'
    ]
    def context = new Context(null, config, logger)

    when:
    def script = loadScript('vars/odsComponentStageUploadToNexus.groovy')
    def uploadUrl = script.call(context, config)

    then:
    printCallStack()
    assertJobStatusSuccess()
    uploadUrl && uploadUrl.contains(config.distributionFile) && uploadUrl.contains(config.repoType) && uploadUrl.contains(config.groupId) 
  }

}
