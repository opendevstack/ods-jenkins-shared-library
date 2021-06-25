package org.ods.services

import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase

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
        def steps = Spy(util.PipelineSteps)
        def service = Spy(BitbucketService, constructorArgs: [
            steps,
            'https://bitbucket.example.com',
            'FOO',
            'foo-cd-cd-user-with-password',
            new Logger(steps, false)
        ])

        when:
        service.createCodeInsightReport("http://link", "http://link-nexus", "repo-name", "123456", "Title", "Details", "PASS")

        then:
        2 * steps.getEnv() >> ['USERNAME':'user', 'TOKEN': 'tokenvalue']
        1 * steps.sh(_)>> {
            assert it.label == ['Create Bitbucket Code Insight report via API']
            assert it.script.toString().contains('curl')
            assert it.script.toString().contains('--fail')
            assert it.script.toString().contains('-sS')
            assert it.script.toString().contains('--request PUT')
            assert it.script.toString().contains('--header "Authorization: Bearer tokenvalue"')
            assert it.script.toString().contains('--header "Content-Type: application/json"')
            assert it.script.toString().contains('-data \'{"title":"Title","reporter":"OpenDevStack","createdDate":')
            // Avoid timestamp of creation
            assert it.script.toString().contains('"details":"Details","result":"PASS","link":"http://link-nexus","data": ' +
                '[{"title":"Link","value":{"linktext":"Result in Aqua","href":"http://link"},"type":"LINK"},' +
                '{"title":"Link","value":{"linktext":"Result in Nexus","href":"http://link-nexus"},"type":"LINK"},]}\'')
            assert it.script.toString().contains('https://bitbucket.example.com/rest/insights/1.0/' +
                'projects/FOO/repos/repo-name/commits/123456/reports/org.opendevstack.aquasec')
        }
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

        when:
        service.createCodeInsightReport("http://link", null, "repo-name", "123456", "Title", "Details", "PASS")

        then:
        2 * steps.getEnv() >> ['USERNAME':'user', 'TOKEN': 'tokenvalue']
        1 * steps.sh(_)>> {
            assert it.label == ['Create Bitbucket Code Insight report via API']
            assert it.script.toString().contains('curl')
            assert it.script.toString().contains('--fail')
            assert it.script.toString().contains('-sS')
            assert it.script.toString().contains('--request PUT')
            assert it.script.toString().contains('--header "Authorization: Bearer tokenvalue"')
            assert it.script.toString().contains('--header "Content-Type: application/json"')
            assert it.script.toString().contains('-data \'{"title":"Title","reporter":"OpenDevStack","createdDate":')
            // Avoid timestamp of creation
            assert it.script.toString().contains('"details":"Details","result":"PASS","data": ' +
                '[{"title":"Link","value":{"linktext":"Result in Aqua","href":"http://link"},"type":"LINK"},]}\'')
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

        when:
        service.createCodeInsightReport(null, null, "repo-name", "123456", "Title", "Details", "PASS")

        then:
        2 * steps.getEnv() >> ['USERNAME':'user', 'TOKEN': 'tokenvalue']
        1 * steps.sh(_)>> {
            assert it.label == ['Create Bitbucket Code Insight report via API']
            assert it.script.toString().contains('curl')
            assert it.script.toString().contains('--fail')
            assert it.script.toString().contains('-sS')
            assert it.script.toString().contains('--request PUT')
            assert it.script.toString().contains('--header "Authorization: Bearer tokenvalue"')
            assert it.script.toString().contains('--header "Content-Type: application/json"')
            assert it.script.toString().contains('-data \'{"title":"Title","reporter":"OpenDevStack","createdDate":')
            // Avoid timestamp of creation
            assert it.script.toString().contains('"details":"Details","result":"PASS","data": []}\'')
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

        when:
        service.createCodeInsightReport("http://link", "http://linq-nexus", "repo-name", "123456", "Title", "Details", "PASS")

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
