package org.ods.component

import org.ods.PipelineScript
import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.services.SonarQubeService
import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase

class ScanWithSonarStageSpec extends PipelineSpockTestBase {

    ScanWithSonarStage createStage() {
        def script = Spy(PipelineScript)
        def steps = Spy(util.PipelineSteps)
        def logger = Spy(new Logger(steps, false))
        IContext context = new Context(steps,
            [componentId: "component1",
             projectId: "prj1",
             buildUrl: "http://build",
             buildNumber: "56",
             repoName: "component1",
             gitCommit: "12112121212121",
             cdProject: "prj1-cd",
             credentialsId: "cd-user",
             branchToEnvironmentMapping: [
                 '*': 'dev'
                ]
             ], logger)
        def config = [:]
        def bitbucket = Spy(new BitbucketService (steps,
            'https://bitbucket.example.com',
            'FOO',
            'foo-cd-cd-user-with-password',
            logger))
        def sonarQube = Spy(new SonarQubeService(script, logger, "SonarServerConfig"))
        def nexus = Spy(new NexusService ("http://nexus", "user", "pass"))
        def stage = new ScanWithSonarStage (
            script,
            context,
            config,
            bitbucket,
            sonarQube,
            nexus,
            logger
        )

        return stage
    }

    def "create Bitbucket Insight report - PASS"() {
        given:
        def stage = createStage()
        def data = [
            key: ScanWithSonarStage.BITBUCKET_SONARQUBE_REPORT_KEY,
            title: "SonarQube",
            details: "Please visit the following links to review the SonarQube report:",
            result: "PASS"
        ]

        when:
        stage.createBitbucketCodeInsightReport()

        then:
        1 * stage.bitbucket.createCodeInsightReport(data, stage.context.repoName, stage.context.gitCommit)
    }
}
