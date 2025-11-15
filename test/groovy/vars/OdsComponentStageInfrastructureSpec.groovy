package vars

import org.ods.component.Context
import org.ods.component.IContext
import org.ods.services.IScmService
import org.ods.services.InfrastructureService
import org.ods.services.ScmBitbucketService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import spock.lang.Shared
import vars.test_helper.PipelineSpockTestBase

class OdsComponentStageInfrastructureSpec extends PipelineSpockTestBase {
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
        def options = [ cloudProvider: 'AWS' ]
        IContext context = new Context(null, c, logger)

        InfrastructureService infrastructureService = Stub(InfrastructureService.class)
        infrastructureService.runMake(*_) >> 0
        ServiceRegistry.instance.add(InfrastructureService, infrastructureService)

        ScmBitbucketService bitbucketService = Stub(ScmBitbucketService.class)
        bitbucketService.createCodeInsightReport(*_) >> null
        ServiceRegistry.instance.add(IScmService, bitbucketService)

        when:
        def script = loadScript('vars/odsComponentStageInfrastructure.groovy')
        helper.registerAllowedMethod('readFile', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('readJSON', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('readYaml', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('sh', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('archiveArtifacts', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('stash', [ Map ]) { Map args -> }
        helper.registerAllowedMethod('emailext', [ Map ]) { Map args -> }
        script.call(context, options)

        then:
        printCallStack()
        assertJobStatusSuccess()
    }

    def "Run successfully with target environment"() {
        given:
        def c = config + [environment: 'dev']
        def options = [ cloudProvider: 'AWS' ]
        IContext context = new Context(null, c, logger)

        InfrastructureService infrastructureService = Stub(InfrastructureService.class)
        infrastructureService.runMake(*_) >> 0
        ServiceRegistry.instance.add(InfrastructureService, infrastructureService)

        ScmBitbucketService bitbucketService = Stub(ScmBitbucketService.class)
        bitbucketService.createCodeInsightReport(*_) >> null
        ServiceRegistry.instance.add(IScmService, bitbucketService)

        when:
        def script = loadScript('vars/odsComponentStageInfrastructure.groovy')
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
        script.call(context, options)

        then:
        printCallStack()
        assertJobStatusSuccess()
    }

    def "Run failed when runMake fails"() {
        given:
        def c = config + [environment: 'dev']
        def options = [ cloudProvider: 'AWS' ]
        IContext context = new Context(null, c, logger)

        InfrastructureService infrastructureService = Stub(InfrastructureService.class)
        infrastructureService.runMake(rule, *_) >> 1
        ServiceRegistry.instance.add(InfrastructureService, infrastructureService)

        ScmBitbucketService bitbucketService = Stub(ScmBitbucketService.class)
        bitbucketService.createCodeInsightReport(*_) >> null
        ServiceRegistry.instance.add(IScmService, bitbucketService)

        when:
        def script = loadScript('vars/odsComponentStageInfrastructure.groovy')
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
        script.call(context, options)

        then:
        printCallStack()
        assertCallStackContains(errorMessage)
        assertJobStatusFailure()

        where:
        rule                || errorMessage
        'create-tfvars'     || 'Creation of tfvars failed!'
        'test'              || 'IaC - Testing stage failed!'
        'plan'              || 'IaC - Plan stage failed!'
        'deploy'            || 'IaC - Deploy stage failed!'
        'deployment-test'   || 'IaC - Deployment-Test stage failed!'
        'install-report'    || 'IaC - Report stage failed!'
    }

    def "Run failed when no right cloud provider"() {
        given:
        def c = config + [environment: 'dev']
        def options = [ cloudProvider: cloudProvider ]
        IContext context = new Context(null, c, logger)

        InfrastructureService infrastructureService = Stub(InfrastructureService.class)
        infrastructureService.runMake(*_) >> 0
        ServiceRegistry.instance.add(InfrastructureService, infrastructureService)

        ScmBitbucketService bitbucketService = Stub(ScmBitbucketService.class)
        bitbucketService.createCodeInsightReport(*_) >> null
        ServiceRegistry.instance.add(IScmService, bitbucketService)

        when:
        def script = loadScript('vars/odsComponentStageInfrastructure.groovy')
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
        script.call(context, options)

        then:
        printCallStack()
        assertCallStackContains(errorMessage)
        assertJobStatusFailure()

        where:
        cloudProvider       || errorMessage
        'AWSS'              || "Cloud provider AWSS not in supported list: ${InfrastructureService.CLOUD_PROVIDERS}"
        'AW'                || "Cloud provider AW not in supported list: ${InfrastructureService.CLOUD_PROVIDERS}"
        ''                  || "Cloud provider  not in supported list: ${InfrastructureService.CLOUD_PROVIDERS}"
        null                || "Cloud provider null not in supported list: ${InfrastructureService.CLOUD_PROVIDERS}"
    }

}


