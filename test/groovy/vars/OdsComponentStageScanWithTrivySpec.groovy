package vars

import org.ods.component.Context
import org.ods.component.IContext
import org.ods.services.IScmService
import org.ods.services.TrivyService
import org.ods.services.ScmBitbucketService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import spock.lang.Shared
import vars.test_helper.PipelineSpockTestBase

class OdsComponentStageScanWithTrivySpec extends PipelineSpockTestBase {
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

        TrivyService trivyService = Stub(TrivyService.class)
        trivyService.scanViaCli(*_) >> 0
        ServiceRegistry.instance.add(TrivyService, trivyService)

        ScmBitbucketService bitbucketService = Stub(ScmBitbucketService.class)
        bitbucketService.createCodeInsightReport(*_) >> null
        ServiceRegistry.instance.add(IScmService, bitbucketService)

        OpenShiftService openShiftService = Stub(OpenShiftService.class)
        ServiceRegistry.instance.add(OpenShiftService, openShiftService)

        NexusService nexusService = Stub(NexusService.class)
        nexusService.storeArtifact(*_) >> new URI("http://nexus/repository/leva-documentation/foo/12345-11/trivy/trivy-sbom.json")
        ServiceRegistry.instance.add(NexusService, nexusService)

        when:
        def script = loadScript('vars/odsComponentStageScanWithTrivy.groovy')
        helper.registerAllowedMethod('sh', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('archiveArtifacts', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('stash', [ Map ]) { Map args -> }
        script.call(context)

        then:
        printCallStack()
        assertJobStatusSuccess()
    }
}
