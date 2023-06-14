package org.ods.component

import org.ods.PipelineScript
import org.ods.services.TrivyService
import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase

class ScanWithTrivyStageSpec extends PipelineSpockTestBase {

    ScanWithTrivyStage createStage() {
        def script = Spy(PipelineScript)
        def steps = Spy(util.PipelineSteps)
        def logger = Spy(new Logger(steps, false))
        IContext context = new Context(steps,
            [componentId: "component1",
             projectId: "prj1",
             buildUrl: "http://buidl",
             buildNumber: "56",
             repoName: "component1",
             gitCommit: "12112121212121",
             cdProject: "prj1-cd",
             credentialsId: "cd-user"], logger)
        def trivy = Spy(new TrivyService(steps, logger))
        def config = [:]
        def bitbucket = Spy(new BitbucketService (steps,
            'https://bitbucket.example.com',
            'FOO',
            'foo-cd-cd-user-with-password',
            logger))
        def nexus = Spy(new NexusService ("http://nexus", "user", "pass"))
        def openShift = Spy(new OpenShiftService (steps, logger))
        def stage = new ScanWithTrivyStage(
            script,
            context,
            config,
            trivy,
            bitbucket,
            nexus,
            openShift,
            logger
        )

        return stage
    }

    def "archive report in Jenkins if stage not launched by orchestration pipeline"() {
        given:
        def stage = createStage()

        when:
        stage.archiveReportInJenkins(true, "trivy-sbom.json")

        then:
        1 * stage.script.sh(_) >> {
            assert it.label == ['Create artifacts dir']
            assert it.script == ['mkdir -p artifacts']
        }
        1 * stage.script.sh(_) >> {
            assert it.label == ['Rename report to SCSR']
            assert it.script == ['mv trivy-sbom.json artifacts/SCSR-prj1-component1-trivy-sbom.json']
        }
        1 * stage.script.archiveArtifacts(_) >> {
            assert it.artifacts == ['artifacts/SCSR*']
        }
        1 * stage.script.stash(_) >> {
            assert it.name == ['scsr-report-component1-56']
            assert it.includes == ['artifacts/SCSR*']
            assert it.allowEmpty == [true]
        }

    }

    def "archive report in Jenkins if stage launched by orchestration pipeline"() {
        given:
        def stage = createStage()

        when:
        stage.archiveReportInJenkins(false, "trivy-sbom.json")

        then:
        1 * stage.script.sh(_) >> {
            assert it.label == ['Create artifacts dir']
            assert it.script == ['mkdir -p artifacts']
        }
        1 * stage.script.sh(_) >> {
            assert it.label == ['Rename report to SCSR']
            assert it.script == ['mv trivy-sbom.json artifacts/SCSR-prj1-component1-trivy-sbom.json']
        }
        0 * stage.script.archiveArtifacts(_) >> {
            assert it.artifacts == ['artifacts/SCSR*']
        }
        1 * stage.script.stash(_) >> {
            assert it.name == ['scsr-report-component1-56']
            assert it.includes == ['artifacts/SCSR*']
            assert it.allowEmpty == [true]
        }

    }

    def "archive report in Nexus"() {
        given:
        def stage = createStage()

        when:
        stage.archiveReportInNexus("trivy-sbom.json", "leva-documentation")

        then:
        1 * stage.script.readFile([file: "trivy-sbom.json"]) >> "Cool report"
        1 * stage.nexus.storeArtifact("leva-documentation", _, "trivy-sbom.json", _, "json") >>
            new URI("http://nexus/repository/leva-documentation/prj1/12345-56/trivy/trivy-sbom.json")
        1 * stage.logger.info("Report stored in: http://nexus/repository/leva-documentation/prj1/12345-56/trivy/trivy-sbom.json")
    }

    def "create Bitbucket Insight report - PASS"() {
        given:
        def stage = createStage()
        def data = [
            key: ScanWithTrivyStage.BITBUCKET_TRIVY_REPORT_KEY,
            title: "Trivy Security",
            link: "http://nexus",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: "http://nexus"
                ]
            ],
            details: "Please visit the following link to review the Trivy Security scan report:",
            result: "PASS"
        ]

        when:
        stage.createBitbucketCodeInsightReport("http://nexus", 0, null)

        then:
        1 * stage.bitbucket.createCodeInsightReport(data, stage.context.repoName, stage.context.gitCommit)
    }

    def "create Bitbucket Insight report - FAIL"() {
        given:
        def stage = createStage()
        def data = [
            key: ScanWithTrivyStage.BITBUCKET_TRIVY_REPORT_KEY,
            title: "Trivy Security",
            link: "http://nexus",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: "http://nexus"
                ]
            ],
            details: "Please visit the following link to review the Trivy Security scan report:",
            result: "FAIL"
        ]

        when:
        stage.createBitbucketCodeInsightReport("http://nexus", 1, null)

        then:
        1 * stage.bitbucket.createCodeInsightReport(data, stage.context.repoName, stage.context.gitCommit)
    }

    def "create Bitbucket Insight report - Messages"() {
        given:
        def stage = createStage()
        def data = [
            key: ScanWithTrivyStage.BITBUCKET_TRIVY_REPORT_KEY,
            title: "Trivy Security",
            messages: [
                [
                    title: "Messages",
                    value: "Message"
                ]
            ],
            details: "There was some problems with Trivy:",
            result: "FAIL"
        ]

        when:
        stage.createBitbucketCodeInsightReport('Message')

        then:
        1 * stage.bitbucket.createCodeInsightReport(data, stage.context.repoName, stage.context.gitCommit)
    }

    // def "scan with CLI - SUCCESS"() {
    //     given:
    //     def stage = createStage()
    //     OpenShiftService openShift = Mock(OpenShiftService.class) 
    //     ServiceRegistry.instance.add(OpenShiftService, openShift)
    //     openShift.getApplicationDomain("foo-cd") >> "openshift-domain.com"

    //     when:
    //     def result = stage.scanViaCli("vuln,config,secret,license", "os,library", "cyclonedx",
    //         ["--debug", "--timeout=10m"], "trivy-sbom.json", "docker-group-ods")

    //     then:
    //     1 * stage.trivy.scanViaCli("vuln,config,secret,license", "os,library",
    //        "cyclonedx", "--debug --timeout=10m", "trivy-sbom.json", "openshift-domain.com", "docker-group-ods") >> TrivyService.TRIVY_SUCCESS
    //     1 * stage.logger.info("Finished scan via Trivy CLI successfully!")
    //     TrivyService.TRIVY_SUCCESS == result
    // }

    // def "scan with CLI - Operational Error"() {
    //     given:
    //     def stage = createStage()
    //     OpenShiftService openShift = Mock(OpenShiftService.class)

    //     when:
    //     def result = stage.scanViaCli("vuln,config,secret,license", "os,library", "cyclonedx",
    //         ["--debug", "--timeout=10m"], "trivy-sbom.json", "docker-group-ods")

    //     then:
    //     1 * stage.trivy.scanViaCli("vuln,config,secret,license", "os,library",
    //        "cyclonedx", "--debug --timeout=10m", "trivy-sbom.json", "openshift-domain.com", "docker-group-ods") >> TrivyService.TRIVY_OPERATIONAL_ERROR
    //     1 * stage.logger.info("An error occurred in processing the scan request " +
    //         "(e.g. invalid command line options, image not pulled, operational error).")
    //     TrivyService.TRIVY_OPERATIONAL_ERROR == result
    // }

//     def "scan with CLI - Error"() {
//         given:
//         def stage = createStage()

//         when:
//         def result = stage.scanViaCli("http://aqua", "internal", "12345",
//             "cd-user", "trivy-sbom.json", "report.json")

//         then:
//         1 * stage.aqua.scanViaCli("http://aqua", "internal", "12345",
//             "cd-user", "trivy-sbom.json", "report.json", ScanWithAquaStage.AQUA_DEFAULT_TIMEOUT) >> 127
//         1 * stage.logger.info("An unknown return code was returned: 127")
//         127 == result
//     }

}
