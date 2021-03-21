package org.ods.component

import org.ods.util.Logger
import spock.lang.Specification
import vars.test_helper.PipelineSpockTestBase

class ScanWithSonarStageSpec extends PipelineSpockTestBase {
    def "Run"() {
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
        def scanSonarStage = new ScanWithSonarStage(script, context, config, logger)
        scanSonarStage.execute()

        then:
        assertJobStatusSuccess()
        scanSonarStage.ran == expectedRan

        where:
        gitBranch             | configBranch        | configBranches         || expectedRan | expectedLogLine
        'master'              | null                | null                   || true        | null
        'master'              | null                | ['*']                  || true        | null

    }
}
