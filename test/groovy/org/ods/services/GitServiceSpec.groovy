package org.ods.services

import org.ods.PipelineScript
import org.ods.util.Logger
import util.SpecHelper

class GitServiceSpec extends SpecHelper {

    def "get release branch"() {
        given:
        def version = "0.0.1"
        def script = new PipelineScript()
        def service = new GitService(script, new Logger(script, false))

        when:
        def releaseBranch = service.getReleaseBranch(version)

        then:
        releaseBranch == "release/0.0.1"
    }

    def "merged branch"() {
        given:
        def script = new PipelineScript()
        def service = new GitService(script, new Logger(script, false))

        when:
        def result = service.mergedBranch("odm", "odm-components", gitCommitMessage)

        then:
        result == mergedBranch

        where:
        gitCommitMessage                                                                            || mergedBranch
        'Merge pull request #62 in ODM/odm-components from bugfix/ODM-509-foo to master'            || 'bugfix/ODM-509-foo'
        'Pull request #62: test\n\nMerge in ODM/odm-components from bugfix/ODM-509-foo to master'   || 'bugfix/ODM-509-foo'
        "Merge branch 'bugfix/ODM-509-foo' to master"                                               || 'bugfix/ODM-509-foo'
    }
}

