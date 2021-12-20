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
        // To use test steps class
        stage.metaClass.steps = steps

        return stage
    }

    def "generate temp file from report file"() {
        given:
        def stage = createStage()

        when:
        def file = stage.generateTempFileFromReport("report.md")

        then:
        assert file.exists()
        assert file.text == ""
    }

    def "archive report in Nexus"() {
        given:
        def stage = createStage()
        def file = File.createTempFile("temp", ".md", new File(System.getProperty("java.io.tmpdir") as String))

        when:
        stage.generateAndArchiveReportInNexus(file, "leva-documentation")

        then:
        1 * stage.nexus.storeArtifact("leva-documentation", _, "report.pdf", _, "application/pdf") >>
            new URI("http://nexus/repository/leva-documentation/prj1/12345-56/sonarQube/report.pdf")
        1 * stage.logger.info("Report stored in: http://nexus/repository/leva-documentation/prj1/12345-56/sonarQube/report.pdf")
    }

    def "create Bitbucket Insight report - PASS"() {
        given:
        def stage = createStage()
        def data = [
            key: ScanWithSonarStage.BITBUCKET_SONARQUBE_REPORT_KEY,
            title: "SonarQube",
            link: "http://nexus",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in SonarQube",
                    link: "https://sonarqube.example.com/dashboard?id=prj1-component1"
                ],
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: "http://nexus"
                ]
            ],
            details: "Please visit the following links to review the SonarQube report:",
            result: "PASS"
        ]

        when:
        stage.createBitbucketCodeInsightReport("OK", "http://nexus", "prj1-component1")

        then:
        1 * stage.bitbucket.createCodeInsightReport(data, stage.context.repoName, stage.context.gitCommit)
    }

    def "create Bitbucket Insight report - FAIL"() {
        given:
        def stage = createStage()
        def data = [
            key: ScanWithSonarStage.BITBUCKET_SONARQUBE_REPORT_KEY,
            title: "SonarQube",
            link: "http://nexus",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in SonarQube",
                    link: "https://sonarqube.example.com/dashboard?id=prj1-component1"
                ],
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: "http://nexus"
                ]
            ],
            details: "Please visit the following links to review the SonarQube report:",
            result: "FAIL"
        ]

        when:
        stage.createBitbucketCodeInsightReport("ERROR", "http://nexus", "prj1-component1")

        then:
        1 * stage.bitbucket.createCodeInsightReport(data, stage.context.repoName, stage.context.gitCommit)
    }
}
