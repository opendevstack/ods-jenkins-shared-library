package vars

import org.ods.component.Context
import org.ods.component.IContext
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import spock.lang.Unroll
import vars.test_helper.PipelineSpockTestBase

class OdsComponentStageCopyImageSpec extends PipelineSpockTestBase {

    private Logger logger = Mock(Logger)

    @Unroll
    def "run copy image command"() {
        given:
        def sourceImageUrlIncludingRegistry = "${registry}/${repo}/${imageName}:${imageTag}"
        def cfg = [
            sourceImageUrlIncludingRegistry: sourceImageUrlIncludingRegistry,
            verifyTLS                      : verifyTLS,
            tagIntoTargetEnv               : tagIntoTargetEnv,
        ]
        def ctxCfg = [
            cdProject     : 'project-cd',
            targetProject : 'project-dev',
            componentId   : 'example-component',
            dockerRegistry: 'internal-registry',
            branch        : 'master',

        ]
        IContext context = new Context(null, ctxCfg, logger)
        OpenShiftService openShiftService = Mock(OpenShiftService.class)
        openShiftService.findOrCreateImageStream('project-cd', sourceImageUrlIncludingRegistry)
        openShiftService.getImageReference(*_) >> "${imageName}:${imageTag}"

        ServiceRegistry.instance.add(OpenShiftService, openShiftService)

        // We allow this method here but do not care about it
        this.helper.registerAllowedMethod('odsComponentStageScanWithAqua', [IContext, Map]) {}

        when:
        def script = loadScript('vars/odsComponentStageCopyImage.groovy')
        helper.registerAllowedMethod('readFile', [ Map ]) { "secret-token" }
        script.call(context, cfg)


        then:
        printCallStack()
        assertCallStackContains("!!! Image successfully imported into internal-registry/project-cd/${imageName}:${imageTag}")
        assertCallStackContains("Resolved source Image data: ${sourceImageUrlIncludingRegistry}")
        assertCallStackContains("importing into: docker://internal-registry/project-cd/${imageName}:${imageTag}")
        // FIXME: this should probably verify that the steps.sh is called with the correct string rather than checking the full callstack
        assertCallStackContains("skopeo copy --src-tls-verify=${expectedVerifyTLS}                  docker://${sourceImageUrlIncludingRegistry}                 --dest-creds openshift:secret-token                 docker://internal-registry/project-cd/image:1f3d1                 --dest-tls-verify=${expectedVerifyTLS}")
        if (tagIntoTargetEnv) {
            1 * openShiftService.importImageTagFromProject('project-dev', imageName, 'project-cd', imageTag, imageTag)
            1 * openShiftService.findOrCreateImageStream('project-dev', imageName)
        }

        assertJobStatusSuccess()

        where:
        registry      || repo   || imageName || imageTag || verifyTLS || expectedVerifyTLS || tagIntoTargetEnv
        'example.com' || 'repo' || 'image'   || '1f3d1'  || true      || true              || true
        'example.com' || 'repo' || 'image'   || '1f3d1'  || true      || true              || false
        'example.com' || 'repo' || 'image'   || '1f3d1'  || false     || false             || true
        'example.com' || 'repo' || 'image'   || '1f3d1'  || false     || false             || false
        'example.com' || 'repo' || 'image'   || '1f3d1'  || null      || true              || true
        'example.com' || 'repo' || 'image'   || '1f3d1'  || null      || true              || false

    }
}
