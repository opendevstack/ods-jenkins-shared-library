package vars

import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import groovy.lang.MissingPropertyException
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
        'application/octet-stream', 'maven2', expectedNexusArgs)
    
    then:
    printCallStack()
    assertJobStatusSuccess()
  }

  def "run successfully with overrides _ maven artifact"() {
    def config = [
      componentId  : 'componentId',
      tagversion   : 'tagversion',
      nexusUsername: 'nexusUsername',
      nexusPassword: 'nexusPassword',
      nexusHost    : 'host',
    ]
    def opts = [
      groupId: 'groupIdOverRide',
      distributionFile: "somedistfile.xx",
      repository: 'releases',
    ]

    Map expectedNexusArgs = [
      'maven2.groupId' : opts.groupId,
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
    def uploadUrl = script.call(context, opts)

    then:
    1 * nexus.storeComplextArtifact(opts.repository, _,
        'application/octet-stream', 'maven2', expectedNexusArgs)
    printCallStack()
    assertJobStatusSuccess()
  }

  def "run successfully with overrides _ raw artifact"() {
    def config = [
      nexusUsername: 'nexusUsername',
      nexusPassword: 'nexusPassword',
      nexusHost    : 'host',
    ]
    def opts = [
      distributionFile : "somedistfile.xx",
      repositoryType : 'raw',
      repository : 'releases',
    ]

    Map expectedNexusArgs = [
       'raw.asset1.filename' : opts.distributionFile,
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
    def uploadUrl = script.call(context, opts)

    then:
    1 * nexus.storeComplextArtifact(opts.repository, _,
        'application/octet-stream', opts.repositoryType, expectedNexusArgs)
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

  def "fails on incorrect options"() {
    given:
    def config = [
      environment: null,
      gitBranch: 'master',
      gitCommit: 'cd3e9082d7466942e1de86902bb9e663751dae8e',
      branchToEnvironmentMapping: [:]
    ]
    def context = new Context(null, config, logger)

    when:
    def script = loadScript('vars/odsComponentStageUploadToNexus.groovy')
    script.call(context, options)

    then:
    def exception = thrown(wantEx)
    exception.message == wantExMessage

    where:
    options           || wantEx                   | wantExMessage
    [branches: 'abc'] || GroovyCastException      | "Cannot cast object 'abc' with class 'java.lang.String' to class 'java.util.List'"
    [foobar: 'abc']   || MissingPropertyException | "No such property: foobar for class: org.ods.component.UploadToNexusOptions"
  }

}
