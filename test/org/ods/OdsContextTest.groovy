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

    void testDetermineEnvironment() {

        uut = pipeline.context

        // DevTest Mode
        config.autoCreateEnvironment = false
        config.gitBranch
        assertEquals("test", uut.determineEnvironment(config.gitBranch, config.projectId, true))

        config.gitBranch = "develop"
        assertEquals("dev", uut.determineEnvironment(config.gitBranch, config.projectId, true))

        config.gitBranch = "PSP-111"
        assertEquals("dev", uut.determineEnvironment(config.gitBranch, config.projectId, true))

        config.gitBranch = "PSP-111#-something"
        assertTrue(uut.determineEnvironment(config.gitBranch, config.projectId, true).endsWith("-dev"))

        // Multi Environments Mode
        config.gitBranch = "master"
        config.autoCreateEnvironment = true
        assertEquals("test", uut.determineEnvironment(config.gitBranch, config.projectId, true))

        config.gitBranch = "develop"
        assertEquals("dev", uut.determineEnvironment(config.gitBranch, config.projectId, true))

        config.gitBranch = "master"
        config.projectId = "psp"
        config.gitBranch = "feature/" + config.projectId.toUpperCase() + "-ABC"
        assertEquals("dev", uut.determineEnvironment(config.gitBranch, config.projectId, true))

        config.gitBranch = "something-else"
        assertEquals("", uut.determineEnvironment(config.gitBranch, config.projectId, true))

        config.gitBranch = "something_without_a_minus"
        assertEquals("", uut.determineEnvironment(config.gitBranch, config.projectId, true))

    }

}
