package vars

import org.ods.component.Context
import org.ods.services.NexusService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase

class OdsComponentStageUploadToNexusSpec extends PipelineSpockTestBase {

  private Logger logger = Mock(Logger)

  def "run successfully _ maven artifact"() {
    def config = [
      componentId  : 'componentId',
      tagversion   : 'tagversion',
      groupId      : 'groupId',
      nexusUsername: 'nexusUsername',
      nexusPassword: 'nexusPassword',
      nexusHost    : 'host',
    ]

    Map expectedNexusArgs = [
      'maven2.groupId' : config.groupId,
      'maven2.artifactId' : config.componentId,
      'maven2.version' : config.tagversion,
      'maven2.asset1.extension' : 'gz'
    ]

    given:
    def context = new Context(null, config, logger)
    NexusService nexus = Mock(NexusService.class)
    ServiceRegistry.instance.add(NexusService, nexus)

    when:
    def script = loadScript('vars/odsComponentStageUploadToNexus.groovy')
    helper.registerAllowedMethod('fileExists', [ String ]) { String args -> 
      true
    }
    
    helper.registerAllowedMethod('readFile', [ Map ]) { Map args ->
      'abc'
    }

    def uploadUrl = script.call(context)

    then:
    1 * nexus.storeComplextArtifact('candidates', _, 
        'application/zip', 'maven2', expectedNexusArgs)
    
    then:
    printCallStack()
    assertJobStatusSuccess()
  }

  def "run successfully with overrides _ maven artifact"() {
    def config = [
      componentId  : 'componentId',
      tagversion   : 'tagversion',
      groupId      : 'groupIdOverRide',
      distributionFile : "somedistfile.xx",
      repository : 'releases',
      nexusUsername: 'nexusUsername',
      nexusPassword: 'nexusPassword',
      nexusHost    : 'host',
    ]

    Map expectedNexusArgs = [
      'maven2.groupId' : config.groupId,
      'maven2.artifactId' : config.componentId,
      'maven2.version' : config.tagversion,
      'maven2.asset1.extension' : 'xx'
    ]

    String filePayload = 'abc'

    given:
    def context = new Context(null, config, logger)
    NexusService nexus = Mock(NexusService.class)
    ServiceRegistry.instance.add(NexusService, nexus)

    when:
    def script = loadScript('vars/odsComponentStageUploadToNexus.groovy')
    helper.registerAllowedMethod('fileExists', [ String ]) { String args ->
      true
    }
    helper.registerAllowedMethod('readFile', [ Map ]) { Map args ->
      filePayload
    }
    def uploadUrl = script.call(context, config)

    then:
    1 * nexus.storeComplextArtifact (config.repository, _, 
        'application/zip', 'maven2', expectedNexusArgs)
    printCallStack()
    assertJobStatusSuccess()
  }

  def "run successfully with overrides _ raw artifact"() {
    def config = [
      distributionFile : "somedistfile.xx",
      repositoryType : 'raw',
      repository : 'releases',
      nexusUsername: 'nexusUsername',
      nexusPassword: 'nexusPassword',
      nexusHost    : 'host',
    ]

    Map expectedNexusArgs = [
       'raw.asset1.filename' : config.distributionFile,
       'raw.directory' : null, /*test issue, projectId not set*/
    ]

    String filePayload = 'abc'

    given:
    def context = new Context(null, config, logger)
    NexusService nexus = Mock(NexusService.class)
    ServiceRegistry.instance.add(NexusService, nexus)

    when:
    def script = loadScript('vars/odsComponentStageUploadToNexus.groovy')
    helper.registerAllowedMethod('fileExists', [ String ]) { String args ->
      true
    }
    helper.registerAllowedMethod('readFile', [ Map ]) { Map args ->
      filePayload
    }
    def uploadUrl = script.call(context, config)

    then:
    1 * nexus.storeComplextArtifact (config.repository, _,
        'application/zip', config.repositoryType, expectedNexusArgs)
    printCallStack()
    assertJobStatusSuccess()
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
    NexusService nexus = Mock(NexusService.class)
    ServiceRegistry.instance.add(NexusService, nexus)

    when:
    def script = loadScript('vars/odsComponentStageUploadToNexus.groovy')
    helper.registerAllowedMethod('fileExists', [ String ]) { String args -> 
      false
    }
    helper.registerAllowedMethod('readFile', [ Map ]) { Map args ->
      'abc'
    }

    def uploadUrl = script.call(context)

    then:
    printCallStack()
    assertJobStatusFailure ()
    assertCallStackContains("Could not upload file componentId-tagversion.tar.gz - it does NOT exist!")
  }

}
