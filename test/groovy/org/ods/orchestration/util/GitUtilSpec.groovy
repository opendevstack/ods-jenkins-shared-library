package org.ods.orchestration.util

import util.SpecHelper

class GitUtilSpec extends SpecHelper {

    def "verify git branch url building"() {
        given:
        String gitRepoUrl = "http://git.test.url/scm/myrepo.git"
        String projectKey = "TestPRJ"
        String repoName = "myRepo"
        String gitBranch = "myBranch"
        String expected = "http://git.test.url/projects/TestPRJ/repos/myRepo/browse?at=refs%2Fheads%2FmyBranch"

        when:
        def result = GitUtil.buildGitBranchUrl(gitRepoUrl, projectKey, repoName, gitBranch)

        then:
        result == expected
    }

    def "verify full repo name building"() {
        given:

        when:
        def result = GitUtil.buildFullRepoName(projectKey, repoName)

        then:
        result == expected

        where:
        expected                        ||      repoName                |   projectKey
        null                            ||      null                    |   null
        null                            ||      null                    |   'prj'
        'repo'                          ||      'repo'                  |   null
        'projectKey-repo'               ||      'projectKey-repo'       |   'ProjectKey'
        '2-1'                           ||      '1'                     |   '2'
        'PRJ-REPO'                      ||      'REPO'                  |   'PRJ'

    }

}
