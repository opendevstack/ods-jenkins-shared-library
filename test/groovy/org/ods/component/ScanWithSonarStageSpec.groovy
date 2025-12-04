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
        script.readProperties(_) >> [:] // Add stub for readProperties
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
             buildTime: new Date(),
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
        def nexus = Mock(NexusService)
        def stage = new ScanWithSonarStage (
            script,
            context,
            config,
            bitbucket,
            sonarQube,
            nexus,
            logger,
            [:], // configurationSonarCluster
            [:]  // configurationSonarProject
        )
        // To use test steps class
        stage.metaClass.script = script

        return stage
    }

    def "generateAndArchiveReportInNexus stores artifact in Nexus"() {
        given:
        def tempFolderPath = tempFolder.getRoot().absolutePath
        def stage = createStage(tempFolderPath)
        def targetReport = "sonarqube-report-prj1-component1.pdf"
        
        // Create a mock PDF file in artifacts directory
        def artifactsDir = new File(tempFolderPath, "artifacts")
        artifactsDir.mkdirs()
        def reportFile = new File(artifactsDir, targetReport)
        reportFile.text = "mock pdf content"
        
        stage.script.readFile(_) >> "mock pdf content"

        when:
        def result = stage.generateAndArchiveReportInNexus(targetReport, "leva-documentation")

        then:
        1 * stage.nexus.storeArtifact("leva-documentation", _, "report.pdf", _, "application/pdf") >>
            new URI("http://nexus/repository/leva-documentation/prj1/component1/2023-01-01_12-30-45_56/sonarQube/report.pdf")
        1 * stage.logger.info(_)
        result.toString().contains("nexus/repository/leva-documentation")
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

    def "stage uses exclusions from configurationSonarCluster and logs them"() {
        given:
        def tempFolderPath = tempFolder.getRoot().absolutePath
        def script = Spy(PipelineSteps)
        script.env.WORKSPACE = tempFolderPath
        def logger = Spy(new Logger(script, false))
        def config = [:]
        def configurationSonarCluster = [exclusions: "**/test/**"]
        def stage = new ScanWithSonarStage(
            script,
            new Context(script, [
                componentId: "component1",
                projectId: "prj1",
                buildUrl: "http://build",
                buildNumber: "56",
                repoName: "component1",
                gitCommit: "12112121212121",
                cdProject: "prj1-cd",
                credentialsId: "cd-user",
                branchToEnvironmentMapping: ['*': 'dev']
            ], logger),
            config,
            Spy(new BitbucketService(script, 'https://bitbucket.example.com', 'FOO', 'foo-cd-cd-user-with-password', logger)),
            Spy(new SonarQubeService(script, logger, "SonarServerConfig")),
            Spy(new NexusService("http://nexus", script, "foo-cd-cd-user-with-password")),
            logger,
            configurationSonarCluster,
            [:]
        )

        when:
        stage.run()

        then:
        1 * stage.logger.info("SonarQube scan will run for the entire repository source code. The following exclusions will be applied: **/test/**")
        thrown(Exception)
    }

    def "stage uses nexusRepository from configurationSonarCluster"() {
        given:
        def tempFolderPath = tempFolder.getRoot().absolutePath
        def script = Spy(PipelineSteps)
        script.readProperties([file: 'sonar-project.properties']) >> [:]
        script.readProperties(_) >> [:] // fallback for any other call
        def logger = Spy(new Logger(script, false))
        def config = [:]
        def configurationSonarCluster = [nexusRepository: "custom-nexus"]
        def stage = new ScanWithSonarStage(
            script,
            new Context(script, [
                componentId: "component1",
                projectId: "prj1",
                buildUrl: "http://build",
                buildNumber: "56",
                repoName: "component1",
                gitCommit: "12112121212121",
                cdProject: "prj1-cd",
                credentialsId: "cd-user",
                branchToEnvironmentMapping: ['*': 'dev']
            ], logger),
            config,
            Spy(new BitbucketService(script, 'https://bitbucket.example.com', 'FOO', 'foo-cd-cd-user-with-password', logger)),
            Spy(new SonarQubeService(script, logger, "SonarServerConfig")),
            Spy(new NexusService("http://nexus", script, "foo-cd-cd-user-with-password")),
            logger,
            configurationSonarCluster,
            [:]
        )

        expect:
        stage.options.sonarQubeNexusRepository == "custom-nexus"
    }

    def "stage uses sonarQubeAccount and sonarQubeProjectsPrivate defaults"() {
        given:
        def tempFolderPath = tempFolder.getRoot().absolutePath
        def script = Spy(PipelineSteps)
        script.env.WORKSPACE = tempFolderPath
        def logger = Spy(new Logger(script, false))
        def config = [:]
        def configurationSonarCluster = [:] // no values set
        def stage = new ScanWithSonarStage(
            script,
            new Context(script, [
                componentId: "component1",
                projectId: "prj1",
                buildUrl: "http://build",
                buildNumber: "56",
                repoName: "component1",
                gitCommit: "12112121212121",
                cdProject: "prj1-cd",
                credentialsId: "cd-user",
                branchToEnvironmentMapping: ['*': 'dev']
            ], logger),
            config,
            Spy(new BitbucketService(script, 'https://bitbucket.example.com', 'FOO', 'foo-cd-cd-user-with-password', logger)),
            Spy(new SonarQubeService(script, logger, "SonarServerConfig")),
            Spy(new NexusService("http://nexus", script, "foo-cd-cd-user-with-password")),
            logger,
            configurationSonarCluster,
            [:]
        )
        expect:
        stage.sonarQubeAccount == "cd-user-with-password"
        stage.sonarQubeProjectsPrivate == false
    }

    def "stage uses custom sonarQubeAccount and sonarQubeProjectsPrivate true"() {
        given:
        def tempFolderPath = tempFolder.getRoot().absolutePath
        def script = Spy(PipelineSteps)
        script.env.WORKSPACE = tempFolderPath
        def logger = Spy(new Logger(script, false))
        def config = [:]
        def configurationSonarCluster = [sonarQubeAccount: "custom-account", sonarQubeProjectsPrivate: true]
        def stage = new ScanWithSonarStage(
            script,
            new Context(script, [
                componentId: "component1",
                projectId: "prj1",
                buildUrl: "http://build",
                buildNumber: "56",
                repoName: "component1",
                gitCommit: "12112121212121",
                cdProject: "prj1-cd",
                credentialsId: "cd-user",
                branchToEnvironmentMapping: ['*': 'dev']
            ], logger),
            config,
            Spy(new BitbucketService(script, 'https://bitbucket.example.com', 'FOO', 'foo-cd-cd-user-with-password', logger)),
            Spy(new SonarQubeService(script, logger, "SonarServerConfig")),
            Spy(new NexusService("http://nexus", script, "foo-cd-cd-user-with-password")),
            logger,
            configurationSonarCluster,
            [:]
        )
        expect:
        stage.sonarQubeAccount == "custom-account"
        stage.sonarQubeProjectsPrivate == true
    }

}
