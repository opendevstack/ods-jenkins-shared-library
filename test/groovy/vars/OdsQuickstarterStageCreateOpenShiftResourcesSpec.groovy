package vars

import org.ods.quickstarter.Context
import org.ods.quickstarter.IContext
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import util.PipelineSteps
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
    helper.registerAllowedMethod('fileExists', [ String ]) { String file ->
        file == "${config.sourceDir}/ocp.env" || file == "${config.componentId}/metadata.yml"
    }
    helper.registerAllowedMethod('readYaml', [ Map ]) { Map args ->
        def testSteps = new PipelineSteps()
        testSteps.readYaml(args) { file ->
            def metadata = null
            if (file == "${config.componentId}/metadata.yml") {
                metadata = [
                    name:        'Name',
                    description: 'Description',
                    supplier:    'none',
                    version:     '1.0',
                    type:        'ods',
                ]
            }
            return metadata
        }
    }
    def openShiftService = Stub(OpenShiftService)
    openShiftService.getResourcesForComponent('foo-dev', ['dc', 'deploy'], 'app=foo-bar') >> [DeploymentConfig: ['foo'], Deployment: ['bar']]
    ServiceRegistry.instance.add(OpenShiftService, openShiftService)

    when:
    def script = loadScript('vars/odsQuickstarterStageCreateOpenShiftResources.groovy')
    script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
  }

}
