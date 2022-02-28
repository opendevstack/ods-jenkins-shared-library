package vars

import org.ods.component.Context
import org.ods.component.IContext
import org.ods.component.InfrastructureStage
import org.ods.services.InfrastructureService
import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.services.NexusServiceSpec
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import spock.lang.Shared
import vars.test_helper.PipelineSpockTestBase

class OdsComponentStageInfrastructureAWSSpec extends PipelineSpockTestBase {
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
        odsSharedLibVersion: '4.x',
        branchToEnvironmentMapping: ['master': 'dev', 'release/': 'test'],
        JENKINS_MASTER_OPENSHIFT_BUILD_NAMESPACE: 'ods'
    ]

    def "Run successfully without target environment"() {
        given:
        def c = config
        IContext context = new Context(null, c, logger)

        InfrastructureService infrastructureService = Stub(InfrastructureService.class)
        infrastructureService.runMake(*_) >> 0
        ServiceRegistry.instance.add(InfrastructureService, infrastructureService)

        BitbucketService bitbucketService = Stub(BitbucketService.class)
        bitbucketService.createCodeInsightReport(*_) >> null
        ServiceRegistry.instance.add(BitbucketService, bitbucketService)

        when:
        def script = loadScript('vars/odsComponentStageInfrastructureAWS.groovy')
        helper.registerAllowedMethod('readFile', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('readJSON', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('readYaml', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('sh', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('archiveArtifacts', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('stash', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('emailext', [ Map ]) { Map args -> }
        script.call(context)

        then:
        printCallStack()
        assertJobStatusSuccess()
    }

    def "Run successfully with target environment"() {
        given:
        def c = config + [environment: 'dev']
        IContext context = new Context(null, c, logger)

        InfrastructureService infrastructureService = Stub(InfrastructureService.class)
        infrastructureService.runMake(*_) >> 0
        ServiceRegistry.instance.add(InfrastructureService, infrastructureService)

        BitbucketService bitbucketService = Stub(BitbucketService.class)
        bitbucketService.createCodeInsightReport(*_) >> null
        ServiceRegistry.instance.add(BitbucketService, bitbucketService)

        when:
        def script = loadScript('vars/odsComponentStageInfrastructureAWS.groovy')
        helper.registerAllowedMethod('readFile', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('readYaml', [ Map ]) { [
            account: "anAwsAccount",
            credentials: [key: "aKey", secret: "aSecret"]
        ] }
        helper.registerAllowedMethod('readJSON', [ Map ]) { [
            meta_environment: "development"
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

    def "Run failed when runMake fails"() {
        given:
        def c = config + [environment: 'dev']
        IContext context = new Context(null, c, logger)

        InfrastructureService infrastructureService = Stub(InfrastructureService.class)
        infrastructureService.runMake(rule, *_) >> 1
        ServiceRegistry.instance.add(InfrastructureService, infrastructureService)

        BitbucketService bitbucketService = Stub(BitbucketService.class)
        bitbucketService.createCodeInsightReport(*_) >> null
        ServiceRegistry.instance.add(BitbucketService, bitbucketService)

        when:
        def script = loadScript('vars/odsComponentStageInfrastructureAWS.groovy')
        helper.registerAllowedMethod('readFile', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('readYaml', [ Map ]) { [
            account: "anAwsAccount",
            credentials: [key: "aKey", secret: "aSecret"]
        ] }
        helper.registerAllowedMethod('readJSON', [ Map ]) { [
            meta_environment: "development"
        ] }
        helper.registerAllowedMethod('sh', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('archiveArtifacts', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('stash', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('emailext', [ Map ]) { Map args -> }
        script.call(context)

        then:
        printCallStack()
        assertCallStackContains(errorMessage)
        assertJobStatusFailure()

        where:
        rule                || errorMessage
        'create-tfvars'     || 'Creation of tfvars failed!'
        'test'              || 'AWS IaC - Testing stage failed!'
        'plan'              || 'AWS IaC - Plan stage failed!'
        'deploy'            || 'AWS IaC - Deploy stage failed!'
        'deployment-test'   || 'AWS IaC - Deployment-Test stage failed!'
        'install-report'    || 'AWS IaC - Report stage failed!'
    }
}
