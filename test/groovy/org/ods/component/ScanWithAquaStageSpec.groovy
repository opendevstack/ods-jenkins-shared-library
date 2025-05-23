package org.ods.component

import org.ods.services.AquaService
import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.util.Logger

import util.PipelineSteps
import vars.test_helper.PipelineSpockTestBase
import spock.lang.Unroll

class ScanWithAquaStageSpec extends PipelineSpockTestBase {

    ScanWithAquaStage createStage(Map configurationAquaCluster = [:], Map configurationAquaProject = [:],
            extraConfig = [:]) {
        def script = Spy(PipelineSteps)
        def logger = Spy(new Logger(script, false))
        Map contextConfig =  [componentId: "component1",
                       projectId: "prj1",
                       buildUrl: "http://buidl",
                       buildNumber: "56",
                       repoName: "component1",
                       gitCommit: "12112121212121",
                       cdProject: "prj1-cd",
                       credentialsId: "cd-user"]
        contextConfig << extraConfig

        IContext context = new Context(script, contextConfig, logger)
        def config = [:]
        def aqua = Spy(new AquaService(script, logger))
        def bitbucket = Spy(new BitbucketService (script,
            'https://bitbucket.example.com',
            'FOO',
            'foo-cd-cd-user-with-password',
            logger))
        def openShift = Spy(new OpenShiftService (script, logger))
        def nexus = Spy(new NexusService ("http://nexus", script, "foo-cd-cd-user-with-password"))
        def stage = new ScanWithAquaStage(
            script,
            context,
            config,
            aqua,
            bitbucket,
            openShift,
            nexus,
            logger,
            configurationAquaCluster,
            configurationAquaProject
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

    def "archive report in Jenkins if stage not launched by orchestration pipeline"() {
        given:
        def stage = createStage()

        when:
        stage.archiveReportInJenkins("report.html")

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

    def "archive report in Nexus"() {
        given:
        def stage = createStage()

        when:
        stage.archiveReportInNexus("aqua-report.html", "leva-documentation")

        then:
        1 * stage.script.readFile([file: "aqua-report.html"]) >> "Cool report"
        1 * stage.nexus.storeArtifact("leva-documentation", _, "aqua-report.html", _, "text/html") >>
            new URI("http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report.html")
        1 * stage.logger.info("Report stored in: http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report.html")
    }

    def "create Bitbucket Insight report - PASS"() {
        given:
        def stage = createStage()
        def data = [
            key: ScanWithAquaStage.BITBUCKET_AQUA_REPORT_KEY + "_12345",
            title: "Aqua Security (Image: 12345)",
            link: "http://nexus",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Aqua",
                    link: "http://aqua/#/images/internal/12345/vulns"
                ],
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: "http://nexus"
                ]
            ],
            details: "Please visit the following links to review the Aqua Security scan report:",
            result: "PASS"
        ]

        when:
        stage.createBitbucketCodeInsightReport("http://aqua", "http://nexus",
            "internal", "12345", 0, null, [])

        then:
        1 * stage.bitbucket.createCodeInsightReport(data, stage.context.repoName, stage.context.gitCommit)
    }

    def "create Bitbucket Insight report - FAIL"() {
        given:
        def stage = createStage()
        def data = [
            key: ScanWithAquaStage.BITBUCKET_AQUA_REPORT_KEY + "_12345",
            title: "Aqua Security (Image: 12345)",
            link: "http://nexus",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Aqua",
                    link: "http://aqua/#/images/internal/12345/vulns"
                ],
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: "http://nexus"
                ]
            ],
            details: "Please visit the following links to review the Aqua Security scan report:",
            result: "FAIL"
        ]

        when:
        stage.createBitbucketCodeInsightReport("http://aqua", "http://nexus","internal",
            "12345", 1, null, [])

        then:
        1 * stage.bitbucket.createCodeInsightReport(data, stage.context.repoName, stage.context.gitCommit)

    }

    def "create Bitbucket Insight report - Messages"() {
        given:
        def stage = createStage()
        def data = [
            key: ScanWithAquaStage.BITBUCKET_AQUA_REPORT_KEY,
            title: "Aqua Security",
            messages: [
                [
                    title: "Messages",
                    value: "Message"
                ]
            ],
            details: "There were some problems with Aqua:",
            result: "FAIL"
        ]

        when:
        stage.createBitbucketCodeInsightReport('Message')

        then:
        1 * stage.bitbucket.createCodeInsightReport(data, stage.context.repoName, stage.context.gitCommit)
    }

    def "scan with CLI - SUCCESS"() {
        given:
        def stage = createStage()

        when:
        def result = stage.scanViaCli("http://aqua", "internal", "12345",
            "cd-user", "report.html", "report.json")

        then:
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "12345",
            "cd-user", "report.html", "report.json", ScanWithAquaStage.AQUA_DEFAULT_TIMEOUT) >> AquaService.AQUA_SUCCESS
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
            "cd-user", "report.html", "report.json", ScanWithAquaStage.AQUA_DEFAULT_TIMEOUT) >> AquaService.AQUA_POLICIES_ERROR
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
            "cd-user", "report.html", "report.json", ScanWithAquaStage.AQUA_DEFAULT_TIMEOUT) >> AquaService.AQUA_OPERATIONAL_ERROR
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
            "cd-user", "report.html", "report.json", ScanWithAquaStage.AQUA_DEFAULT_TIMEOUT) >> 127
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
        def stage = createStage([
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal",
            nexusRepository: "leva-documentation"
        ], [
            enabled: true
        ])
        def data = [
            key: ScanWithAquaStage.BITBUCKET_AQUA_REPORT_KEY + "_image1",
            title: "Aqua Security (Image: image1)",
            link: "http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Aqua",
                    link: "http://aqua/#/images/internal/image1:2323232323/vulns"
                ],
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: "http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html"
                ]
            ],
            details: "Please visit the following links to review the Aqua Security scan report:",
            result: "PASS"
        ]

        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:

        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "image1:2323232323",
            "cd-user", "aqua-report-image1.html", "aqua-report-image1.json", ScanWithAquaStage.AQUA_DEFAULT_TIMEOUT) >> AquaService.AQUA_SUCCESS
        // Read results
        1 * stage.script.readFile([file: "aqua-report-image1.json"]) >> "[vulnerability_summary: [critical: 0, malware: 0]]"
        1 * stage.script.readJSON([text: "[vulnerability_summary: [critical: 0, malware: 0]]"]) >> [
            vulnerability_summary: [critical: 0, malware: 0]
        ]
        // Archive in Nexus
        1 * stage.script.readFile([file: "aqua-report-image1.html"]) >> "Cool report"
        1 * stage.nexus.storeArtifact("leva-documentation", _, "aqua-report-image1.html", _, "text/html") >>
            new URI("http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html")
        // Create report in Bitbucket
        1 * stage.bitbucket.createCodeInsightReport(data, stage.context.repoName, stage.context.gitCommit)
        // create the report (via sh)
        1 * stage.script.sh(_) >> {
            assert it.label.contains('Create Bitbucket Code Insight report via API')
        }
        // Archive artifact
        1 * stage.script.sh(_) >> {
            assert it.label == ['Create artifacts dir']
            assert it.script == ['mkdir -p artifacts']
        }
        1 * stage.script.sh(_) >> {
            assert it.label == ['Rename report to SCSR']
            assert it.script == ['mv aqua-report-image1.html artifacts/SCSR-prj1-component1-aqua-report-image1.html']
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
        def stage = createStage([
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal",
            secretName: "custom-secret",
            nexusRepository: "leva-documentation"
        ], [
            enabled: true
        ])
        def data = [
            key: ScanWithAquaStage.BITBUCKET_AQUA_REPORT_KEY + "_image1",
            title: "Aqua Security (Image: image1)",
            link: "http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Aqua",
                    link: "http://aqua/#/images/internal/image1:2323232323/vulns"
                ],
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: "http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html"
                ]
            ],
            details: "Please visit the following links to review the Aqua Security scan report:",
            result: "PASS"
        ]
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // No default cd-user
        0 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "image1:2323232323",
            "prj1-cd-custom-secret", "aqua-report-image1.html", "aqua-report-image1.json", ScanWithAquaStage.AQUA_DEFAULT_TIMEOUT) >> AquaService.AQUA_SUCCESS
        // Read results
        1 * stage.script.readFile([file: "aqua-report-image1.json"]) >> "[vulnerability_summary: [critical: 0, malware: 0]]"
        1 * stage.script.readJSON([text: "[vulnerability_summary: [critical: 0, malware: 0]]"]) >> [
            vulnerability_summary: [critical: 0, malware: 0]
        ]
        // Archive in Nexus
        1 * stage.script.readFile([file: "aqua-report-image1.html"]) >> "Cool report"
        1 * stage.nexus.storeArtifact("leva-documentation", _, "aqua-report-image1.html", _, "text/html") >>
            new URI("http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html")
        // Create report in Bitbucket
        1 * stage.bitbucket.createCodeInsightReport(data, stage.context.repoName, stage.context.gitCommit)
        // create the report (via sh)
        1 * stage.script.sh(_) >> {
            assert it.label.contains('Create Bitbucket Code Insight report via API')
        }
        // Archive artifact
        1 * stage.script.sh(_) >> {
            assert it.label == ['Create artifacts dir']
            assert it.script == ['mkdir -p artifacts']
        }
        1 * stage.script.sh(_) >> {
            assert it.label == ['Rename report to SCSR']
            assert it.script == ['mv aqua-report-image1.html artifacts/SCSR-prj1-component1-aqua-report-image1.html']
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
        def stage = createStage([
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal",
            nexusRepository: "leva-documentation"
        ], [
            enabled: true
        ])

        when:
        stage.run()

        then:
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
        def stage = createStage([
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal",
            nexusRepository: "leva-documentation"
        ], [
            enabled: true
        ])
        def data = [
            key: ScanWithAquaStage.BITBUCKET_AQUA_REPORT_KEY + "_image1",
            title: "Aqua Security (Image: image1)",
            link: "http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Aqua",
                    link: "http://aqua/#/images/internal/image1:2323232323/vulns"
                ],
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: "http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html"
                ]
            ],
            details: "Please visit the following links to review the Aqua Security scan report:",
            result: "FAIL"
        ]
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "image1:2323232323",
            "cd-user", "aqua-report-image1.html", "aqua-report-image1.json", ScanWithAquaStage.AQUA_DEFAULT_TIMEOUT) >> AquaService.AQUA_POLICIES_ERROR
        // Read results
        1 * stage.script.readFile([file: "aqua-report-image1.json"]) >> "[vulnerability_summary: [critical: 0, malware: 0]]"
        1 * stage.script.readJSON([text: "[vulnerability_summary: [critical: 0, malware: 0]]"]) >> [
            vulnerability_summary: [critical: 0, malware: 0]
        ]
        // Archive in Nexus
        1 * stage.script.readFile([file: "aqua-report-image1.html"]) >> "Cool report"
        1 * stage.nexus.storeArtifact("leva-documentation", _, "aqua-report-image1.html", _, "text/html") >>
            new URI("http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html")
        // Create report in Bitbucket
        1 * stage.bitbucket.createCodeInsightReport(data, stage.context.repoName, stage.context.gitCommit)
        // create the report (via sh)
        1 * stage.script.sh(_) >> {
            assert it.label.contains('Create Bitbucket Code Insight report via API')
        }
        // Archive artifact
        1 * stage.script.sh(_) >> {
            assert it.label == ['Create artifacts dir']
            assert it.script == ['mkdir -p artifacts']
        }
        1 * stage.script.sh(_) >> {
            assert it.label == ['Rename report to SCSR']
            assert it.script == ['mv aqua-report-image1.html artifacts/SCSR-prj1-component1-aqua-report-image1.html']
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

    def "run the Stage with default credential - Error Vulnerabilities critical"() {
        given:
        def stage = createStage([
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal",
            nexusRepository: "leva-documentation"
        ], [
            enabled: true
        ])
        def data = [
            key: ScanWithAquaStage.BITBUCKET_AQUA_REPORT_KEY + "_image1",
            title: "Aqua Security (Image: image1)",
            link: "http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Aqua",
                    link: "http://aqua/#/images/internal/image1:2323232323/vulns"
                ],
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: "http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html"
                ]
            ],
            details: "Please visit the following links to review the Aqua Security scan report:",
            result: "FAIL"
        ]
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "image1:2323232323",
            "cd-user", "aqua-report-image1.html", "aqua-report-image1.json", ScanWithAquaStage.AQUA_DEFAULT_TIMEOUT) >> AquaService.AQUA_SUCCESS
        // Read results
        1 * stage.script.readFile([file: "aqua-report-image1.json"]) >> "[vulnerability_summary: [critical: 1, malware: 0]]"
        1 * stage.script.readJSON([text: "[vulnerability_summary: [critical: 1, malware: 0]]"]) >> [
            vulnerability_summary: [critical: 1, malware: 0]
        ]
        // Archive in Nexus
        1 * stage.script.readFile([file: "aqua-report-image1.html"]) >> "Cool report"
        1 * stage.nexus.storeArtifact("leva-documentation", _, "aqua-report-image1.html", _, "text/html") >>
            new URI("http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html")
        // Create report in Bitbucket
        1 * stage.bitbucket.createCodeInsightReport(data, stage.context.repoName, stage.context.gitCommit)
        // create the report (via sh)
        1 * stage.script.sh(_) >> {
            assert it.label.contains('Create Bitbucket Code Insight report via API')
        }
        // Archive artifact
        1 * stage.script.sh(_) >> {
            assert it.label == ['Create artifacts dir']
            assert it.script == ['mkdir -p artifacts']
        }
        1 * stage.script.sh(_) >> {
            assert it.label == ['Rename report to SCSR']
            assert it.script == ['mv aqua-report-image1.html artifacts/SCSR-prj1-component1-aqua-report-image1.html']
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

    def "run the Stage with default credential - Error Malware"() {
        given:
        def stage = createStage([
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal",
            nexusRepository: "leva-documentation"
        ], [
            enabled: true
        ])
        def data = [
            key: ScanWithAquaStage.BITBUCKET_AQUA_REPORT_KEY + "_image1",
            title: "Aqua Security (Image: image1)",
            link: "http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Aqua",
                    link: "http://aqua/#/images/internal/image1:2323232323/vulns"
                ],
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: "http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html"
                ]
            ],
            details: "Please visit the following links to review the Aqua Security scan report:",
            result: "FAIL"
        ]
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "image1:2323232323",
            "cd-user", "aqua-report-image1.html", "aqua-report-image1.json", ScanWithAquaStage.AQUA_DEFAULT_TIMEOUT) >> AquaService.AQUA_SUCCESS
        // Read results
        1 * stage.script.readFile([file: "aqua-report-image1.json"]) >> "[vulnerability_summary: [critical: 0, malware: 1]]"
        1 * stage.script.readJSON([text: "[vulnerability_summary: [critical: 0, malware: 1]]"]) >> [
            vulnerability_summary: [critical: 0, malware: 1]
        ]
        // Archive in Nexus
        1 * stage.script.readFile([file: "aqua-report-image1.html"]) >> "Cool report"
        1 * stage.nexus.storeArtifact("leva-documentation", _, "aqua-report-image1.html", _, "text/html") >>
            new URI("http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html")
        // Create report in Bitbucket
        1 * stage.bitbucket.createCodeInsightReport(data, stage.context.repoName, stage.context.gitCommit)
        // create the report (via sh)
        1 * stage.script.sh(_) >> {
            assert it.label.contains('Create Bitbucket Code Insight report via API')
        }
        // Archive artifact
        1 * stage.script.sh(_) >> {
            assert it.label == ['Create artifacts dir']
            assert it.script == ['mkdir -p artifacts']
        }
        1 * stage.script.sh(_) >> {
            assert it.label == ['Rename report to SCSR']
            assert it.script == ['mv aqua-report-image1.html artifacts/SCSR-prj1-component1-aqua-report-image1.html']
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

    def "run the Stage with default credential - Error CLI Operational"() {
        given:
        def stage = createStage([
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal",
            nexusRepository: "leva-documentation"
        ], [
            enabled: true
        ])
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "image1:2323232323",
            "cd-user", "aqua-report-image1.html", "aqua-report-image1.json", ScanWithAquaStage.AQUA_DEFAULT_TIMEOUT) >> AquaService.AQUA_OPERATIONAL_ERROR
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
        def stage = createStage([
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal",
            nexusRepository: "leva-documentation"
        ], [
            enabled: true
        ])
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "image1:2323232323",
            "cd-user", "aqua-report-image1.html", "aqua-report-image1.json", ScanWithAquaStage.AQUA_DEFAULT_TIMEOUT) >> 127
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

    def "run the Stage with default credential - Error archiving report in Bitbucket"() {
        given:
        def stage = createStage([
            enabled: true,
            alertEmails: "mail1@mail.com",
            url: "http://aqua",
            registry: "internal",
            nexusRepository: "leva-documentation"
        ], [
            enabled: true
        ])
        def data = [
            key: ScanWithAquaStage.BITBUCKET_AQUA_REPORT_KEY + "_image1",
            title: "Aqua Security (Image: image1)",
            link: "http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Aqua",
                    link: "http://aqua/#/images/internal/image1:2323232323/vulns"
                ],
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: "http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html"
                ]
            ],
            details: "Please visit the following links to review the Aqua Security scan report:",
            result: "PASS"
        ]
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli("http://aqua", "internal", "image1:2323232323",
            "cd-user", "aqua-report-image1.html", "aqua-report-image1.json", ScanWithAquaStage.AQUA_DEFAULT_TIMEOUT) >> AquaService.AQUA_SUCCESS
        // Read results
        1 * stage.script.readFile([file: "aqua-report-image1.json"]) >> "[vulnerability_summary: [critical: 0, malware: 0]]"
        1 * stage.script.readJSON([text: "[vulnerability_summary: [critical: 0, malware: 0]]"]) >> [
            vulnerability_summary: [critical: 0, malware: 0]
        ]
        // Archive in Nexus
        1 * stage.script.readFile([file: "aqua-report-image1.html"]) >> "Cool report"
        1 * stage.nexus.storeArtifact("leva-documentation", _, "aqua-report-image1.html", _, "text/html") >>
            new URI("http://nexus/repository/leva-documentation/prj1/12345-56/aqua/aqua-report-image1.html")
        // Error creating report in Bitbucket
        1 * stage.bitbucket.createCodeInsightReport(data, stage.context.repoName, stage.context.gitCommit) >> {
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

    def "run the Stage with default credential and without config params in cluster ConfigMap, but with email - Error CLI Operational"() {
        given:
        def stage = createStage([
            enabled: true,
            alertEmails: "mail1@mail.com"
        ], [
            enabled: true
        ])
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        1 * stage.logger.info("Please provide the URL of the Aqua platform!")
        1 * stage.logger.info("Please provide the name of the registry that contains the image of interest!")
        1 * stage.logger.info("Please provide the name of the repository in Nexus to store the reports!")
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli(null, null, "image1:2323232323",
            "cd-user", "aqua-report-image1.html", "aqua-report-image1.json", ScanWithAquaStage.AQUA_DEFAULT_TIMEOUT) >> AquaService.AQUA_OPERATIONAL_ERROR
        // Mail sent
        1 * stage.script.emailext(
            [
                'body':'<p>Build component1 on project prj1 had some problems with Aqua!</p> ' +
                    '<p>URL : <a href="http://buidl">http://buidl</a></p> ' +
                    '<ul><li>Provide the Aqua url of platform</li>' +
                    '<li>Provide the name of the registry to use in Aqua</li>' +
                    '<li>Provide the name of the repository in Nexus to use with Aqua</li>' +
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
        def stage = createStage([
            enabled: true
        ], [
            enabled: true
        ])
        stage.context.addBuildToArtifactURIs("component1", [image: "image1/image1:2323232323"])

        when:
        stage.run()

        then:
        1 * stage.logger.info("Please provide the alert emails of the Aqua platform!")
        1 * stage.logger.info("Please provide the URL of the Aqua platform!")
        1 * stage.logger.info("Please provide the name of the registry that contains the image of interest!")
        1 * stage.logger.info("Please provide the name of the repository in Nexus to store the reports!")
        // Default cd-user
        1 * stage.logger.info("No custom secretName was specified in the aqua ConfigMap, " +
            "continuing with default credentialsId 'cd-user'...")
        // Do the scan
        1 * stage.aqua.scanViaCli(null, null, "image1:2323232323",
            "cd-user", "aqua-report-image1.html", "aqua-report-image1.json", ScanWithAquaStage.AQUA_DEFAULT_TIMEOUT) >> AquaService.AQUA_OPERATIONAL_ERROR
        // Mail without to
        1 * stage.script.emailext(
            [
                'body':'<p>Build component1 on project prj1 had some problems with Aqua!</p> ' +
                    '<p>URL : <a href="http://buidl">http://buidl</a></p> <ul>' +
                    '<li>Provide the alert emails of the Aqua platform</li>' +
                    '<li>Provide the Aqua url of platform</li>' +
                    '<li>Provide the name of the registry to use in Aqua</li>' +
                    '<li>Provide the name of the repository in Nexus to use with Aqua</li>' +
                    '<li>Error executing Aqua CLI</li></ul>',
                'mimeType':'text/html',
                'replyTo':'$script.DEFAULT_REPLYTO',
                'subject':'Build component1 on project prj1 had some problems with Aqua!',
                'to':null
            ]
        ) >> {
            println "No destination to mail!!"
        }
        // No warnings
        0 * stage.logger.warn(_)
    }

    def "Filter the vulnerabilities that are critical, remote and have a solution"() {
        given:
        def stage = createStage()
        def aquaJsonFile = new File(getClass().getResource("aqua-test-result.json").toURI())
        def pipelineSteps = new PipelineSteps()
        Set whiteListed = []

        when:
        def aquaJsonAsMap = pipelineSteps.readJSON(text: aquaJsonFile.text) as Map
        def result = stage.filterRemoteCriticalWithSolutionVulnerabilities(aquaJsonAsMap, whiteListed)

        then:
        assert result != null
        assert result.size() == 1
        assert whiteListed.size() == 1
    }

    def "Check compute scanned branch for valid release branch"() {
        given:
        def releaseBranch = "release/2.0"
        def stage = createStage([:], [:],
            [
                gitBranch : releaseBranch
            ]
        )
        def branchesReponse = new File(getClass().getResource("branches-response.json").toURI())
        def pipelineSteps = new PipelineSteps()

        and:
        Map branchesReponseAsMap = pipelineSteps.readJSON(text: branchesReponse.text) as Map
        1 * stage.bitbucket.findRepoBranches(_, _) >> branchesReponseAsMap

        when:
        def result = stage.computeScannedBranch()

        then:
        0 * stage.bitbucket.getDefaultBranch(_)
        assert result == releaseBranch
    }

    def "Check compute scanned branch for missing release branch"() {
        given:
        def releaseBranch = "release/missing"
        def defaultBranch = "master"
        def stage = createStage([:], [:],
            [
                gitBranch : releaseBranch
            ]
        )
        def branchesReponse = new File(getClass().getResource("branches-response.json").toURI())
        def pipelineSteps = new PipelineSteps()

        and:
        def branchesReponseAsMap = pipelineSteps.readJSON(text: branchesReponse.text) as Map
        1 * stage.bitbucket.findRepoBranches(_, _) >> branchesReponseAsMap
        1 * stage.bitbucket.getDefaultBranch(_) >> defaultBranch

        when:
        def result = stage.computeScannedBranch()

        then:
        assert result == defaultBranch
    }

    def "Check compute scanned branch for non release branch"() {
        given:
        def devBranch = "dev"
        def stage = createStage([:], [:],
            [
                gitBranch : devBranch
            ]
        )

        when:
        def result = stage.computeScannedBranch()

        then:
        0 * stage.bitbucket.findRepoBranches(_, _)
        0 * stage.bitbucket.getDefaultBranch(_)
        assert result == devBranch
    }

    @Unroll
    def "verify image name sanitize for reports"() {
        given:

        when:
            def sanitized = ScanWithAquaStage.createImageRefNameForReport(imageRef)
        then:
            expectedImageReportName == sanitized

        where:
        imageRef                  || expectedImageReportName
        'someImageWithoutTag'     || 'someImageWithoutTag'
        'someImageWithTag:10'     || 'someImageWithTag'
        'someImageWithTag@sha:10' || 'someImageWithTag'
        'reg:5/name/image:tag'    || 'image'

    }

}
