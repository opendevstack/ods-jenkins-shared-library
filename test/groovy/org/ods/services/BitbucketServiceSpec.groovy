package org.ods.services

import spock.lang.*
import vars.test_helper.PipelineSpockTestBase

import util.*
import org.ods.util.Logger

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
}
