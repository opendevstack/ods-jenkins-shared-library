package org.ods.component

import org.ods.util.PipelineSteps
import org.ods.util.Logger
import org.ods.util.ILogger
import vars.test_helper.PipelineSpockTestBase
import spock.lang.*

class StageSpec extends PipelineSpockTestBase {

    @Unroll
    def "when gitBranch=#gitBranch and configBranch=#configBranch and configBranches=#configBranches then run should=#expectedRan"(gitBranch, configBranch, configBranches, expectedRan, expectedLogLine) {
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
        Logger logger = new Logger (script, true)
        IContext context = new Context(script, [gitBranch: gitBranch], logger)
        def stage = new Stage(script, context, config, logger) {
            public final String STAGE_NAME = 'Test'
            def run() {
                ran = true
            }
        }
        stage.execute()

        then:
        if (expectedLogLine) {
            assertCallStackContains(expectedLogLine)
        }
        assertJobStatusSuccess()
        ran == expectedRan

        where:
        gitBranch             | configBranch        | configBranches         || expectedRan | expectedLogLine
        'master'              | null                | null                   || true        | null
        'master'              | null                | ['*']                  || true        | null
        'master'              | '*'                 | null                   || true        | null
        'develop'             | 'master'            | null                   || false       | "Skipping stage 'Test' for branch 'develop' as it is not covered by: 'master'."
        'develop'             | null                | ['master']             || false       | "Skipping stage 'Test' for branch 'develop' as it is not covered by: 'master'."
        'develop'             | null                | ['master', 'release/'] || false       | "Skipping stage 'Test' for branch 'develop' as it is not covered by: 'master', 'release/'."
        'develop'             | 'master,develop'    | null                   || true        | null
        'develop'             | null                | ['master', 'develop']  || true        | null
        'release/foo'         | 'release/'          | null                   || true        | null
        'release/foo'         | null                | ['release/']           || true        | null
    }
}
