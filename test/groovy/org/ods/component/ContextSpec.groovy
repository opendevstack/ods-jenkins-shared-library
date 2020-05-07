package org.ods.component

import org.ods.PipelineScript
import spock.lang.*

class ContextSpec extends Specification {

    private PipelineScript script = new PipelineScript()
    private Logger logger = Mock(Logger)

    @Shared
    def noEnv = []

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
            ],
            autoCloneEnvironmentsFromSourceMapping: [:]
        ]

        when:
        determineEnvironment(config, existingEnv, branch)

        then:
        config.environment == expectedEnv
        config.cloneSourceEnv == null

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
    def "Githubflow: when branch = #branch and existingEnv = #existingEnv expectedEnv should be #expectedEnv and cloneSourceEnv = #cloneSourceEnv"(branch, existingEnv, expectedEnv, cloneSourceEnv) {

        given:
        def config = [
            projectId                             : 'foo',
            branchToEnvironmentMapping            : [
                'master': 'prod',
                '*'     : 'preview'
            ],
            autoCloneEnvironmentsFromSourceMapping: [
                'preview': 'prod'
            ]
        ]

        when:
        determineEnvironment(config, existingEnv, branch)

        then:
        config.environment == expectedEnv
        config.cloneSourceEnv == cloneSourceEnv

        where:
        branch                | existingEnv | expectedEnv   | cloneSourceEnv
        'master'              | noEnv       | 'prod'        | null
        'feature/foo-123-bar' | noEnv       | 'preview-123' | 'prod'
    }

    @Unroll
    def testDetermineEnvironmentAutocloneWithoutBranchprefix(branch, existingEnv, expectedEnv, cloneSourceEnv) {

        given:
        def config = [
            projectId                             : 'foo',
            branchToEnvironmentMapping            : [
                'master'     : 'prod',
                'develop'    : 'dev',
                'integration': 'int'
            ],
            autoCloneEnvironmentsFromSourceMapping: [
                'int': 'dev'
            ]
        ]

        when:
        determineEnvironment(config, existingEnv, branch)

        then:
        config.environment == expectedEnv
        config.cloneSourceEnv == cloneSourceEnv

        where:
        branch        | existingEnv     | expectedEnv | cloneSourceEnv
        'integration' | ['dev', 'prod'] | 'int'       | 'dev'
        'integration' | ['int']         | 'int'       | false
    }

    @Unroll
    def testDetermineEnvironment_autoclone(branch, existingEnv, expectedEnv, cloneSourceEnv) {

        given:
        def config = [
            projectId                             : 'foo',
            branchToEnvironmentMapping            : [
                'master'  : 'prod',
                'develop' : 'dev',
                'release/': 'rel',
                'hotfix/' : 'hotfix',
                '*'       : 'preview'
            ],
            autoCloneEnvironmentsFromSourceMapping: [
                'rel'    : 'dev',
                'hotfix' : 'prod',
                'preview': 'dev'
            ]
        ]

        when:
        determineEnvironment(config, existingEnv, branch)

        then:
        config.environment == expectedEnv
        config.cloneSourceEnv == cloneSourceEnv

        where:
        branch                | existingEnv | expectedEnv       | cloneSourceEnv
        'release/1.0.0'       | []          | 'rel-1.0.0'       | 'dev'
        'hotfix/foo-123-bar'  | []          | 'hotfix-123'      | 'prod'
        'hotfix/foo'          | []          | 'hotfix-foo'      | 'prod'
        'feature/foo-123-bar' | []          | 'preview-123'     | 'dev'
        'foo-bar'             | []          | 'preview-foo-bar' | 'dev'
    }

    //resets config.environment and call determineEnvironment on newly created Context object
    void determineEnvironment(config, existingEnvironments, String branch) {
        config.environment = null
        config.gitBranch = branch
        config.cloneSourceEnv = null
        def uut = new Context(script, config, logger) {
            boolean environmentExists(String name) {
                existingEnvironments.contains(name)
            }
        }
        uut.determineEnvironment()

    }

    def bbBaseUrl = 'https://bitbucket.example.com/projects/opendevstack/repos/ods-core/raw/ocp-scripts'

    def testCloneProjectScriptUrlsDefault() {
        when:
        def m = getCloneProjectScriptUrls([
            podContainers: [],
            branchToEnvironmentMapping: [:],
            environment: 'foo-dev',
        ])

        then:
        m.keySet() == ['clone-project.sh', 'import-project.sh', 'export-project.sh'] as Set
        m['clone-project.sh'] == "${bbBaseUrl}/clone-project.sh?at=refs%2Fheads%2Fmaster"
        m['export-project.sh'] == "${bbBaseUrl}/export-project.sh?at=refs%2Fheads%2Fmaster"
        m['import-project.sh'] == "${bbBaseUrl}/import-project.sh?at=refs%2Fheads%2Fmaster"
    }

    void testCloneProjectScriptUrlsAtBranch() {

        when:
        def m = getCloneProjectScriptUrls([
            podContainers: [],
            branchToEnvironmentMapping: [:],
            environment: 'foo-dev',
            cloneProjectScriptBranch: 'fix/gh318-test',
        ])

        then:
        m.keySet() == ['clone-project.sh', 'import-project.sh', 'export-project.sh'] as Set
        m['clone-project.sh'] == "${bbBaseUrl}/clone-project.sh?at=refs%2Fheads%2Ffix%2Fgh318-test"
        m['export-project.sh'] == "${bbBaseUrl}/export-project.sh?at=refs%2Fheads%2Ffix%2Fgh318-test"
        m['import-project.sh'] == "${bbBaseUrl}/import-project.sh?at=refs%2Fheads%2Ffix%2Fgh318-test"
    }

    Map<String, String> getCloneProjectScriptUrls(config) {
        def uut = new Context(script, config, logger)
        uut.assemble()
        return uut.getCloneProjectScriptUrls()
    }
}
