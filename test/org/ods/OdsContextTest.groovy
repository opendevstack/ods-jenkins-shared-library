package org.ods


import static org.assertj.core.api.Assertions.assertThat

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

        determineEnvironment(config, existingEnvironments, 'master')
        assertEquals("prod", config.environment)
        assertThat(config.cloneSourceEnv).isNull()

        determineEnvironment(config, existingEnvironments, 'develop')
        assertEquals("dev", config.environment)
        assertThat(config.cloneSourceEnv).isNull()

        determineEnvironment(config, existingEnvironments, 'release/1.0.0')
        assertEquals("rel", config.environment)
        assertThat(config.cloneSourceEnv).isNull()

        determineEnvironment(config, existingEnvironments, 'hotfix/foo-123-bar')
        assertEquals("hotfix", config.environment)
        assertThat(config.cloneSourceEnv).isNull()

        determineEnvironment(config, existingEnvironments, 'feature/foo-123-bar')
        assertEquals("preview", config.environment)
        assertThat(config.cloneSourceEnv).isNull()

        existingEnvironments = ['preview-123']
        determineEnvironment(config, existingEnvironments, 'feature/foo-123-bar')
        assertEquals("preview-123", config.environment)
        assertThat(config.cloneSourceEnv).isNull()

        existingEnvironments = ['preview-foo-bar']
        determineEnvironment(config, existingEnvironments, 'foo-bar')
        assertEquals("preview-foo-bar", config.environment)
        assertThat(config.cloneSourceEnv).isNull()
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

        determineEnvironment(config, existingEnvironments, 'master')
        assertEquals("prod", config.environment)
        assertThat(config.cloneSourceEnv).isNull()

        determineEnvironment(config, existingEnvironments, 'feature/foo-123-bar')
        assertEquals("preview-123", config.environment)
        assertThat(config.cloneSourceEnv).isEqualTo("prod")

    }

    void testDetermineEnvironment_autoclone_without_branchprefix() {
        def existingEnvironments = ["dev", "prod"]
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

        determineEnvironment(config, existingEnvironments, 'integration')
        assertEquals("int", config.environment)
        assertEquals("dev", config.cloneSourceEnv)

        determineEnvironment(config, ["int"], 'integration')
        assertThat(config.cloneSourceEnv)
                .as("Already existing environment is not created again")
                .isEqualTo(false);
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

        determineEnvironment(config, existingEnvironments, 'release/1.0.0')
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
        config.cloneSourceEnv = null
        def uut = new OdsContext(script, config, logger) {
            boolean environmentExists(String name) {
                existingEnvironments.contains(name)
            }
        }
        uut.determineEnvironment()

    }

    def bbBaseUrl = 'https://bitbucket.bix-digital.com/projects/OPENDEVSTACK/repos/ods-core/raw/ocp-scripts'

    void testCloneProjectScriptUrlsDefault() {
        def m = getCloneProjectScriptUrls([
                podContainers: [],
                jobName: 'test-job-name',
                environment: 'foo-dev',
        ])


        def expected = [
                'clone-project.sh': 'clone-project.sh?at=refs%2Fheads%2Fproduction',
                'export-project.sh': 'export-project.sh?at=refs%2Fheads%2Fproduction',
                'import-project.sh': 'import-project.sh?at=refs%2Fheads%2Fproduction',
        ]
        assertEquals(['clone-project.sh', 'import-project.sh', 'export-project.sh'].toSet(), m.keySet())
        for (e in expected) {
            assertScript(m, e.key, "${bbBaseUrl}/${e.value}")
            print "${bbBaseUrl}/${e.value}\n"
        }
    }

    void testCloneProjectScriptUrlsAtBranch() {
        def m = getCloneProjectScriptUrls([
                podContainers: [],
                environment: 'foo-dev',
                cloneProjectScriptBranch: 'fix/gh318-test',
        ])
        def expected = [
                'clone-project.sh': 'clone-project.sh?at=refs%2Fheads%2Ffix%2Fgh318-test',
                'export-project.sh': 'export-project.sh?at=refs%2Fheads%2Ffix%2Fgh318-test',
                'import-project.sh': 'import-project.sh?at=refs%2Fheads%2Ffix%2Fgh318-test',
        ]
        assertEquals(['clone-project.sh', 'import-project.sh', 'export-project.sh'].toSet(), m.keySet())
        for (e in expected) {
            assertScript(m, e.key, "${bbBaseUrl}/${e.value}")
        }
    }

    Map<String,String> getCloneProjectScriptUrls(config) {
        def uut = new OdsContext(script, config, logger)
        uut.assemble()
        return uut.getCloneProjectScriptUrls()
    }

    private assertScript(Map<String, String> m, String script, String expectedUrl) {
        def url = m[script]
        assertEquals(expectedUrl, url)
        assertTrue('script name should be some as in url', url.contains(script))
        print "${url}\n"  // for manual opening links
    }
}
