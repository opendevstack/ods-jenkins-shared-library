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
            projectId: 'foo',
            branchToEnvironmentMapping: [
                'master': 'prod',
                'develop': 'dev',
                'release/': 'rel',
                'hotfix/': 'hotfix',
                '*': 'preview'
            ],
            autoCloneEnvironmentsFromSourceMapping: [:]
        ]
        def uut = new OdsContext(script, config, logger) {
            protected boolean environmentExists(String name) {
                existingEnvironments.contains(name)
            }
        }

        config.gitBranch = 'master'
        uut.determineEnvironment()
        assertEquals("prod", config.environment)

        config.gitBranch = 'develop'
        uut.determineEnvironment()
        assertEquals("dev", config.environment)

        config.gitBranch = 'release/1.0.0'
        uut.determineEnvironment()
        assertEquals("rel", config.environment)

        config.gitBranch = 'hotfix/foo-123-bar'
        uut.determineEnvironment()
        assertEquals("hotfix", config.environment)

        config.gitBranch = 'feature/foo-123-bar'
        uut.determineEnvironment()
        assertEquals("preview", config.environment)

        existingEnvironments = ['preview-123']
        config.gitBranch = 'feature/foo-123-bar'
        uut.determineEnvironment()
        assertEquals("preview-123", config.environment)

        existingEnvironments = ['preview-foo-bar']
        config.gitBranch = 'foo-bar'
        uut.determineEnvironment()
        assertEquals("preview-foo-bar", config.environment)
    }

    void testDetermineEnvironment_githubflow() {
        def existingEnvironments = []
        def config = [
            projectId: 'foo',
            branchToEnvironmentMapping: [
                'master': 'prod',
                '*': 'preview'
            ],
            autoCloneEnvironmentsFromSourceMapping: [
                'preview': 'prod'
            ]
        ]
        def uut = new OdsContext(script, config, logger) {
            protected boolean environmentExists(String name) {
                existingEnvironments.contains(name)
            }
        }

        config.gitBranch = 'master'
        uut.determineEnvironment()
        assertEquals("prod", config.environment)

        config.gitBranch = 'feature/foo-123-bar'
        uut.determineEnvironment()
        assertEquals("preview-123", config.environment)
    }

    void testDetermineEnvironment_autoclone() {
        def existingEnvironments = []
        def config = [
            projectId: 'foo',
            branchToEnvironmentMapping: [
                'master': 'prod',
                'develop': 'dev',
                'release/': 'rel',
                'hotfix/': 'hotfix',
                '*': 'preview'
            ],
            autoCloneEnvironmentsFromSourceMapping: [
                'rel': 'dev',
                'hotfix': 'prod',
                'preview': 'dev'
            ]
        ]
        def uut = new OdsContext(script, config, logger) {
            protected boolean environmentExists(String name) {
                existingEnvironments.contains(name)
            }
        }

        config.gitBranch = 'release/1.0.0'
        uut.determineEnvironment()
        assertEquals("rel-1.0.0", config.environment)
        assertEquals("dev", config.cloneSourceEnv)

        config.gitBranch = 'hotfix/foo-123-bar'
        uut.determineEnvironment()
        assertEquals("hotfix-123", config.environment)
        assertEquals("prod", config.cloneSourceEnv)

        config.gitBranch = 'hotfix/foo'
        uut.determineEnvironment()
        assertEquals("hotfix-foo", config.environment)
        assertEquals("prod", config.cloneSourceEnv)

        config.gitBranch = 'feature/foo-123-bar'
        uut.determineEnvironment()
        assertEquals("preview-123", config.environment)
        assertEquals("dev", config.cloneSourceEnv)

        config.gitBranch = 'foo-bar'
        uut.determineEnvironment()
        assertEquals("preview-foo-bar", config.environment)
        assertEquals("dev", config.cloneSourceEnv)
    }
}
