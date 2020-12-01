package org.ods.component

import org.ods.PipelineScript
import org.ods.util.Logger
import org.ods.util.ILogger
import vars.test_helper.PipelineSpockTestBase
import spock.lang.*

class StageSpec extends PipelineSpockTestBase {

    private Logger logger = Mock(Logger)

    @Unroll
    def "when gitBranch is #gitBranch and configBranch is #configBranch and configBranches is #configBranches then run should be #expectedRan"(gitBranch, configBranch, configBranches, expectedRan) {
        given:
        def config = [:]
        if (configBranch != null) {
            config.branch = configBranch
        }
        if (configBranches != null) {
            config.branches = configBranches
        }
        def script = loadScript('vars/withStage.groovy')

        when:
        def ran = false
        IContext context = new Context(null, [gitBranch: gitBranch], logger)
        def stage = new Stage(script, context, config, logger) {
            public final String STAGE_NAME = 'Test'
            def run() {
                ran = true
            }
        }
        stage.execute()

        then:
        ran == expectedRan

        where:
        gitBranch             | configBranch        | configBranches        || expectedRan
        'master'              | null                | null                  || true
        'master'              | null                | ['*']                 || true
        'master'              | '*'                 | null                  || true
        'develop'             | 'master'            | null                  || false
        'develop'             | null                | ['master']            || false
        'develop'             | 'master,develop'    | null                  || true
        'develop'             | null                | ['master', 'develop'] || true
        'release/foo'         | 'release/'          | null                  || true
        'release/foo'         | null                | ['release/']          || true
    }
}
