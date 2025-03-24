package vars

import org.ods.component.Context
import org.ods.component.IContext
import org.ods.component.ScanWithAquaStage
import org.ods.services.AquaService
import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.services.NexusServiceSpec
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.PipelineSteps

import spock.lang.Shared
import vars.test_helper.PipelineSpockTestBase

class OdsComponentStageScanWithAquaSpec extends PipelineSpockTestBase {
    private Logger logger = Mock(Logger)

    @Shared
    def config = [
        bitbucketUrl: 'https://bitbucket.example.com',
        projectId: 'foo',
        componentId: 'bar',
        repoName: 'foo-bar',
        gitUrl: 'https://bitbucket.example.com/scm/foo/foo-bar.git',
        gitCommit: 'cd3e9082d7466942e1de86902bb9e663751dae8e',
        gitCommitMessage: """Foo\n\nSome "explanation".""",
        gitCommitAuthor: "John O'Hare",
        gitCommitTime: '2020-03-23 12:27:08 +0100',
        gitBranch: 'master',
        buildUrl: 'https://jenkins.example.com/job/foo-cd/job/foo-cd-bar-master/11/console',
        buildTime: '2020-03-23 12:27:08 +0100',
        odsSharedLibVersion: '2.x',
        branchToEnvironmentMapping: ['master': 'dev', 'release/': 'test'],
        JENKINS_MASTER_OPENSHIFT_BUILD_NAMESPACE: 'ods'
    ]

    def "Run successfully"() {
        given:
        def c = config + [environment: 'dev']
        IContext context = new Context(null, c, logger)
        context.addBuildToArtifactURIs("bar", [image: "image1/image1:2323232323"])

        AquaService aquaService = Stub(AquaService.class)
        aquaService.scanViaCli(*_) >> 0
        ServiceRegistry.instance.add(AquaService, aquaService)

        BitbucketService bitbucketService = Stub(BitbucketService.class)
        bitbucketService.createCodeInsightReport(*_) >> null
        ServiceRegistry.instance.add(BitbucketService, bitbucketService)

        OpenShiftService openShiftService = Stub(OpenShiftService.class)
        openShiftService.getConfigMapData('ods',
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal"
        ]
        openShiftService.getConfigMapData("foo", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true
        ]
        ServiceRegistry.instance.add(OpenShiftService, openShiftService)

        NexusService nexusService = Stub(NexusService.class)
        nexusService.storeArtifact(*_) >> new URI("http://nexus/repository/leva-documentation/foo/12345-11/aqua/report.html")
        ServiceRegistry.instance.add(NexusService, nexusService)

        PipelineSteps steps = Mock(PipelineSteps.class)
        steps.sh(*_) >> "something"
        ServiceRegistry.instance.add(PipelineSteps, steps)

        when:
        def script = loadScript('vars/odsComponentStageScanWithAqua.groovy')
        helper.registerAllowedMethod('readFile', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('readJSON', [ Map ]) { [
            vulnerability_summary: [critical: 0, malware: 0]
        ] }
        helper.registerAllowedMethod('sh', [ Map ]) { Map args -> return "xx"}
        helper.registerAllowedMethod('archiveArtifacts', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('stash', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('emailext', [ Map ]) { Map args -> }
        script.call(context)

        then:
        printCallStack()
        assertJobStatusSuccess()
    }

    def "Not executed - Disabled at project level"() {
        given:
        def c = config + [environment: 'dev']
        IContext context = new Context(null, c, logger)

        OpenShiftService openShiftService = Stub(OpenShiftService.class)
        openShiftService.getConfigMapData('ods',
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal"
        ]
        openShiftService.getConfigMapData("foo", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: false
        ]
        ServiceRegistry.instance.add(OpenShiftService, openShiftService)

        NexusService nexusService = Stub(NexusService.class)
        ServiceRegistry.instance.add(NexusService, nexusService)

        when:
        def script = loadScript('vars/odsComponentStageScanWithAqua.groovy')
        helper.registerAllowedMethod('emailext', [ Map ]) { Map args -> }
        script.call(context)

        then:
        printCallStack()
        assertJobStatusSuccess()
    }

    def "Not executed - Disabled at cluster level"() {
        given:
        def c = config + [environment: 'dev']
        IContext context = new Context(null, c, logger)

        OpenShiftService openShiftService = Stub(OpenShiftService.class)
        openShiftService.getConfigMapData('ods',
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: false,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal"
        ]
        openShiftService.getConfigMapData("foo", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true
        ]
        ServiceRegistry.instance.add(OpenShiftService, openShiftService)

        NexusService nexusService = Stub(NexusService.class)
        ServiceRegistry.instance.add(NexusService, nexusService)

        when:
        def script = loadScript('vars/odsComponentStageScanWithAqua.groovy')
        helper.registerAllowedMethod('emailext', [ Map ]) { Map args -> }
        script.call(context)

        then:
        printCallStack()
        assertJobStatusSuccess()
    }

    def "Not executed - Disabled at cluster and project level"() {
        given:
        def c = config + [environment: 'dev']
        IContext context = new Context(null, c, logger)

        OpenShiftService openShiftService = Stub(OpenShiftService.class)
        openShiftService.getConfigMapData('ods',
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: false,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal"
        ]
        openShiftService.getConfigMapData("foo", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: false
        ]
        ServiceRegistry.instance.add(OpenShiftService, openShiftService)

        NexusService nexusService = Stub(NexusService.class)
        ServiceRegistry.instance.add(NexusService, nexusService)

        when:
        def script = loadScript('vars/odsComponentStageScanWithAqua.groovy')
        helper.registerAllowedMethod('emailext', [ Map ]) { Map args -> }
        script.call(context)

        then:
        printCallStack()
        assertJobStatusSuccess()
    }

    def "Run successfully - Without enabled property in project ConfigMap"() {
        given:
        def c = config + [environment: 'dev']
        IContext context = new Context(null, c, logger)
        context.addBuildToArtifactURIs("bar", [image: "image1/image1:2323232323"])

        AquaService aquaService = Stub(AquaService.class)
        aquaService.scanViaCli(*_) >> 0
        ServiceRegistry.instance.add(AquaService, aquaService)

        BitbucketService bitbucketService = Stub(BitbucketService.class)
        bitbucketService.createCodeInsightReport(*_) >> null
        ServiceRegistry.instance.add(BitbucketService, bitbucketService)

        OpenShiftService openShiftService = Stub(OpenShiftService.class)
        openShiftService.getConfigMapData('ods',
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal"
        ]
        openShiftService.getConfigMapData("foo", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [:]
        ServiceRegistry.instance.add(OpenShiftService, openShiftService)

        NexusService nexusService = Stub(NexusService.class)
        nexusService.storeArtifact(*_) >> new URI("http://nexus/repository/leva-documentation/foo/12345-11/aqua/report.html")
        ServiceRegistry.instance.add(NexusService, nexusService)

        PipelineSteps steps = Mock(PipelineSteps.class)
        steps.sh(*_) >> "something"
        ServiceRegistry.instance.add(PipelineSteps, steps)

        when:
        def script = loadScript('vars/odsComponentStageScanWithAqua.groovy')
        helper.registerAllowedMethod('readFile', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('readJSON', [ Map ]) { [
            vulnerability_summary: [critical: 0, malware: 0]
        ] }
        helper.registerAllowedMethod('sh', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('archiveArtifacts', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('stash', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('emailext', [ Map ]) { Map args -> }
        script.call(context)

        then:
        printCallStack()
        assertJobStatusSuccess()
    }

    def "Run successfully - Without ConfigMap at project level"() {
        given:
        def c = config + [environment: 'dev']
        IContext context = new Context(null, c, logger)
        context.addBuildToArtifactURIs("bar", [image: "image1/image1:2323232323"])

        AquaService aquaService = Stub(AquaService.class)
        aquaService.scanViaCli(*_) >> 0
        ServiceRegistry.instance.add(AquaService, aquaService)

        BitbucketService bitbucketService = Stub(BitbucketService.class)
        bitbucketService.createCodeInsightReport(*_) >> null
        ServiceRegistry.instance.add(BitbucketService, bitbucketService)

        OpenShiftService openShiftService = Stub(OpenShiftService.class)
        openShiftService.getConfigMapData('ods',
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal"
        ]
        openShiftService.getConfigMapData("foo", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> {
            throw new Exception("Non existing ConfigMap")
        }
        ServiceRegistry.instance.add(OpenShiftService, openShiftService)

        NexusService nexusService = Stub(NexusService.class)
        nexusService.storeArtifact(*_) >> new URI("http://nexus/repository/leva-documentation/foo/12345-11/aqua/report.html")
        ServiceRegistry.instance.add(NexusService, nexusService)

        PipelineSteps steps = Mock(PipelineSteps.class)
        steps.sh(*_) >> "something"
        ServiceRegistry.instance.add(PipelineSteps, steps)

        when:
        def script = loadScript('vars/odsComponentStageScanWithAqua.groovy')
        helper.registerAllowedMethod('readFile', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('readJSON', [ Map ]) { [
            vulnerability_summary: [critical: 0, malware: 0]
        ] }
        helper.registerAllowedMethod('sh', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('archiveArtifacts', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('stash', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('emailext', [ Map ]) { Map args -> }
        script.call(context)

        then:
        printCallStack()
        assertJobStatusSuccess()
    }

    def "Not executed - Not existing Map in cluster"() {
        given:
        def c = config + [environment: 'dev']
        IContext context = new Context(null, c, logger)

        OpenShiftService openShiftService = Stub(OpenShiftService.class)
        openShiftService.getConfigMapData('ods',
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> {
            throw new Exception("Non existing ConfigMap")
        }
        ServiceRegistry.instance.add(OpenShiftService, openShiftService)

        NexusService nexusService = Stub(NexusService.class)
        ServiceRegistry.instance.add(NexusService, nexusService)

        when:
        def script = loadScript('vars/odsComponentStageScanWithAqua.groovy')
        helper.registerAllowedMethod('emailext', [ Map ]) { Map args -> }
        script.call(context)

        then:
        printCallStack()
        assertJobStatusSuccess()
    }

}
