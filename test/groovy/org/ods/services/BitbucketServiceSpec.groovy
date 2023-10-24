package org.ods.services

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.ods.component.ScanWithAquaStage
import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase

import static com.github.tomakehurst.wiremock.client.WireMock.put
import static com.github.tomakehurst.wiremock.client.WireMock.ok

class BitbucketServiceSpec extends PipelineSpockTestBase {

    def "find pull request"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = Spy(BitbucketService, constructorArgs: [
            steps,
            'https://bitbucket.example.com',
            'FOO',
            'foo-cd-cd-user-with-password',
            new Logger(steps, false)
        ])

        def res = readResource(jsonFixture);
        service.getPullRequests(*_) >> res

        when:
        def result = service.findPullRequest('foo-bar', branch)

        then:
        result == expected

        where:
        jsonFixture             | branch        || expected
        'pull-requests.json'    | 'feature/foo' || [key: 1, base: 'master']
        'no-pull-requests.json' | 'feature/foo' || [:]
    }

    def "find default reviewers"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = Spy(BitbucketService, constructorArgs: [
            steps,
            'https://bitbucket.example.com',
            'FOO',
            'foo-cd-cd-user-with-password',
            new Logger(steps, false)
        ])

        def res = readResource(jsonFixture);
        service.getDefaultReviewerConditions(*_) >> res

        when:
        def result = service.getDefaultReviewers('foo-bar')

        then:
        result == expected

        where:
        jsonFixture                      | expected
        'reviewer-conditions.json'       | ['john.doe@example.com', 'jane.doe@example.com']
        'reviewer-conditions-empty.json' | []
    }

    def "check user token secret yml"() {
        expect:
        BitbucketService.userTokenSecretYml("my-secret", username, password) == expected
        where:
        username | password | expected
        "test@example.com" | "\$1 2 3\u00a3" | readResource('user-token-secret-1.yml')

    }


    def "create code insight report"() {
        given:

        // FIXME: just an example for later!!
        // FixtureHelper fh = new FixtureHelper()
        // def expected = fh.getResource("project-jira-data.json")

        String project = "FOO"
        String gitCommit = "123456"
        String repo = "repo-name"
        def data = [
            key: ScanWithAquaStage.BITBUCKET_AQUA_REPORT_KEY,
            title: "Title",
            link: "http://link-nexus",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Aqua",
                    link: "http://link"
                ],
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: "http://link-nexus"
                ]
            ],
            details: "Details",
            result: "PASS"
        ]

        def url = "/rest/insights/1.0/projects/${project}/repos/${repo}/commits/${gitCommit}/reports/${data.key}"
        def server = new WireMockServer(WireMockConfiguration.options().dynamicPort())
        server.stubFor(put(url)
            .willReturn(ok("OK")))
        server.start()
        WireMock.configureFor(server.port())

        def steps = Spy(util.PipelineSteps)
        def logger = Spy(Logger, constructorArgs: [steps, false])
        def service = Spy(BitbucketService, constructorArgs: [
            steps,
            server.baseUrl(),
            'FOO',
            'foo-cd-cd-user-with-password',
            logger
        ])

        when:
        service.createCodeInsightReport(data, repo, gitCommit)

        then:
        0 * logger.warn(_)
    }

    def "create code insight report without Aqua Link"() {
        given:
        String project = "FOO"
        String gitCommit = "123456"
        String repo = "repo-name"

        def data = [
            key: ScanWithAquaStage.BITBUCKET_AQUA_REPORT_KEY,
            title: "Title",
            link: "http://link-nexus",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: "http://link-nexus"
                ]
            ],
            details: "Details",
            result: "PASS"
        ]

        def url = "/rest/insights/1.0/projects/${project}/repos/${repo}/commits/${gitCommit}/reports/${data.key}"
        def server = new WireMockServer(WireMockConfiguration.options().dynamicPort())
        server.stubFor(put(url)
            .willReturn(ok("OK")))
        server.start()
        WireMock.configureFor(server.port())

        def steps = Spy(util.PipelineSteps)
        def logger = Spy(Logger, constructorArgs: [steps, false])
        def service = Spy(BitbucketService, constructorArgs: [
            steps,
            server.baseUrl(),
            project,
            'foo-cd-cd-user-with-password',
            logger
        ])

        when:
        service.createCodeInsightReport(data, repo, gitCommit)

        then:
        0 * logger.warn(_)
    }

    def "create code insight report without Nexus link"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = Spy(BitbucketService, constructorArgs: [
            steps,
            'https://bitbucket.example.com',
            'FOO',
            'foo-cd-cd-user-with-password',
            new Logger(steps, false)
        ])
        def data = [
            key: ScanWithAquaStage.BITBUCKET_AQUA_REPORT_KEY,
            title: "Title",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Aqua",
                    link: "http://link"
                ]
            ],
            details: "Details",
            result: "PASS"
        ]

        when:
        service.createCodeInsightReport(data, "repo-name", "123456")

        then:
        2 * steps.getEnv() >> ['USERNAME':'user', 'TOKEN': 'tokenvalue']
        1 * steps.sh(_)>> {
            assert it.label == ['Create Bitbucket Code Insight report via API']
            assert it.script.toString().contains('curl')
            assert it.script.toString().contains('--fail')
            assert it.script.toString().contains('-sS')
            assert it.script.toString().contains('--request PUT')
            // sh will not run and hence not replace the value with the token from an env var
            assert it.script.toString().contains('--header "Authorization: Bearer $TOKEN"')
            assert it.script.toString().contains('--header "Content-Type: application/json"')
            assert it.script.toString().contains('-data \'{"title":"Title","reporter":"OpenDevStack","createdDate":')
            // Avoid timestamp of creation
            assert it.script.toString().contains('"details":"Details","result":"PASS","data": ' +
                '[{"title":"Report","value":{"linktext":"Result in Aqua","href":"http://link"},"type":"LINK"}]}\'')
            assert it.script.toString().contains('https://bitbucket.example.com/rest/insights/1.0/' +
                'projects/FOO/repos/repo-name/commits/123456/reports/org.opendevstack.aquasec')
        }
    }

    def "create code insight report without Nexus and Aqua link"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = Spy(BitbucketService, constructorArgs: [
            steps,
            'https://bitbucket.example.com',
            'FOO',
            'foo-cd-cd-user-with-password',
            new Logger(steps, false)
        ])
        def data = [
            key: ScanWithAquaStage.BITBUCKET_AQUA_REPORT_KEY,
            title: "Title",
            details: "Details",
            result: "PASS"
        ]

        when:
        service.createCodeInsightReport(data, "repo-name", "123456")

        then:
        2 * steps.getEnv() >> ['USERNAME':'user', 'TOKEN': 'tokenvalue']
        1 * steps.sh(_)>> {
            assert it.label == ['Create Bitbucket Code Insight report via API']
            assert it.script.toString().contains('curl')
            assert it.script.toString().contains('--fail')
            assert it.script.toString().contains('-sS')
            assert it.script.toString().contains('--request PUT')
            // sh will not run and hence not replace the value with the token from an env var
            assert it.script.toString().contains('--header "Authorization: Bearer $TOKEN"')
            assert it.script.toString().contains('--header "Content-Type: application/json"')
            assert it.script.toString().contains('-data \'{"title":"Title","reporter":"OpenDevStack","createdDate":')
            // Avoid timestamp of creation
            assert it.script.toString().contains('"details":"Details","result":"PASS","data": []}\'')
            assert it.script.toString().contains('https://bitbucket.example.com/rest/insights/1.0/' +
                'projects/FOO/repos/repo-name/commits/123456/reports/org.opendevstack.aquasec')
        }
    }

    def "create code insight report with links and messages"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = Spy(BitbucketService, constructorArgs: [
            steps,
            'https://bitbucket.example.com',
            'FOO',
            'foo-cd-cd-user-with-password',
            new Logger(steps, false)
        ])
        def data = [
            key: ScanWithAquaStage.BITBUCKET_AQUA_REPORT_KEY,
            title: "Title",
            link: "http://link-nexus",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Aqua",
                    link: "http://link"
                ],
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: "http://link-nexus"
                ]
            ],
            messages: [
                [
                    title: "Messages",
                    value: "Messages"
                ]
            ],
            details: "Details",
            result: "PASS"
        ]

        when:
        service.createCodeInsightReport(data, "repo-name", "123456")

        then:
        2 * steps.getEnv() >> ['USERNAME':'user', 'TOKEN': 'tokenvalue']
        1 * steps.sh(_)>> {
            assert it.label == ['Create Bitbucket Code Insight report via API']
            assert it.script.toString().contains('curl')
            assert it.script.toString().contains('--fail')
            assert it.script.toString().contains('-sS')
            assert it.script.toString().contains('--request PUT')
            // sh will not run and hence not replace the value with the token from an env var
            assert it.script.toString().contains('--header "Authorization: Bearer $TOKEN"')
            assert it.script.toString().contains('--header "Content-Type: application/json"')
            assert it.script.toString().contains('-data \'{"title":"Title","reporter":"OpenDevStack","createdDate":')
            // Avoid timestamp of creation
            assert it.script.toString().contains('"details":"Details","result":"PASS","link":"http://link-nexus","data": ' +
                '[{"title":"Report","value":{"linktext":"Result in Aqua","href":"http://link"},"type":"LINK"},' +
                '{"title":"Report","value":{"linktext":"Result in Nexus","href":"http://link-nexus"},"type":"LINK"},' +
                '{"title":"Messages","value":"Messages","type":"TEXT"}]}\'')
            assert it.script.toString().contains('https://bitbucket.example.com/rest/insights/1.0/' +
                'projects/FOO/repos/repo-name/commits/123456/reports/org.opendevstack.aquasec')
        }
    }

    def "create code insight report with messages but without links"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = Spy(BitbucketService, constructorArgs: [
            steps,
            'https://bitbucket.example.com',
            'FOO',
            'foo-cd-cd-user-with-password',
            new Logger(steps, false)
        ])
        def data = [
            key: ScanWithAquaStage.BITBUCKET_AQUA_REPORT_KEY,
            title: "Title",
            messages: [
                [
                    title: "Messages",
                    value: "Messages"
                ]
            ],
            details: "Details",
            result: "PASS"
        ]

        when:
        service.createCodeInsightReport(data, "repo-name", "123456")

        then:
        2 * steps.getEnv() >> ['USERNAME':'user', 'TOKEN': 'tokenvalue']
        1 * steps.sh(_)>> {
            assert it.label == ['Create Bitbucket Code Insight report via API']
            assert it.script.toString().contains('curl')
            assert it.script.toString().contains('--fail')
            assert it.script.toString().contains('-sS')
            assert it.script.toString().contains('--request PUT')
            // sh will not run and hence not replace the value with the token from an env var
            assert it.script.toString().contains('--header "Authorization: Bearer $TOKEN"')
            assert it.script.toString().contains('--header "Content-Type: application/json"')
            assert it.script.toString().contains('-data \'{"title":"Title","reporter":"OpenDevStack","createdDate":')
            // Avoid timestamp of creation
            assert it.script.toString().contains('"details":"Details","result":"PASS","data": ' +
                '[{"title":"Messages","value":"Messages","type":"TEXT"}]}\'')
            assert it.script.toString().contains('https://bitbucket.example.com/rest/insights/1.0/' +
                'projects/FOO/repos/repo-name/commits/123456/reports/org.opendevstack.aquasec')
        }
    }

    def "create code insight report with error in call"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def logger = Spy(new Logger(steps, false))
        def service = Spy(BitbucketService, constructorArgs: [
            steps,
            'https://bitbucket.example.com',
            'FOO',
            'foo-cd-cd-user-with-password',
            logger
        ])
        def data = [
            key: ScanWithAquaStage.BITBUCKET_AQUA_REPORT_KEY,
            title: "Title",
            link: "http://link-nexus",
            otherLinks: [
                [
                    title: "Report",
                    text: "Result in Aqua",
                    link: "http://link"
                ],
                [
                    title: "Report",
                    text: "Result in Nexus",
                    link: "http://link-nexus"
                ]
            ],
            messages: [
                [
                    title: "Messages",
                    value: "Messages"
                ]
            ],
            details: "Details",
            result: "PASS"
        ]

        when:
        service.createCodeInsightReport(data, "repo-name", "123456")

        then:
        2 * steps.getEnv() >> ['USERNAME':'user', 'TOKEN': 'tokenvalue']
        1 * steps.sh(_) >> {
            throw new Exception ("Error with curl")
        }
        1 * logger.warn("Could not create Bitbucket Code Insight report due to: java.lang.Exception: Error with curl")
    }

    protected String readResource(String name) {
        def classLoader = getClass().getClassLoader();
        def file = new File(classLoader.getResource(name).getFile());
        file.text
    }
}
