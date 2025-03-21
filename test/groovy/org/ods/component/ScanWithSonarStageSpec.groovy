package org.ods.component

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import util.PipelineSteps
import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.services.SonarQubeService
import org.ods.util.Logger
import util.FixtureHelper
import vars.test_helper.PipelineSpockTestBase
import java.nio.file.Paths

class ScanWithSonarStageSpec extends PipelineSpockTestBase {

    @Rule
    public TemporaryFolder tempFolder

    @Override
    protected String readResource(String name) {
        return super.readResource(name)
    }

    ScanWithSonarStage createStage(tempFolderPath) {
        def script = Spy(PipelineSteps)
        script.env.WORKSPACE = tempFolderPath
        def logger = Spy(new Logger(script, false))
        IContext context = new Context(script,
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
        def bitbucket = Spy(new BitbucketService (script,
            'https://bitbucket.example.com',
            'FOO',
            'foo-cd-cd-user-with-password',
            logger))
        def sonarQube = Spy(new SonarQubeService(script, logger, "SonarServerConfig"))
        def nexus = Spy(new NexusService ("http://nexus", script, "foo-cd-cd-user-with-password"))
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
        stage.metaClass.script = script

        return stage
    }

    def "generate temp file from report file"() {
        given:
        def tempFolderPath = tempFolder.getRoot().absolutePath
        def stage = createStage(tempFolderPath)

        when:
        def file = stage.generateTempFileFromReport("report.md")

        then:
        assert file.exists()
        assert file.text == ""
    }

    def "archive report in Nexus"() {
        given:
        def tempFolderPath = tempFolder.getRoot().absolutePath
        def stage = createStage(tempFolderPath)
        def file =  new FixtureHelper().getResource("Test.md")

        when:
        stage.generateAndArchiveReportInNexus(file, "leva-documentation")

        then:
        1 * stage.nexus.storeArtifact("leva-documentation", _, "report.pdf", _, "application/pdf") >>
            new URI("http://nexus/repository/leva-documentation/prj1/12345-56/sonarQube/report.pdf")
        1 * stage.logger.info("Report stored in: http://nexus/repository/leva-documentation/prj1/12345-56/sonarQube/report.pdf")
    }

    def "create Bitbucket Insight report - PASS"() {
        given:
        def tempFolderPath = tempFolder.getRoot().absolutePath
        def stage = createStage(tempFolderPath)
        def edition = "enterprise"
        def branch = "develop"
        def data = [
            key: ScanWithSonarStage.BITBUCKET_SONARQUBE_REPORT_KEY,
            title: "SonarQube",
            link: "http://nexus",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in SonarQube",
                    link: "https://sonarqube.example.com/dashboard?id=prj1-component1&branch=develop"
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
        stage.createBitbucketCodeInsightReport("OK", "http://nexus", "prj1-component1", "enterprise", "develop")

        then:
        1 * stage.bitbucket.createCodeInsightReport(data, stage.context.repoName, stage.context.gitCommit)
    }

    def "create Bitbucket Insight report - FAIL"() {
        given:
        def tempFolderPath = tempFolder.getRoot().absolutePath
        def stage = createStage(tempFolderPath)
        def edition = "enterprise"
        def branch = "develop"
        def data = [
            key: ScanWithSonarStage.BITBUCKET_SONARQUBE_REPORT_KEY,
            title: "SonarQube",
            link: "http://nexus",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in SonarQube",
                    link: "https://sonarqube.example.com/dashboard?id=prj1-component1&branch=develop"
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
        stage.createBitbucketCodeInsightReport("ERROR", "http://nexus", "prj1-component1", "enterprise", "develop")

        then:
        1 * stage.bitbucket.createCodeInsightReport(data, stage.context.repoName, stage.context.gitCommit)
    }
}
