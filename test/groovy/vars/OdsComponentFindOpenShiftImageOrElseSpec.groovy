package vars

import org.ods.component.Context
import org.ods.component.IContext
import org.ods.services.OpenShiftService
import org.ods.services.JenkinsService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase
import util.PipelineSteps
import spock.lang.*

class OdsComponentFindOpenShiftImageOrElseSpec extends PipelineSpockTestBase {

    private Logger logger = new Logger (new PipelineSteps(), true)

    @Unroll
    def "when image exists=#imageExists then orElseRan should be=#wantOrElseRan"(imageExists, wantOrElseRan, wantLogLine) {
        given:
        def cfg = [
            projectId: 'foo',
            componentId: 'bar',
            cdProject: 'foo-cd',
            gitCommit: 'cd3e9082d7466942e1de86902bb9e663751dae8e'
        ]
        def imageReference = '172.30.21.196:5000foo/bar@sha256:3877...9fe2'
        IContext context = new Context(null, cfg, logger)
        OpenShiftService openShiftService = Mock(OpenShiftService.class)
        openShiftService.imageExists(*_) >> imageExists
        openShiftService.getImageReference(*_) >> imageReference
        ServiceRegistry.instance.add(OpenShiftService, openShiftService)

        when:
        def script = loadScript('vars/odsComponentFindOpenShiftImageOrElse.groovy')
        def orElseRan = false
        script.call(context) {
            orElseRan = true
            def info = [image: imageReference]
            context.addBuildToArtifactURIs('bar', info)
        }

        then:
        printCallStack()
        assertCallStackContains(wantLogLine)
        assertJobStatusSuccess()
        orElseRan == wantOrElseRan
        context.buildArtifactURIs.builds['bar']['image'] == imageReference

        where:
        imageExists || wantOrElseRan | wantLogLine
        true        || false         | "Image 'bar:cd3e9082' exists already in 'foo-cd'"
        false       || true          | "Image 'bar:cd3e9082' does not exist yet in 'foo-cd'"
    }

    def "shortcut is disabled in orchestration pipeline"() {
        given:
        def cfg = [triggeredByOrchestrationPipeline: true]
        IContext context = new Context(null, cfg, logger)

        when:
        def script = loadScript('vars/odsComponentFindOpenShiftImageOrElse.groovy')
        def orElseRan = false
        script.call(context) {
            orElseRan = true
        }

        then:
        printCallStack()
        assertJobStatusSuccess()
        assertCallStackContains("Orchestration pipeline runs always execute the 'orElse' block")
        orElseRan == true
    }

}
