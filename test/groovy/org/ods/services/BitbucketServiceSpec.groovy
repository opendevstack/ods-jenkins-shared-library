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
        def result = service.createCodeInsightReport("http://link", "repo-name", "123456", "Title", "Details", "PASS")

        then:
        1 * steps.sh(_)
    }

    protected String readResource(String name) {
        def classLoader = getClass().getClassLoader();
        def file = new File(classLoader.getResource(name).getFile());
        file.text
    }
}
