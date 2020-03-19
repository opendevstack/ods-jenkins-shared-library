package org.ods

class OdsContextTest extends GroovyTestCase {

    PipelineScript script
    Logger logger

    protected void setUp() {
        script = new PipelineScript()

        logger = new Logger() {
            @Override
            void info(String message) {
            }

            @Override
            void debug(String message) {
            }

            @Override
            void error(String message) {
            }
        }
    }

    void testDetermineEnvironment_gitflow() {
        def existingEnvironments = []
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

        determineEnvironment(config,existingEnvironments,'master')
        assertEquals("prod", config.environment)

        determineEnvironment(config,existingEnvironments,'develop')
        assertEquals("dev", config.environment)

        determineEnvironment(config,existingEnvironments,'release/1.0.0')
        assertEquals("rel", config.environment)

        determineEnvironment(config,existingEnvironments,'hotfix/foo-123-bar')
        assertEquals("hotfix", config.environment)

        determineEnvironment(config,existingEnvironments,'feature/foo-123-bar')
        assertEquals("preview", config.environment)

        existingEnvironments = ['preview-123']
        determineEnvironment(config,existingEnvironments,'feature/foo-123-bar')
        assertEquals("preview-123", config.environment)

        existingEnvironments = ['preview-foo-bar']
        determineEnvironment(config,existingEnvironments,'foo-bar')
        assertEquals("preview-foo-bar", config.environment)
    }

    void testDetermineEnvironment_githubflow() {
        def existingEnvironments = []
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

        determineEnvironment(config,existingEnvironments, 'master')
        assertEquals("prod", config.environment)

        determineEnvironment(config,existingEnvironments,'feature/foo-123-bar')
        assertEquals("preview-123", config.environment)
    }

    void testDetermineEnvironment_autoclone() {
        def existingEnvironments = []
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

        determineEnvironment(config,existingEnvironments,'release/1.0.0')
        assertEquals("rel-1.0.0", config.environment)
        assertEquals("dev", config.cloneSourceEnv)

        determineEnvironment(config, existingEnvironments, 'hotfix/foo-123-bar')
        assertEquals("hotfix-123", config.environment)
        assertEquals("prod", config.cloneSourceEnv)

        determineEnvironment(config, existingEnvironments, 'hotfix/foo')
        assertEquals("hotfix-foo", config.environment)
        assertEquals("prod", config.cloneSourceEnv)

        determineEnvironment(config, existingEnvironments, 'feature/foo-123-bar')
        assertEquals("preview-123", config.environment)
        assertEquals("dev", config.cloneSourceEnv)

        determineEnvironment(config, existingEnvironments, 'foo-bar')
        assertEquals("preview-foo-bar", config.environment)
        assertEquals("dev", config.cloneSourceEnv)
    }

    //resets config.environment and call determineEnvironment on newly created OdsContext object
    void determineEnvironment(config, existingEnvironments, String branch) {
        config.environment = null
        config.gitBranch = branch
        def uut = new OdsContext(script, config, logger) {
            boolean environmentExists(String name) {
                existingEnvironments.contains(name)
            }
        }
        uut.determineEnvironment()

    }
}
