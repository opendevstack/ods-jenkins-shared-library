package org.ods

class OdsContextTest extends GroovyTestCase {

    LinkedHashMap<String, String> config
    PipelineScript script
    OdsPipeline pipeline
    Context uut

    protected void setUp() {

        config = [gitBranch: 'master', projectId:'PSP', autoCreateEnvironment:'true']

        script = new PipelineScript()

        Logger logger = new Logger() {
            @Override
            void echo(String message) {
                script.echo(message)
            }

            @Override
            void verbose(String message) {
                script.echo(message)
            }

            @Override
            void error(String message) {
                script.echo(message)
            }
        }

        pipeline = new OdsPipeline(script, config, logger)

        uut = pipeline.context

    }

    void testGivenDetermineEnvironment_whenAutoCreateEqualsFalse() {

        uut = pipeline.context

        // DevTest Mode
        config.autoCreateEnvironment = false
        config.projectId = "psp"

        config.gitBranch = "dev"
        assertEquals("dev", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "master"
        assertEquals("test", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "uat"
        assertEquals("uat", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "prod"
        assertEquals("prod", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "feature/PSP-111"
        assertEquals("dev", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "feature/PSP-111#-something"
        assertEquals("dev", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "bugfix/" + config.projectId.toUpperCase() + "-444-something-else"
        assertEquals("dev", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "hotfix/" + config.projectId.toUpperCase() + "-444-something-else"
        assertEquals("dev", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "release/" + config.projectId.toUpperCase() + "-v1_1_1"
        assertEquals("dev", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "something_without_a_minus"
        assertEquals("", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

    }

    void testGivenDetermineEnvironment_whenAutoCreateEqualsTrue() {

        uut = pipeline.context

        // Multi Environments Mode
        config.autoCreateEnvironment = true
        config.projectId = "psp"

        config.gitBranch = "dev"
        assertEquals("dev", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "master"
        assertEquals("test", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "uat"
        assertEquals("uat", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "prod"
        assertEquals("prod", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "feature/" + config.projectId.toUpperCase() + "-ABC"
        assertEquals("dev", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))


        config.gitBranch = "something-else"
        assertEquals("", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "something_without_a_minus"
        assertEquals("", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "bugfix/" + config.projectId.toUpperCase() + "-444-something-else"
        assertEquals("dev", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "hotfix/" + config.projectId.toUpperCase() + "-444-something-else"
        assertEquals("dev", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "feature/PSP-111#-something"
        assertEquals("111#-dev", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

        config.gitBranch = "release/" + config.projectId.toUpperCase() + "-v1_1_1"
        assertEquals("v1_1_1-rel", uut.determineEnvironment(config.gitBranch, config.projectId, config.autoCreateEnvironment))

    }


}
