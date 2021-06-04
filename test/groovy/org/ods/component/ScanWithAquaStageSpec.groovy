package org.ods.component

import org.ods.PipelineScript
import org.ods.services.AquaService
import org.ods.services.BitbucketService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import org.ods.util.Logger
import spock.lang.Ignore
import vars.test_helper.PipelineSpockTestBase

class ScanWithAquaStageSpec extends PipelineSpockTestBase {

    ScanWithAquaStage createStage() {
        def script = Spy(PipelineScript)
        def steps = Spy(util.PipelineSteps)
        def logger = Spy(new Logger(steps, false))
        IContext context = new Context(steps,
            [componentId: "component1",
             projectId: "prj1",
             buildUrl: "http://buidl",
             buildNumber: "56",
             repoName: "component1",
             gitCommit: "12112121212121"], logger)
        def config = [:]
        def aqua = Spy(new AquaService(steps, logger))
        def bitbucket = Spy(new BitbucketService (steps,
            'https://bitbucket.example.com',
            'FOO',
            'foo-cd-cd-user-with-password',
            logger))
        def openShift = Spy(new OpenShiftService (steps, logger))
        def stage = new ScanWithAquaStage(
            script,
            context,
            config,
            aqua,
            bitbucket,
            openShift,
            logger
        )

        return stage
    }

    def "send mails with messages"() {
        given:
        def stage = createStage()

        when:
        stage.notifyAquaProblem("user1@mail.com, user2@mail,com", "<li>Cool message<li>")

        then:
        1 * stage.script.emailext(_) >> {
            assert it.body == ['<p>Build component1 on project prj1 had some problems with Aqua!</p> <p>URL : ' +
                                   '<a href="http://buidl">http://buidl</a></p> <ul><li>Cool message<li></ul>']
            assert it.mimeType == ['text/html']
            assert it.replyTo == ['$script.DEFAULT_REPLYTO'] // Retrieved in Jenkins email template
            assert it.subject == ['Build component1 on project prj1 had some problems with Aqua!']
            assert it.to == ['user1@mail.com, user2@mail,com']
        }
    }

    def "don't send mails with empty message"() {
        given:
        def stage = createStage()

        when:
        stage.notifyAquaProblem("user1@mail.com, user2@mail,com")

        then:
        0 * stage.script.emailext(_)
    }

    def "archive report if stage not launched by orchestration pipeline"() {
        given:
        def stage = createStage()

        when:
        stage.archiveReport(true, "report.html")

        then:
        1 * stage.script.sh(_) >> {
            assert it.label == ['Create artifacts dir']
            assert it.script == ['mkdir -p artifacts']
        }
        1 * stage.script.sh(_) >> {
            assert it.label == ['Rename report to SCSR']
            assert it.script == ['mv report.html artifacts/SCSR-prj1-component1-report.html']
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

    def "archive report if stage launched by orchestration pipeline"() {
        given:
        def stage = createStage()

        when:
        stage.archiveReport(false, "report.html")

        then:
        1 * stage.script.sh(_) >> {
            assert it.label == ['Create artifacts dir']
            assert it.script == ['mkdir -p artifacts']
        }
        1 * stage.script.sh(_) >> {
            assert it.label == ['Rename report to SCSR']
            assert it.script == ['mv report.html artifacts/SCSR-prj1-component1-report.html']
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

    def "create Bitbucket Insight report - PASS"() {
        given:
        def stage = createStage()

        when:
        stage.createBitbucketCodeInsightReport("http://aqua", "internal", "12345", 0)

        then:
        1 * stage.bitbucket.createCodeInsightReport("http://aqua/#/images/internal/12345/vulns",
            stage.context.repoName, stage.context.gitCommit,
            "Aqua Security","Please visit the following link to review the Aqua Security scan report:",
            "PASS")

    }

    def "create Bitbucket Insight report - FAIL"() {
        given:
        def stage = createStage()

        when:
        stage.createBitbucketCodeInsightReport("http://aqua", "internal", "12345", 1)

        then:
        1 * stage.bitbucket.createCodeInsightReport("http://aqua/#/images/internal/12345/vulns",
            stage.context.repoName, stage.context.gitCommit,
            "Aqua Security","Please visit the following link to review the Aqua Security scan report:",
            "FAIL")

    }

    def "scan with CLI - SUCCESS"() {
        given:
        def stage = createStage()

        when:
        def result = stage.scanViaCli("http://aqua", "internal", "12345",
            "cd-user", "report.html", "report.json")

        then:
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "12345",
            "cd-user", "report.html", "report.json") >> AquaService.AQUA_SUCCESS
        1 * stage.logger.info("Finished scan via Aqua CLI successfully!")
        AquaService.AQUA_SUCCESS == result
    }

    def "scan with CLI - Policies Error"() {
        given:
        def stage = createStage()

        when:
        def result = stage.scanViaCli("http://aqua", "internal", "12345",
            "cd-user", "report.html", "report.json")

        then:
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "12345",
            "cd-user", "report.html", "report.json") >> AquaService.AQUA_POLICIES_ERROR
        1 * stage.logger.info("The image scanned failed at least one of the Image Assurance Policies specified.")
        AquaService.AQUA_POLICIES_ERROR == result
    }

    def "scan with CLI - Operational Error"() {
        given:
        def stage = createStage()

        when:
        def result = stage.scanViaCli("http://aqua", "internal", "12345",
            "cd-user", "report.html", "report.json")

        then:
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "12345",
            "cd-user", "report.html", "report.json") >> AquaService.AQUA_OPERATIONAL_ERROR
        1 * stage.logger.info("An error occurred in processing the scan request " +
            "(e.g. invalid command line options, image not pulled, operational error).")
        AquaService.AQUA_OPERATIONAL_ERROR == result
    }

    def "scan with CLI - Error"() {
        given:
        def stage = createStage()

        when:
        def result = stage.scanViaCli("http://aqua", "internal", "12345",
            "cd-user", "report.html", "report.json")

        then:
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "12345",
            "cd-user", "report.html", "report.json") >> 127
        1 * stage.logger.info("An unknown return code was returned: 127")
        127 == result
    }

    def "get image ref of not existing image"() {
        given:
        def stage = createStage()

        when:
        def result = stage.getImageRef()

        then:
        null == result
    }

    @Ignore
    def "get image ref of existing image"() {
        given:
        def stage = createStage()
        stage.context.addBuildToArtifactURIs("resourceName", [image: "image1/image1:2323232323"])

        when:
        def result = stage.getImageRef()

        then:
        1 == result
    }

}
