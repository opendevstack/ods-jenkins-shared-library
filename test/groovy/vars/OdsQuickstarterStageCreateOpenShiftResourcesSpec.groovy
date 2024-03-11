package vars

import org.ods.quickstarter.Context
import org.ods.quickstarter.IContext
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import util.PipelineSteps
import vars.test_helper.PipelineSpockTestBase
import spock.lang.*

class OdsQuickstarterStageCreateOpenShiftResourcesSpec extends PipelineSpockTestBase {

  private Logger logger = new Logger (new PipelineSteps(), true)

  def "run successfully"() {
    given:
    def config = [
        sourceDir: 'be-golang-plain',
        targetDir: 'out',
        projectId: 'foo',
        componentId: 'bar'
    ]
    IContext context = new Context(null, config, logger)
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
    helper.registerAllowedMethod('sh', [ Map ]) { Map args ->
        if (args.label ==~ /Getting all .* names for selector 'app=foo-bar'/) {
            return 'DeploymentConfig:foo Deployment:bar '
        }
    }

    when:
    def script = loadScript('vars/odsQuickstarterStageCreateOpenShiftResources.groovy')
    script.call(context)

    then:
    printCallStack()
    assertJobStatusSuccess()
  }

}
