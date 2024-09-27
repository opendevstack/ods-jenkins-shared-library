package org.ods.orchestration.util

import util.SpecHelper

class GitUtilSpec extends SpecHelper {

    def "verify git branch url building"() {
        given:
        String gitRepoUrl = "http://git.test.url"
        String projectKey = "TestPRJ"
        String repoName = "myRepo"
        String gitBranch = "myBranch"
        String expected = "http://git.test.url/projects/TestPRJ/repos/myRepo/browse?at=refs%2Fheads%2FmyBranch"

        when:
        def result = GitUtil.buildGitBranchUrl(gitRepoUrl, projectKey, repoName, gitBranch)

        then:
        result == expected
    }

}
