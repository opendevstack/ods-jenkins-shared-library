package org.ods.component

import org.ods.PipelineScript
import org.ods.services.AquaService
import org.ods.services.BitbucketService
import org.ods.services.OpenShiftService
import org.ods.util.Logger
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
             gitCommit: "12112121212121",
             cdProject: "prj1-cd",
             credentialsId: "cd-user"], logger)
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

    def "get image ref of non existing image"() {
        given:
        def stage = createStage()

        when:
        def result = stage.getImageRef()

        then:
        null == result
    }

    def "get image ref of existing image"() {
        given:
        def stage = createStage()
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        def result = stage.getImageRef()

        then:
        "image1:2323232323" == result
    }

    def "run the Stage with default credential - OK"() {
        given:
        def stage = createStage()
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Get configs
        1 * stage.openShift.getConfigMapData(ScanWithAquaStage.AQUA_GENERAL_CONFIG_MAP_PROJECT,
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
                enabled: true,
                alertEmails: "mail1@mail.com",
                url: "http://aqua",
                registry: "internal"
            ]

        1 * stage.openShift.getConfigMapData("prj1-cd", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true
        ]
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "image1:2323232323",
            "cd-user", "aqua-report.html", "aqua-report.json") >> AquaService.AQUA_SUCCESS
        // Read results
        1 * stage.script.readFile([file: "aqua-report.json"]) >> "[vulnerability_summary: [critical: 0, malware: 0]]"
        1 * stage.script.readJSON([text: "[vulnerability_summary: [critical: 0, malware: 0]]"]) >> [
            vulnerability_summary: [critical: 0, malware: 0]
        ]
        // Create report in Bitbucket
        1 * stage.bitbucket.createCodeInsightReport("http://aqua/#/images/internal/image1:2323232323/vulns",
            stage.context.repoName, stage.context.gitCommit,
            "Aqua Security","Please visit the following link to review the Aqua Security scan report:",
            "PASS")
        // Archive artifact
        1 * stage.script.sh(_) >> {
            assert it.label == ['Create artifacts dir']
            assert it.script == ['mkdir -p artifacts']
        }
        1 * stage.script.sh(_) >> {
            assert it.label == ['Rename report to SCSR']
            assert it.script == ['mv aqua-report.html artifacts/SCSR-prj1-component1-aqua-report.html']
        }
        1 * stage.script.archiveArtifacts(_) >> {
            assert it.artifacts == ['artifacts/SCSR*']
        }
        1 * stage.script.stash(_) >> {
            assert it.name == ['scsr-report-component1-56']
            assert it.includes == ['artifacts/SCSR*']
            assert it.allowEmpty == [true]
        }
        // No mail sent
        0 * stage.script.emailext(_)
        // No warnings
        0 * stage.logger.warn(_)
    }

    def "run the Stage with custom credential - OK"() {
        given:
        def stage = createStage()
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Get configs
        1 * stage.openShift.getConfigMapData(ScanWithAquaStage.AQUA_GENERAL_CONFIG_MAP_PROJECT,
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal",
            secretName: "custom-secret"
        ]

        1 * stage.openShift.getConfigMapData("prj1-cd", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true
        ]
        // No default cd-user
        0 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "image1:2323232323",
            "prj1-cd-custom-secret", "aqua-report.html", "aqua-report.json") >> AquaService.AQUA_SUCCESS
        // Read results
        1 * stage.script.readFile([file: "aqua-report.json"]) >> "[vulnerability_summary: [critical: 0, malware: 0]]"
        1 * stage.script.readJSON([text: "[vulnerability_summary: [critical: 0, malware: 0]]"]) >> [
            vulnerability_summary: [critical: 0, malware: 0]
        ]
        // Create report in Bitbucket
        1 * stage.bitbucket.createCodeInsightReport("http://aqua/#/images/internal/image1:2323232323/vulns",
            stage.context.repoName, stage.context.gitCommit,
            "Aqua Security","Please visit the following link to review the Aqua Security scan report:",
            "PASS")
        // Archive artifact
        1 * stage.script.sh(_) >> {
            assert it.label == ['Create artifacts dir']
            assert it.script == ['mkdir -p artifacts']
        }
        1 * stage.script.sh(_) >> {
            assert it.label == ['Rename report to SCSR']
            assert it.script == ['mv aqua-report.html artifacts/SCSR-prj1-component1-aqua-report.html']
        }
        1 * stage.script.archiveArtifacts(_) >> {
            assert it.artifacts == ['artifacts/SCSR*']
        }
        1 * stage.script.stash(_) >> {
            assert it.name == ['scsr-report-component1-56']
            assert it.includes == ['artifacts/SCSR*']
            assert it.allowEmpty == [true]
        }
        // No mail sent
        0 * stage.script.emailext(_)
        // No warnings
        0 * stage.logger.warn(_)
    }

    def "run the Stage without image - ERROR "() {
        given:
        def stage = createStage()

        when:
        stage.run()

        then:
        // Get configs
        1 * stage.openShift.getConfigMapData(ScanWithAquaStage.AQUA_GENERAL_CONFIG_MAP_PROJECT,
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal"
        ]

        1 * stage.openShift.getConfigMapData("prj1-cd", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true
        ]
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Email sent
        1 * stage.script.emailext(
            ['body':'<p>Build component1 on project prj1 had some problems with Aqua!</p> <p>URL : ' +
                '<a href="http://buidl">http://buidl</a></p> <ul><li>Skipping as imageRef could not be retrieved</li></ul>',
             'mimeType':'text/html',
             'replyTo':'$script.DEFAULT_REPLYTO',
             'subject':'Build component1 on project prj1 had some problems with Aqua!', 'to':'mail1@mail.com'])
        // Info log
        1 * stage.logger.info('Skipping as imageRef could not be retrieved. Possible reasons are:\n' +
            '-> The aqua stage runs before the image build stage and hence no new image was created yet.\n' +
            '-> The image build stage was not executed because the image was imported.\n' +
            '-> The aqua stage and the image build stage have different values for \'resourceName\' set.')
    }

    def "run the Stage with default credential - Error Policies"() {
        given:
        def stage = createStage()
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Get configs
        1 * stage.openShift.getConfigMapData(ScanWithAquaStage.AQUA_GENERAL_CONFIG_MAP_PROJECT,
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal"
        ]

        1 * stage.openShift.getConfigMapData("prj1-cd", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true
        ]
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "image1:2323232323",
            "cd-user", "aqua-report.html", "aqua-report.json") >> AquaService.AQUA_POLICIES_ERROR
        // Read results
        1 * stage.script.readFile([file: "aqua-report.json"]) >> "[vulnerability_summary: [critical: 0, malware: 0]]"
        1 * stage.script.readJSON([text: "[vulnerability_summary: [critical: 0, malware: 0]]"]) >> [
            vulnerability_summary: [critical: 0, malware: 0]
        ]
        // Create report in Bitbucket
        1 * stage.bitbucket.createCodeInsightReport("http://aqua/#/images/internal/image1:2323232323/vulns",
            stage.context.repoName, stage.context.gitCommit,
            "Aqua Security","Please visit the following link to review the Aqua Security scan report:",
            "FAIL")
        // Archive artifact
        1 * stage.script.sh(_) >> {
            assert it.label == ['Create artifacts dir']
            assert it.script == ['mkdir -p artifacts']
        }
        1 * stage.script.sh(_) >> {
            assert it.label == ['Rename report to SCSR']
            assert it.script == ['mv aqua-report.html artifacts/SCSR-prj1-component1-aqua-report.html']
        }
        1 * stage.script.archiveArtifacts(_) >> {
            assert it.artifacts == ['artifacts/SCSR*']
        }
        1 * stage.script.stash(_) >> {
            assert it.name == ['scsr-report-component1-56']
            assert it.includes == ['artifacts/SCSR*']
            assert it.allowEmpty == [true]
        }
        // Mail sent
        1 * stage.script.emailext(
            [
                'body':'<p>Build component1 on project prj1 had some problems with Aqua!</p> ' +
                '<p>URL : <a href="http://buidl">http://buidl</a></p> <ul><li>Error executing Aqua CLI</li></ul>',
                'mimeType':'text/html',
                'replyTo':'$script.DEFAULT_REPLYTO',
                'subject':'Build component1 on project prj1 had some problems with Aqua!',
                'to':'mail1@mail.com']
        )
        // No warnings
        0 * stage.logger.warn(_)
    }

    def "run the Stage with default credential - Error Vulnerabilities critical"() {
        given:
        def stage = createStage()
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Get configs
        1 * stage.openShift.getConfigMapData(ScanWithAquaStage.AQUA_GENERAL_CONFIG_MAP_PROJECT,
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal"
        ]

        1 * stage.openShift.getConfigMapData("prj1-cd", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true
        ]
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "image1:2323232323",
            "cd-user", "aqua-report.html", "aqua-report.json") >> AquaService.AQUA_SUCCESS
        // Read results
        1 * stage.script.readFile([file: "aqua-report.json"]) >> "[vulnerability_summary: [critical: 1, malware: 0]]"
        1 * stage.script.readJSON([text: "[vulnerability_summary: [critical: 1, malware: 0]]"]) >> [
            vulnerability_summary: [critical: 1, malware: 0]
        ]
        // Create report in Bitbucket
        1 * stage.bitbucket.createCodeInsightReport("http://aqua/#/images/internal/image1:2323232323/vulns",
            stage.context.repoName, stage.context.gitCommit,
            "Aqua Security","Please visit the following link to review the Aqua Security scan report:",
            "FAIL")
        // Archive artifact
        1 * stage.script.sh(_) >> {
            assert it.label == ['Create artifacts dir']
            assert it.script == ['mkdir -p artifacts']
        }
        1 * stage.script.sh(_) >> {
            assert it.label == ['Rename report to SCSR']
            assert it.script == ['mv aqua-report.html artifacts/SCSR-prj1-component1-aqua-report.html']
        }
        1 * stage.script.archiveArtifacts(_) >> {
            assert it.artifacts == ['artifacts/SCSR*']
        }
        1 * stage.script.stash(_) >> {
            assert it.name == ['scsr-report-component1-56']
            assert it.includes == ['artifacts/SCSR*']
            assert it.allowEmpty == [true]
        }
        // Mail sent
        0 * stage.script.emailext(_)
        // No warnings
        0 * stage.logger.warn(_)
    }

    def "run the Stage with default credential - Error Malware"() {
        given:
        def stage = createStage()
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Get configs
        1 * stage.openShift.getConfigMapData(ScanWithAquaStage.AQUA_GENERAL_CONFIG_MAP_PROJECT,
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal"
        ]

        1 * stage.openShift.getConfigMapData("prj1-cd", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true
        ]
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "image1:2323232323",
            "cd-user", "aqua-report.html", "aqua-report.json") >> AquaService.AQUA_SUCCESS
        // Read results
        1 * stage.script.readFile([file: "aqua-report.json"]) >> "[vulnerability_summary: [critical: 0, malware: 1]]"
        1 * stage.script.readJSON([text: "[vulnerability_summary: [critical: 0, malware: 1]]"]) >> [
            vulnerability_summary: [critical: 0, malware: 1]
        ]
        // Create report in Bitbucket
        1 * stage.bitbucket.createCodeInsightReport("http://aqua/#/images/internal/image1:2323232323/vulns",
            stage.context.repoName, stage.context.gitCommit,
            "Aqua Security","Please visit the following link to review the Aqua Security scan report:",
            "FAIL")
        // Archive artifact
        1 * stage.script.sh(_) >> {
            assert it.label == ['Create artifacts dir']
            assert it.script == ['mkdir -p artifacts']
        }
        1 * stage.script.sh(_) >> {
            assert it.label == ['Rename report to SCSR']
            assert it.script == ['mv aqua-report.html artifacts/SCSR-prj1-component1-aqua-report.html']
        }
        1 * stage.script.archiveArtifacts(_) >> {
            assert it.artifacts == ['artifacts/SCSR*']
        }
        1 * stage.script.stash(_) >> {
            assert it.name == ['scsr-report-component1-56']
            assert it.includes == ['artifacts/SCSR*']
            assert it.allowEmpty == [true]
        }
        // Mail sent
        0 * stage.script.emailext(_)
        // No warnings
        0 * stage.logger.warn(_)
    }

    def "run the Stage with default credential - Error CLI Operational"() {
        given:
        def stage = createStage()
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Get configs
        1 * stage.openShift.getConfigMapData(ScanWithAquaStage.AQUA_GENERAL_CONFIG_MAP_PROJECT,
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal"
        ]

        1 * stage.openShift.getConfigMapData("prj1-cd", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true
        ]
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "image1:2323232323",
            "cd-user", "aqua-report.html", "aqua-report.json") >> AquaService.AQUA_OPERATIONAL_ERROR
        // Mail sent
        1 * stage.script.emailext(
            [
                'body':'<p>Build component1 on project prj1 had some problems with Aqua!</p> ' +
                    '<p>URL : <a href="http://buidl">http://buidl</a></p> <ul><li>Error executing Aqua CLI</li></ul>',
                'mimeType':'text/html',
                'replyTo':'$script.DEFAULT_REPLYTO',
                'subject':'Build component1 on project prj1 had some problems with Aqua!',
                'to':'mail1@mail.com'
            ]
        )
        // No warnings
        0 * stage.logger.warn(_)
    }

    def "run the Stage with default credential - Error CLI Unspecified"() {
        given:
        def stage = createStage()
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Get configs
        1 * stage.openShift.getConfigMapData(ScanWithAquaStage.AQUA_GENERAL_CONFIG_MAP_PROJECT,
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal"
        ]

        1 * stage.openShift.getConfigMapData("prj1-cd", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true
        ]
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "image1:2323232323",
            "cd-user", "aqua-report.html", "aqua-report.json") >> 127
        // Mail sent
        1 * stage.script.emailext(
            [
                'body':'<p>Build component1 on project prj1 had some problems with Aqua!</p> ' +
                    '<p>URL : <a href="http://buidl">http://buidl</a></p> <ul><li>Error executing Aqua CLI</li></ul>',
                'mimeType':'text/html',
                'replyTo':'$script.DEFAULT_REPLYTO',
                'subject':'Build component1 on project prj1 had some problems with Aqua!',
                'to':'mail1@mail.com'
            ]
        )
        // No warnings
        0 * stage.logger.warn(_)
    }

    def "run the Stage with default credential - Disabled at project level"() {
        given:
        def stage = createStage()
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Get configs
        1 * stage.openShift.getConfigMapData(ScanWithAquaStage.AQUA_GENERAL_CONFIG_MAP_PROJECT,
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal"
        ]

        1 * stage.openShift.getConfigMapData("prj1-cd", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: false
        ]
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Mail sent
        1 * stage.script.emailext(
            [
                'body':'<p>Build component1 on project prj1 had some problems with Aqua!</p> ' +
                    '<p>URL : <a href="http://buidl">http://buidl</a></p> <ul><li>Skipping Aqua scan ' +
                    'because is not enabled at project level in \'aqua\' ConfigMap</li></ul>',
                'mimeType':'text/html',
                'replyTo':'$script.DEFAULT_REPLYTO',
                'subject':'Build component1 on project prj1 had some problems with Aqua!',
                'to':'mail1@mail.com'
            ]
        )
        // Warnings
        1 * stage.logger.warn("Skipping Aqua scan because is not enabled at project level in 'aqua' ConfigMap")
    }

    def "run the Stage with default credential - Disabled at cluster level"() {
        given:
        def stage = createStage()
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Get configs
        1 * stage.openShift.getConfigMapData(ScanWithAquaStage.AQUA_GENERAL_CONFIG_MAP_PROJECT,
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: false,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal"
        ]

        1 * stage.openShift.getConfigMapData("prj1-cd", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true
        ]
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Mail sent
        1 * stage.script.emailext(
            [
                'body':'<p>Build component1 on project prj1 had some problems with Aqua!</p> ' +
                    '<p>URL : <a href="http://buidl">http://buidl</a></p> <ul><li>Skipping Aqua scan ' +
                    'because is not enabled at cluster level in \'aqua\' ConfigMap in ods project</li></ul>',
                'mimeType':'text/html',
                'replyTo':'$script.DEFAULT_REPLYTO',
                'subject':'Build component1 on project prj1 had some problems with Aqua!',
                'to':'mail1@mail.com'
            ]
        )
        // Warnings
        1 * stage.logger.warn("Skipping Aqua scan because is not enabled at cluster level in 'aqua' ConfigMap in ods project")
    }

    def "run the Stage with default credential - Disabled at cluster and project"() {
        given:
        def stage = createStage()
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Get configs
        1 * stage.openShift.getConfigMapData(ScanWithAquaStage.AQUA_GENERAL_CONFIG_MAP_PROJECT,
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: false,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal"
        ]

        1 * stage.openShift.getConfigMapData("prj1-cd", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: false
        ]
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Mail sent
        1 * stage.script.emailext(
            [
                'body':'<p>Build component1 on project prj1 had some problems with Aqua!</p> ' +
                    '<p>URL : <a href="http://buidl">http://buidl</a></p> <ul><li>Skipping Aqua scan ' +
                    'because is not enabled nor cluster in ods project, nor project level in \'aqua\' ConfigMap</li></ul>',
                'mimeType':'text/html',
                'replyTo':'$script.DEFAULT_REPLYTO',
                'subject':'Build component1 on project prj1 had some problems with Aqua!',
                'to':'mail1@mail.com'
            ]
        )
        // Warnings
        1 * stage.logger.warn("Skipping Aqua scan because is not enabled nor cluster in ods project, nor project level in 'aqua' ConfigMap")
    }

    def "run the Stage with default credential - Error archiving report"() {
        given:
        def stage = createStage()
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Get configs
        1 * stage.openShift.getConfigMapData(ScanWithAquaStage.AQUA_GENERAL_CONFIG_MAP_PROJECT,
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal"
        ]

        1 * stage.openShift.getConfigMapData("prj1-cd", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true
        ]
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "image1:2323232323",
            "cd-user", "aqua-report.html", "aqua-report.json") >> AquaService.AQUA_SUCCESS
        // Read results
        1 * stage.script.readFile([file: "aqua-report.json"]) >> "[vulnerability_summary: [critical: 0, malware: 0]]"
        1 * stage.script.readJSON([text: "[vulnerability_summary: [critical: 0, malware: 0]]"]) >> [
            vulnerability_summary: [critical: 0, malware: 0]
        ]
        // Error creating report in Bitbucket
        1 * stage.bitbucket.createCodeInsightReport("http://aqua/#/images/internal/image1:2323232323/vulns",
            stage.context.repoName, stage.context.gitCommit,
            "Aqua Security","Please visit the following link to review the Aqua Security scan report:",
            "PASS") >> {
            throw new Exception ("Error bitbucket")
        }

        // Mail sent
        1 * stage.script.emailext(
            [
                'body':'<p>Build component1 on project prj1 had some problems with Aqua!</p> ' +
                    '<p>URL : <a href="http://buidl">http://buidl</a></p> <ul><li>Error archiving Aqua reports</li></ul>',
                'mimeType':'text/html',
                'replyTo':'$script.DEFAULT_REPLYTO',
                'subject':'Build component1 on project prj1 had some problems with Aqua!',
                'to':'mail1@mail.com'
            ]
        )
        // Warnings
        1 * stage.logger.warn('Error archiving the Aqua reports due to: java.lang.Exception: Error bitbucket')
    }

    def "run the Stage with default credential and without ConfigMap in project (default enabled) - OK"() {
        given:
        def stage = createStage()
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Get configs
        1 * stage.openShift.getConfigMapData(ScanWithAquaStage.AQUA_GENERAL_CONFIG_MAP_PROJECT,
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal"
        ]

        1 * stage.openShift.getConfigMapData("prj1-cd", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [:]
        1 * stage.logger.info("Not parameter 'enabled' at project level. Default enabled")

        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "image1:2323232323",
            "cd-user", "aqua-report.html", "aqua-report.json") >> AquaService.AQUA_SUCCESS
        // Read results
        1 * stage.script.readFile([file: "aqua-report.json"]) >> "[vulnerability_summary: [critical: 0, malware: 0]]"
        1 * stage.script.readJSON([text: "[vulnerability_summary: [critical: 0, malware: 0]]"]) >> [
            vulnerability_summary: [critical: 0, malware: 0]
        ]
        // Create report in Bitbucket
        1 * stage.bitbucket.createCodeInsightReport("http://aqua/#/images/internal/image1:2323232323/vulns",
            stage.context.repoName, stage.context.gitCommit,
            "Aqua Security","Please visit the following link to review the Aqua Security scan report:",
            "PASS")
        // Archive artifact
        1 * stage.script.sh(_) >> {
            assert it.label == ['Create artifacts dir']
            assert it.script == ['mkdir -p artifacts']
        }
        1 * stage.script.sh(_) >> {
            assert it.label == ['Rename report to SCSR']
            assert it.script == ['mv aqua-report.html artifacts/SCSR-prj1-component1-aqua-report.html']
        }
        1 * stage.script.archiveArtifacts(_) >> {
            assert it.artifacts == ['artifacts/SCSR*']
        }
        1 * stage.script.stash(_) >> {
            assert it.name == ['scsr-report-component1-56']
            assert it.includes == ['artifacts/SCSR*']
            assert it.allowEmpty == [true]
        }
        // No mail sent
        0 * stage.script.emailext(_)
        // No warnings
        0 * stage.logger.warn(_)
    }

    def "run the Stage with default credential and without config params in cluster ConfigMap, but with email - Error CLI Operational"() {
        given:
        def stage = createStage()
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Get configs
        1 * stage.openShift.getConfigMapData(ScanWithAquaStage.AQUA_GENERAL_CONFIG_MAP_PROJECT,
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true,
            alertEmails: "mail1@mail.com",
        ]
        1 * stage.openShift.getConfigMapData("prj1-cd", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true
        ]
        1 * stage.logger.info("Please provide the URL of the Aqua platform!")
        1 * stage.logger.info("Please provide the name of the registry that contains the image of interest!")
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli(null, null, "image1:2323232323",
            "cd-user", "aqua-report.html", "aqua-report.json") >> AquaService.AQUA_OPERATIONAL_ERROR
        // Mail sent
        1 * stage.script.emailext(
            [
                'body':'<p>Build component1 on project prj1 had some problems with Aqua!</p> ' +
                    '<p>URL : <a href="http://buidl">http://buidl</a></p> ' +
                    '<ul><li>Provide the Aqua url of platform</li>' +
                    '<li>Provide the name of the registry to use in Aqua</li>' +
                    '<li>Error executing Aqua CLI</li></ul>',
                'mimeType':'text/html',
                'replyTo':'$script.DEFAULT_REPLYTO',
                'subject':'Build component1 on project prj1 had some problems with Aqua!',
                'to':'mail1@mail.com'
            ]
        )
        // No warnings
        0 * stage.logger.warn(_)
    }

    def "run the Stage with default credential and without config params in cluster ConfigMap, included email - Error CLI Operational"() {
        given:
        def stage = createStage()
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Get configs
        1 * stage.openShift.getConfigMapData(ScanWithAquaStage.AQUA_GENERAL_CONFIG_MAP_PROJECT,
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true
        ]
        1 * stage.openShift.getConfigMapData("prj1-cd", ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> [
            enabled: true
        ]
        1 * stage.logger.info("Please provide the alert emails of the Aqua platform!")
        1 * stage.logger.info("Please provide the URL of the Aqua platform!")
        1 * stage.logger.info("Please provide the name of the registry that contains the image of interest!")
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli(null, null, "image1:2323232323",
            "cd-user", "aqua-report.html", "aqua-report.json") >> AquaService.AQUA_OPERATIONAL_ERROR
        // Mail without to
        1 * stage.script.emailext(
            [
                'body':'<p>Build component1 on project prj1 had some problems with Aqua!</p> ' +
                    '<p>URL : <a href="http://buidl">http://buidl</a></p> ' +
                    '<ul><li>Provide the Aqua url of platform</li>' +
                    '<li>Provide the name of the registry to use in Aqua</li>' +
                    '<li>Error executing Aqua CLI</li></ul>',
                'mimeType':'text/html',
                'replyTo':'$script.DEFAULT_REPLYTO',
                'subject':'Build component1 on project prj1 had some problems with Aqua!',
                'to':null]
        ) >> {
            println "No destination to mail!!"
        }
        // No warnings
        0 * stage.logger.warn(_)
    }

    def "run the Stage without config maps"() {
        given:
        def stage = createStage()
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Get configs
        1 * stage.openShift.getConfigMapData(ScanWithAquaStage.AQUA_GENERAL_CONFIG_MAP_PROJECT,
            ScanWithAquaStage.AQUA_CONFIG_MAP_NAME) >> {
            throw new Exception("Non existing ConfigMap")
        }
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Mail sent
        1 * stage.script.emailext(
            [
                'body':'<p>Build component1 on project prj1 had some problems with Aqua!</p> ' +
                    '<p>URL : <a href="http://buidl">http://buidl</a></p> <ul><li>Error retrieving the Aqua config</li>' +
                    '<li>Provide the Aqua url of platform</li>' +
                    '<li>Provide the name of the registry to use in Aqua</li>' +
                    '<li>Skipping Aqua scan because is not enabled at cluster level in \'aqua\' ConfigMap in ods project</li></ul>',
                'mimeType':'text/html',
                'replyTo':'$script.DEFAULT_REPLYTO',
                'subject':'Build component1 on project prj1 had some problems with Aqua!',
                'to':null
            ]
        ) >> {
            println "No destination to mail!!"
        }
        // Warnings
        1 * stage.logger.warn('Error retrieving the Aqua config due to: java.lang.Exception: Non existing ConfigMap')
    }
}
