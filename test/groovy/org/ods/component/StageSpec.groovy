package org.ods.component

import org.ods.util.PipelineSteps
import org.ods.util.Logger
import org.ods.util.ILogger
import vars.test_helper.PipelineSpockTestBase
import spock.lang.*

class StageSpec extends PipelineSpockTestBase {

    class TestStage extends Stage {
        public final String STAGE_NAME = 'Test'
        protected Map<String, Object> options
        public boolean ran
        TestStage(def script, IContext context, Map<String, Object> config, ILogger logger) {
            super(script, context, logger)
            this.script = script
            this.context = context
            this.logger = logger
            this.options = config
            this.ran = false
        }
        // empty "run" that just records it has been called
        def run() {
            this.ran = true
        }
    }

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
        Logger logger = new Logger (script, true)
        IContext context = new Context(script, [gitBranch: gitBranch], logger)
        def testStage = new TestStage(script, context, config, logger)
        testStage.execute()

        then:
        if (expectedLogLine) {
            assertCallStackContains(expectedLogLine)
        }
        assertJobStatusSuccess()
        testStage.ran == expectedRan

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
