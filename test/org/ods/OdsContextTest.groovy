package org.ods

class OdsContextTest extends GroovyTestCase {

    PipelineScript script
    Logger logger

    protected void setUp() {
        script = new PipelineScript()

        logger = new Logger() {
            @Override
            void echo(String message) {
            }

            @Override
            void verbose(String message) {
            }

            @Override
            void error(String message) {
            }
        }
    }

    void testDetermineEnvironment_master() {
        def existingEnvironments = []
        def config = [
            projectId: 'psp',
            productionBranch: 'master',
            productionEnvironment: 'prod',
            gitBranch: 'master'
        ]
        def uut = new OdsContext(script, config, logger) {
            protected String getTicketIdFromBranch(String branchName, String projectId) {
                ""
            }
            protected boolean environmentExists(String name) {
                existingEnvironments.contains(name)
            }
        }


        config.workflow = 'git-flow'
        existingEnvironments = ['prod', 'dev']
        assertEquals("prod", uut.determineEnvironment())


        config.workflow = 'GitHub Flow'
        existingEnvironments = ['prod']
        assertEquals("prod", uut.determineEnvironment())
    }

    void testDetermineEnvironment_develop() {
        def existingEnvironments = []
        def config = [
            projectId: 'foo',
            developmentBranch: 'develop',
            developmentEnvironment: 'dev',
            defaultReviewEnvironment: 'review',
            gitBranch: 'develop'
        ]
        def uut = new OdsContext(script, config, logger) {
            protected String getTicketIdFromBranch(String branchName, String projectId) {
                ""
            }
            protected boolean environmentExists(String name) {
                existingEnvironments.contains(name)
            }
        }


        config.workflow = 'git-flow'
        existingEnvironments = ['prod', 'dev']
        assertEquals("dev", uut.determineEnvironment())


        config.workflow = 'GitHub Flow'
        existingEnvironments = ['prod']
        assertEquals("", uut.determineEnvironment())
        existingEnvironments = ['prod', 'review']
        assertEquals("review", uut.determineEnvironment())
    }

    void testDetermineEnvironment_hotfix() {
        def existingEnvironments = ['prod', 'dev']
        def config = [
            projectId: 'foo',
            workflow: 'git-flow',
            defaultHotfixEnvironment: 'hotfix',
            gitBranch: 'hotfix/foo-123-bar'
        ]
        def uut = new OdsContext(script, config, logger) {
            protected String getTicketIdFromBranch(String branchName, String projectId) {
                "123"
            }
            protected boolean environmentExists(String name) {
                existingEnvironments.contains(name)
            }
        }

        config.autoCreateHotfixEnvironment = true
        assertEquals("hotfix-123", uut.determineEnvironment())

        config.autoCreateHotfixEnvironment = false
        existingEnvironments = ['prod', 'dev', 'hotfix-123']
        assertEquals("hotfix-123", uut.determineEnvironment())

        config.autoCreateHotfixEnvironment = false
        existingEnvironments = ['prod', 'dev', 'hotfix']
        assertEquals("hotfix", uut.determineEnvironment())

        config.autoCreateHotfixEnvironment = false
        existingEnvironments = []
        assertEquals("", uut.determineEnvironment())
    }

    void testDetermineEnvironment_release() {
        def existingEnvironments = ['prod', 'dev']
        def config = [
            projectId: 'foo',
            workflow: 'git-flow',
            defaultReleaseEnvironment: 'release',
            gitBranch: 'release/1.0.0'
        ]
        def uut = new OdsContext(script, config, logger) {
            protected boolean environmentExists(String name) {
                existingEnvironments.contains(name)
            }
        }

        config.autoCreateReleaseEnvironment = true
        assertEquals("release-1.0.0", uut.determineEnvironment())

        config.autoCreateReleaseEnvironment = false
        existingEnvironments = ['prod', 'dev', 'release-1.0.0']
        assertEquals("release-1.0.0", uut.determineEnvironment())

        config.autoCreateReleaseEnvironment = false
        existingEnvironments = ['prod', 'dev', 'release']
        assertEquals("release", uut.determineEnvironment())

        config.autoCreateReleaseEnvironment = false
        existingEnvironments = []
        assertEquals("", uut.determineEnvironment())
    }

    void testDetermineEnvironment_review() {
        def existingEnvironments = []
        def config = [
            projectId: 'foo',
            defaultReviewEnvironment: 'review',
            gitBranch: 'feature/foo-123-bar'
        ]
        def uut = new OdsContext(script, config, logger) {
            protected boolean environmentExists(String name) {
                existingEnvironments.contains(name)
            }
        }


        config.workflow ='git-flow'
        existingEnvironments = ['prod', 'dev']

        config.autoCreateReviewEnvironment = true
        assertEquals("review-123", uut.determineEnvironment())

        config.autoCreateReviewEnvironment = false
        existingEnvironments = ['prod', 'dev', 'review']
        assertEquals("review", uut.determineEnvironment())

        config.autoCreateReviewEnvironment = false
        existingEnvironments = ['prod', 'dev']
        assertEquals("", uut.determineEnvironment())


        config.workflow ='GitHub Flow'
        existingEnvironments = ['prod']

        config.autoCreateReviewEnvironment = true
        assertEquals("review-123", uut.determineEnvironment())

        config.autoCreateReviewEnvironment = false
        existingEnvironments = ['prod', 'review']
        assertEquals("review", uut.determineEnvironment())

        config.autoCreateReviewEnvironment = false
        existingEnvironments = ['prod']
        assertEquals("", uut.determineEnvironment())
    }
}
