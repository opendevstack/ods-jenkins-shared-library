package vars

import org.ods.component.Context
import org.ods.util.Logger
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
    helper.registerAllowedMethod('fileExists', [ String ]) { String args -> 
      true
    }
    
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
    helper.registerAllowedMethod('fileExists', [ String ]) { String args ->
      true
    }

    def uploadUrl = script.call(context, config)

    then:
    printCallStack()
    assertJobStatusSuccess()
    uploadUrl && uploadUrl.contains(config.distributionFile) && uploadUrl.contains(config.repoType) && uploadUrl.contains(config.groupId) 
  }

  def "fail missing file"() {
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
    helper.registerAllowedMethod('fileExists', [ String ]) { String args -> 
      false
    }
    
    def uploadUrl = script.call(context)

    then:
    printCallStack()
    assertJobStatusFailure ()
    assertCallStackContains("Could not upload file componentId-tagversion.tar.gz - it does NOT exist!")
    
  }
}
