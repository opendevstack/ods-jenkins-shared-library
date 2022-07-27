package vars


import org.ods.component.Context
import org.ods.component.IContext
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase

class OdsComponentStageCopyImageSpec extends PipelineSpockTestBase {

    private Logger logger = Mock(Logger)

    def "run copy image command"() {
        given:
        def cfg = [
            sourceImageUrlIncludingRegistry: 'example.com/repo/image:1f3d1ed76c304893cb709c9b65a64b2cf2e4676b607c6be737c1dddd1cf89065',
        ]
        def ctxCfg = [
            cdProject     : 'project-cd',
            componentId   : 'example-component',
            dockerRegistry: 'internal-registry',
            branch        : 'master',

        ]
        IContext context = new Context(null, ctxCfg, logger)
        OpenShiftService openShiftService = Mock(OpenShiftService.class)
        openShiftService.findOrCreateImageStream('project-cd', 'example.com/foo/repo/image')
        ServiceRegistry.instance.add(OpenShiftService, openShiftService)

        // We allow this method here but do not care about it
        this.helper.registerAllowedMethod('odsComponentStageScanWithAqua', [IContext, Map]) {}

        when:
        def script = loadScript('vars/odsComponentStageCopyImage.groovy')
        script.call(context, cfg)


        then:
        printCallStack()
        assertCallStackContains('(!!! Image successfully imported')

        // FIXME: How do I do this nicely?
        assertCallStackContains('Resolved source Image data: example.com/repo/image:1f3d1ed76c304893cb709c9b65a64b2cf2e4676b607c6be737c1dddd1cf89065')
        assertCallStackContains('importing into: docker://internal-registry/project-cd/image:1f3d1ed76c304893cb709c9b65a64b2cf2e4676b607c6be737c1dddd1cf89065')
        // FIXME: this should probably verify that the steps.sh is called with the correct string rather than checking the full callstack
        assertCallStackContains('skopeo copy --src-tls-verify=true                  docker://example.com/repo/image:1f3d1ed76c304893cb709c9b65a64b2cf2e4676b607c6be737c1dddd1cf89065                 --dest-creds openshift:                 docker://internal-registry/project-cd/image:1f3d1ed76c304893cb709c9b65a64b2cf2e4676b607c6be737c1dddd1cf89065                 --dest-tls-verify=true')

        assertJobStatusSuccess()
    }
}
