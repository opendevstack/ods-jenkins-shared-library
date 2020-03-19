package vars

import org.ods.Logger
import org.ods.OdsContext
import org.ods.build_service.OpenShiftService
import org.ods.build_service.ServiceRegistry
import vars.test_helper.PipelineSpockTestBase

class StageOpenShiftBuildSpec extends PipelineSpockTestBase {

  private Logger logger = Mock(Logger)

  def "run successfully"() {
    given:
    def config = [
        environment: 'environment',
    ]
    def context = new OdsContext(null, config, logger)
    OpenShiftService openShiftService = Stub(OpenShiftService.class)
    openShiftService.buildImage([], []) >> 'foo-123'
    openShiftService.getCurrentImageSha() >> '123'
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)

    when:
    def script = loadScript('vars/stageStartOpenshiftBuild.groovy')
    script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
  }

  def "skip due to no environment set"() {
    given:
    def config = [
        environment: null,
    ]
    def context = new OdsContext(null, config, logger)

    when:
    def script = loadScript('vars/stageStartOpenshiftBuild.groovy')
    script.call(context)

    then:
    printCallStack()
    assertCallStackContains("Skipping for empty environment ...")
    assertJobStatusSuccess()
  }

}
