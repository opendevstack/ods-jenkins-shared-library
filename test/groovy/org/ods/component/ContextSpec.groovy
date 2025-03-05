package org.ods.component

import util.PipelineSteps
import org.ods.util.ILogger
import org.ods.util.Logger
import org.ods.util.ShellWithRetry
import org.ods.util.IPipelineSteps
import spock.lang.*
import org.ods.services.OpenShiftService

class ContextSpec extends Specification {

    private PipelineSteps script = new PipelineSteps()
    private Logger logger = Mock(Logger)

    @Shared
    def noEnv = []

    @Unroll
    def "IssueID: when branch is #branch and commit is #commit issueId should be #expectedIssueId"(branch, commit, expectedIssueId) {
        given:
        def config = [
            projectId: 'foo',
            gitBranch: branch,
            gitCommitMessage: commit
        ]

        when:
        def uut = new Context(script, config, logger)

        then:
        uut.getIssueId() == expectedIssueId

        where:
        branch                | commit                     || expectedIssueId
        'develop'             | 'Some text'                || ''
        'develop'             | 'FOO-123: Some text'       || '123'
        'develop'             | 'FOO-123 Some text'        || '123'
        'develop'             | 'Some text (FOO-123)'      || '123'
        'develop'             | 'Some text (FOOBAR-123)'   || ''
        'develop'             | 'FOO 123 bar'              || ''
        'feature/foo-123-bar' | 'FOO-456: Todo'            || '123'
        'develop'             | 'FOO-456 replaces FOO-123' || '456'
        'feature/foo-123-bar' | 'Todo'                     || '123'
        'feature/foo-123-bar' | 'Todo'                     || '123'
        'release/1.0.0'       | 'Todo'                     || ''
    }

    @Unroll
    def "Gitflow: when branch is #branch and existingEnv is #existingEnv expectedEnv should be #expectedEnv"(branch, existingEnv, expectedEnv) {
        given:
        def config = [
            projectId                             : 'foo',
            branchToEnvironmentMapping            : [
                'master'  : 'prod',
                'develop' : 'dev',
                'release/': 'rel',
                'hotfix/' : 'hotfix',
                '*'       : 'preview'
            ]
        ]

        when:
        determineEnvironment(config, existingEnv, branch)

        then:
        config.environment == expectedEnv

        where:
        branch                | existingEnv         | expectedEnv
        'master'              | noEnv               | 'prod'
        'develop'             | noEnv               | 'dev'
        'release/1.0.0'       | noEnv               | 'rel'
        'hotfix/foo-123-bar'  | noEnv               | 'hotfix'
        'feature/foo-123-bar' | noEnv               | 'preview'
        'feature/foo-123-bar' | ['preview-123']     | 'preview-123'
        'foo-bar'             | ['preview-foo-bar'] | 'preview-foo-bar'
    }

    @Unroll
    def "Githubflow: when branch = #branch and existingEnv = #existingEnv expectedEnv should be #expectedEnv"(branch, existingEnv, expectedEnv) {

        given:
        def config = [
            projectId                             : 'foo',
            branchToEnvironmentMapping            : [
                'master': 'prod',
                '*'     : 'preview'
            ]
        ]

        when:
        determineEnvironment(config, existingEnv, branch)

        then:
        config.environment == expectedEnv

        where:
        branch                | existingEnv | expectedEnv
        'master'              | noEnv       | 'prod'
        'feature/foo-123-bar' | noEnv       | 'preview'
    }

    def "assemble with retry"() {
        given:
        Context context = Spy(new Context(script, null, logger) )

        when:
        context.assemble()

        then:
        1 * context.assembleWithRetry( ) >> { throw new NotSerializableException("error")}
        1 * logger.warn{String it -> it.contains("WARN: Jenkins serialization issue")}

        1 * context.assembleWithRetry( )>> { throw new NotSerializableException("error")}
        1 * logger.warn{String it -> it.contains("WARN: Jenkins serialization issue")}

        1 * context.assembleWithRetry( ) >> { return }
    }

    def "assemble with retry and error"() {
        given:
        Context context = Spy(new Context(script, null, logger) )

        when:
        context.assemble()

        then:
        1 * context.assembleWithRetry( ) >> { throw new NotSerializableException("error")}
        1 * logger.warn{String it -> it.contains("WARN: Jenkins serialization issue")}

        1 * context.assembleWithRetry( )>> { throw new NotSerializableException("error")}
        1 * logger.warn{String it -> it.contains("WARN: Jenkins serialization issue")}

        1 * context.assembleWithRetry( )>> { throw new NotSerializableException("error")}
        1 * logger.warn{String it -> it.contains("WARN: Jenkins serialization issue")}

        1 * context.assembleWithRetry( )>> { throw new NotSerializableException("error")}
        1 * logger.warn{String it -> it.contains("WARN: Jenkins serialization issue")}

        1 * context.assembleWithRetry( )>> { throw new NotSerializableException("error")}
        1 * logger.warn{String it -> it.contains("WARN: Jenkins serialization issue")}

        thrown java.util.concurrent.ExecutionException
    }

    // resets config.environment and call determineEnvironment on newly created Context object
    void determineEnvironment(config, existingEnvironments, String branch) {
        config.environment = null
        config.gitBranch = branch
        def uut = new Context(script, config, logger) {
            boolean environmentExists(String name) {
                existingEnvironments.contains(name)
            }
        }
        uut.determineEnvironment()
    }

    def "get openshift cluster domain"() {
        given:
        def steps = Stub(IPipelineSteps)
        GroovySpy(OpenShiftService, constructorArgs: [steps, new Logger(steps, false)], global: true)
        def context = new Context(steps, null, logger)
        def expectedDomain = 'apps.openshift.com'
        OpenShiftService.getApplicationDomain(_) >> expectedDomain

        when:
        def domain = context.getOpenshiftApplicationDomain()

        then:
        domain == expectedDomain
    }

}
